package com.pm.stack;


import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// localstack requires to delete the stack before deploying a new one -- real AWS deployments don't require that, but
// localstack recommends this
public class LocalStack extends Stack {
    // Need class-level variable so when we create other parts of infra, link to our vpc
    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props) {
        // id: stack ID, props: additional properties we want to define ourselves
        super(scope, id, props);

        this.vpc = createVpc();

        DatabaseInstance authServiceDb = createDatabaseInstance("AuthServiceDB", "auth-service-db");

        DatabaseInstance patientServiceDb = createDatabaseInstance("PatientServiceDB", "patient-service-db");

        // HC is for other services to know when db is running and ready to accept connections,
        // but also will tell other services if the db is in a bad state.
        CfnHealthCheck authDbHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");
        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        FargateService authService = createFargateService(
                "AuthService",
                "auth-service",
                List.of(4005),
                authServiceDb,
                // Randomly generated jwt secret for testing; wouldn't actually be included here in a real prod system
                Map.of("JWT_SECRET", "vzmZayyZrQ17gCNTOPB2pikyT0Z0qnnLJnwUVRRAfevl"));

        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        FargateService billingService = createFargateService("BillingService",
                "billing-service",
                List.of(4001, 9001),
                null,
                null);

        FargateService analytics = createFargateService("AnalyticsService",
                "analytics-service",
                List.of(4002),
                null,
                null);

        analytics.getNode().addDependency(mskCluster);

        FargateService patientService = createFargateService(
                "PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                ));

        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);

        createApiGatewayServiceAndLB();

        // Now need to prepare docker images to make sure using latest code, then deploy to localstack running on
        // our desktop. During development, our run configs did two things: build the image and run the container.
        // The images we deploy to ECS are the exact same images that we used during development. Thus, why Docker
        // is provides a considerable advantage: b/c we can be sure the code will run the same in localstack as it
        // does in our IDE. ...one of the prime reasons why we use containers. All images should be good since we've
        // been developing and building as we go, but best to build them all again to be sure all images are up-to-date
        // with our latest code.
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVPC") // unique ID for this VPC, used by CDK to track this resource
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // max availability zones to use in this VPC
                .build();
    }

    private DatabaseInstance createDatabaseInstance(String id, String dbName) {
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20) // storage in GB
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY) // auto-delete the database when the stack is deleted (typically not in prod)
                .build();
    }

    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id) {
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafka-cluster")
                .kafkaVersion("3.7.x") // localstack service requires wildcard for the patch version
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }


    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                // Sets up a cloudmap namespace "patient-management.local"
                // for service discovery within our cluster. This allows our services
                // to find each other using this domain. e.g. when creating auth-service,
                // other microservices can find it at "auth-service.patient-management.local"
                // Convenience method, can avoid needing to know ips and internal addresses
                // of ecs services as it's all managed by the CloudMap Service Discovery.
                // So, we just need to know the service name and namespace, and CloudMap will handle the rest.
                //
                // Similar to how Docker works on local dev when we add all the services to --network internal.
                // All the services are able to find each other using the container name, so this concept very similar
                // just at a higher level.
                //
                // It's important to not on actual AWS deployment, but doesn't work great on localstack because all
                // services essentially run on localhost on our dev machine. But good to have this in place for when
                // we deploy to actual prod env.
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    // an ECS service is used to manage an ECS task in terms of the load balancers managing the scaling, the number of
    // resources that a task needs, and it will also handle a failed task by starting a new one, and things like that.
    // So, an ECS service is going to run the task and a task is a thing that actually runs our container.
    //
    // Fargate is a type of ECS service, commonly used in enterprises, as it makes it easy to start/stop/scale ECS services
    // that run the different containers.
    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, id + "Task")
                .cpu(256) // CPU units
                .memoryLimitMiB(512) // MB RAM
                .build();

        ContainerDefinitionOptions.Builder containerOptions = ContainerDefinitionOptions.builder()
                // Similar to local Docker dev. Task will pull an image from a repository, and use that image to build
                // a container. You can specify where the image comes from e.g. ECR. But since it's local, localstack
                // will know to pull the image from the local image repository on our machine.
                //
                // Reminder, each time we built and deployed an image to our docker setup, we created a new image that
                // was stored on our machine. This is where localstack pulls from.
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port)
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                .logGroupName("/ecs/" + imageName)
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
//                        This "streamPrefix" error is usually related to logging:
//                        Exception in thread "main" java.lang.NullPointerException: streamPrefix is required
//	                            at java.base/java.util.Objects.requireNonNull(Objects.java:246)
//	                            at software.amazon.awscdk.services.ecs.AwsLogDriverProps$Jsii$Proxy.<init>(AwsLogDriverProps.java:275)
//	                            at software.amazon.awscdk.services.ecs.AwsLogDriverProps$Builder.build(AwsLogDriverProps.java:237)
//	                            at com.pm.stack.LocalStack.createFargateService(LocalStack.java:195)
//	                            at com.pm.stack.LocalStack.<init>(LocalStack.java:43)
//	                            at com.pm.stack.LocalStack.main(LocalStack.java:252)
                        .streamPrefix(imageName)  // Add this stream prefix to fix the error ^
                        .build()));

        Map<String, String> envVars = new HashMap<>();
        // Have to pass the bootstrap servers (where localstack could set up kafka bootstrap servers)
        // as single env variable. Any service that needs access to kafka will have it b/c we specified where the
        // bootstrap servers are.
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");

        if (additionalEnvVars != null) {
            envVars.putAll(additionalEnvVars);
        }

        if (db != null) {
            // Similar to env vars we added to local run configuration
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());
            // DB that the service connects to will run the hibernate stuff
            // and from the data.sql script as well. Good for dev purposes.
            // Leave out for prod database, prob do manual inserts or db migration
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            // Set timeout, so if it's not ready at the point that this service is trying to spin, it will retry a few
            // times before it fails instead of failing straight away.
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    /**
     * Creates API Gateway in container, then creates an Application Load Balancer for access from the internet.
     */
    private void createApiGatewayServiceAndLB() {
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder.create(this, "ApiGatewayTaskDefinition")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        String imageName = "api-gateway";
        ContainerDefinitionOptions containerOptions = ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .environment(Map.of(
                        // API GW will have a different set of routes for dev vs. prod. This is where "-prod" suffix
                        // is recognized to see different config files:
                        "SPRING_PROFILES_ACTIVE", "prod",
                        // Since localstack doesn't implement service discover well, use docker internal service
                        // discovery in combination with the port so the services can find each other.
                        // AUTH_SERVICE_URL is used to validate token before requests passed on to internal microservices.
                        "AUTH_SERVICE_URL", "http://host.docker.internal:4005",
                        "ANALYTICS_SERVICE_URL", "http://analytics-service.patient-management.local:4002"
                        )
                )
                .portMappings(List.of(4004).stream()
                        .map(port -> PortMapping.builder()
                                .containerPort(port)
                                .hostPort(port)
                                .protocol(Protocol.TCP)
                                .build())
                        .toList())
                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                .logGroupName("/ecs/" + imageName)
                                .removalPolicy(RemovalPolicy.DESTROY)
                                .retention(RetentionDays.ONE_DAY)
                                .build())
                        .streamPrefix(imageName)
                        .build()))
                .build();
        // Add container options to task def, then task def below to ALB.
        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        // Automatically creates an application load balancer. Don't have to create manually, just specify
        // this is what we want...lots of magic behind the scenes. CDK code along with CloudFormation automatically
        // starts one.
        ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                .cluster(ecsCluster)
                .serviceName(imageName)
                .taskDefinition(taskDefinition)
                .desiredCount(1)
                // give it more time to start up before health checks start, so it doesn't fail straight away
                .healthCheckGracePeriod(Duration.seconds(60))
                .build();
    }

    static void main(final String[] args) {
        // Initialization code that starts the process:

        // Create cloud formation stack template in this dir:
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        // Synthesizer is AWS term that is used to convert our code into a CF template
        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();
        // Bootstrapless: tell CDK code (cloud dev kit) to skip the initial bootstrapping of the CDK environment
        //  as we don't need it for localstack

        // Now link the localstack class to "app", so the CDK knows to build our stack anytime we run this java app
        new LocalStack(app, "localstack", props);
        // After we've linked a new CDK app instance to our stack:
        // app.synth() tells cdk "app" to take our stack (LocalStack(...)), add any props, convert that stack to a
        //  CF template and store it in the folder specified.
        app.synth();
        // By default, aws-cdk doesn't print out too many logs:
        System.out.println("App synthesizing in progress...");
    }
}
