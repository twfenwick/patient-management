provider "aws" {
    region = "us-east-1"
    # access_key                  = "test"
    # secret_key                  = "test"
    # skip_credentials_validation = true
    # skip_metadata_api_check     = true
    # skip_requesting_account_id  = true
}

# Migrate from CDK (LocalStack.java):
module "vpc" {
    source = "terraform-aws-modules/vpc/aws"
    version = "5.19.0"

    name = "PatientManagementVPC"
    cidr = "10.0.0.0/16"

    azs             = ["us-east-1a", "us-east-1b"]
    # One private subnet per AZ
    # CIDR blocks are the inputs to the VPC module for subnet creation.
    # Everything downstream works with the resulting subnet IDs like "subnet-0abc1234"
    private_subnets = ["10.0.2.0/24", "10.0.3.0/24"]
    public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]

    # CDK creates NAT gateways by default so private subnets can reach internet
    enable_nat_gateway = true
    single_nat_gateway = true  # CDK default is one NAT GW; set false for one-per-AZ in prod

    enable_dns_support   = true
    enable_dns_hostnames = true

    tags = {
        Name = "PatientManagementVPC"
    }
}

module "auth_service_db" {
    source = "./modules/rds"
    db_name = "authservicedb"
    private_subnets = module.vpc.private_subnets
    vpc_id = module.vpc.vpc_id
    enable_health_check = false
}

module "patient_service_db" {
    source = "./modules/rds"
    db_name = "patientservicedb"
    private_subnets = module.vpc.private_subnets
    vpc_id = module.vpc.vpc_id
    enable_health_check = false
}

module "kafka" {
    source = "./modules/msk"
    private_subnets = module.vpc.private_subnets
    vpc_id = module.vpc.vpc_id
}

resource "aws_ecs_cluster" "patient_management" {
    name = "PatientManagementCluster"
}

resource "aws_service_discovery_private_dns_namespace" "patient_management" {
    name        = "patient-management.local"
    description = "CloudMap namespace for patient management services"
    vpc         = module.vpc.vpc_id
}

module "auth_service" {
    source             = "./modules/fargate"
    service_name       = "auth-service"
    image_name         = "auth-service"
    ports              = [4005]
    cluster_id         = aws_ecs_cluster.patient_management.id
    private_subnets    = module.vpc.private_subnets
    namespace_id       = aws_service_discovery_private_dns_namespace.patient_management.id
    execution_role_arn = aws_iam_role.ecs_execution.arn
    vpc_id = module.vpc.vpc_id
    depends_on = [module.auth_service_db]
    environment_vars   = {
        SPRING_KAFKA_BOOTSTRAP_SERVERS = "<bootstrap-servers-placeholder>"
        JWT_SECRET                     = "vzmZayyZrQ17gCNTOPB2pikyT0Z0qnnLJnwUVRRAfevl"
        SPRING_DATASOURCE_URL          = "jdbc:postgresql://${module.auth_service_db.address}:${module.auth_service_db.port}/authservicedb"
        SPRING_DATASOURCE_USERNAME     = "admin_user"
        SPRING_DATASOURCE_PASSWORD     = "password"
        SPRING_JPA_HIBERNATE_DDL_AUTO  = "update"
        SPRING_SQL_INIT_MODE           = "always"
        SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT = "60000"
    }
}

module "billing_service" {
    source             = "./modules/fargate"
    service_name       = "billing-service"
    image_name         = "billing-service"
    ports              = [4001, 9001]
    cluster_id         = aws_ecs_cluster.patient_management.id
    private_subnets    = module.vpc.private_subnets
    namespace_id       = aws_service_discovery_private_dns_namespace.patient_management.id
    execution_role_arn = aws_iam_role.ecs_execution.arn
    vpc_id = module.vpc.vpc_id
    environment_vars   = {
        SPRING_KAFKA_BOOTSTRAP_SERVERS = "<bootstrap-servers-placeholder>"
    }
}

module "analytics_service" {
    source             = "./modules/fargate"
    service_name       = "analytics-service"
    image_name         = "analytics-service"
    ports              = [4002]
    cluster_id         = aws_ecs_cluster.patient_management.id
    private_subnets    = module.vpc.private_subnets
    namespace_id       = aws_service_discovery_private_dns_namespace.patient_management.id
    execution_role_arn = aws_iam_role.ecs_execution.arn
    vpc_id = module.vpc.vpc_id
    depends_on = [module.kafka]
    environment_vars   = {
        SPRING_KAFKA_BOOTSTRAP_SERVERS = "<bootstrap-servers-placeholder>"
    }
}

module "patient_service" {
    source             = "./modules/fargate"
    service_name       = "patient-service"
    image_name         = "patient-service"
    ports              = [4000]
    cluster_id         = aws_ecs_cluster.patient_management.id
    private_subnets    = module.vpc.private_subnets
    namespace_id       = aws_service_discovery_private_dns_namespace.patient_management.id
    execution_role_arn = aws_iam_role.ecs_execution.arn
    vpc_id = module.vpc.vpc_id
    depends_on = [module.billing_service, module.kafka, module.patient_service_db]
    environment_vars   = {
        SPRING_KAFKA_BOOTSTRAP_SERVERS = "<bootstrap-servers-placeholder>"
        BILLING_SERVICE_ADDRESS        = "billing-service.patient-management.local"
        BILLING_SERVICE_GRPC_PORT      = "9001"
        SPRING_DATASOURCE_URL          = "jdbc:postgresql://${module.patient_service_db.address}:${module.patient_service_db.port}/patientservicedb"
        SPRING_DATASOURCE_USERNAME     = "admin_user"
        SPRING_DATASOURCE_PASSWORD     = "password"
        SPRING_JPA_HIBERNATE_DDL_AUTO  = "update"
        SPRING_SQL_INIT_MODE           = "always"
        SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT = "60000"
    }
}

resource "aws_iam_role" "ecs_execution" {
    name = "ecs-task-execution-role"
    assume_role_policy = jsonencode({
        Version = "2012-10-17"
        Statement = [{
            Action    = "sts:AssumeRole"
            Effect    = "Allow"
            Principal = { Service = "ecs-tasks.amazonaws.com" }
        }]
    })
}


module "api_gateway" {
    source             = "./modules/api_gateway"
    cluster_id         = aws_ecs_cluster.patient_management.id
    public_subnets     = module.vpc.public_subnets
    private_subnets    = module.vpc.private_subnets
    vpc_id             = module.vpc.vpc_id
    namespace_id       = aws_service_discovery_private_dns_namespace.patient_management.id
    execution_role_arn = aws_iam_role.ecs_execution.arn
    environment_vars   = {
        SPRING_PROFILES_ACTIVE = "prod"
        AUTH_SERVICE_URL       = "http://auth-service.patient-management.local:4005"
        ANALYTICS_SERVICE_URL  = "http://analytics-service.patient-management.local:4002"
    }
}

output "load_balancer_dns" {
    value = module.api_gateway.load_balancer_dns
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
    role       = aws_iam_role.ecs_execution.name
    policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# how to best put the images up to ecr:
# 1. Create ECR repositories (add to main.tf)
#
resource "aws_ecr_repository" "services" {
    for_each     = toset(["auth-service", "billing-service", "analytics-service", "patient-service", "api-gateway"])
    name         = each.key
    force_delete = true  # allows delete even if images exist
}
#
# 2. After terraform apply, push images (see tag_push_docker_images.sh)
# Note: be safe with shell vars and use ${} and double quotes
# Also, rather than push from local, endgame is CI/CD pipeline to build/push to ECR
#
# 3. Update image references in modules/fargate/main.tf
# > image = "${data.aws_caller_identity.current.account_id}.dkr.ecr.us-east-1.amazonaws.com/${var.image_name}:latest"
#
# And add to modules/fargate/main.tf:
# > data "aws_caller_identity" "current" {}
#
# Do the same in modules/api_gateway/main.tf for the api-gateway image.
# This way the ECR URI is resolved automatically without hardcoding the account ID.
