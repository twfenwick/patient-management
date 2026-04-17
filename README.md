This repo consists of microservices and automated infrastructure deployment for experimentation, learning, refresh, etc.
It was coded from scratch using Java, Spring Boot, postgresql, Kafka, and AWS according to this [course](https://www.youtube.com/watch?v=tseqdcFfTUY) 
over the period of about a week. The code contains helpful notes and comments along the way and required additional debugging and research
to get it fully functional as some libraries, services, and tools were outdated.

## Project Structure

- `patient-service`: Service for managing patient data; publishes events to Kafka.
- `billing-service`: Service for handling billing operations using gRPC for efficient microservice intercommunications.
- `analytics-service`: Dummy analysis service to demo Kafka consumer integration.
- `infrastructure`: Java-based cloud formation code for deploying the microservices and infra on AWS.
- `auth-service`: Service for handling user authentication and token generation.
- `api-gateway`: Handles all client request routing to the appropriate microservice and token validation.
- `integration-tests`: Some basic integration tests for authentication and patient requests.

## AWS Resources
- Kafka cluster for message publishing and consumption.
- ECS cluster for running the microservices.
- RDS for storing patient data.
- Cloud formation for deploying the infrastructure.
- VPC for isolation/security of the internal services.
- ALB for secure entry point to api-gateway.

## Challenges
- Compatibility issues when using newer versions of Spring Boot and Java (4 and 25 respectively) with older libraries.
- There was a mismatch between where the protobuf blueprint files were expected to be by the gRPC plugin vs. where they 
were suggested by the guide.
- Needed to add additional config code/bean for proper webClient injection to the api-gateway validation filter.  
- There was a lot of boilerplate getter/setter code, so I added lombok as an improvement. The plugin to generate this 
code at compile time needed to be added manually and was not included when using Spring Boot Add Starters feature.
- Some minor typos from the guide, like including a redundant "Bearer " prefix in the token validation logic of the API 
gateway led to some extra debugging time.
- The recommended bitnami/kafka Docker image was no longer available, switched to apache/kafka.
- I didn't have node.js installed on my local, which was needed by the aws cdk Java lib, and caused some ambiguous runtime 
error when building the stack on localstack.
- The localstack service is a nice convenience, but the configuration guide was outdated and assumed a fully featured account which was paywalled. 
I did a free 45-day trial and it worked okay. I had to create the localstack container a few times as it wasn't starting 
and/or visible, so I had to run it manually from Docker Desktop and/or add a custom URL:PORT (which was actually the default URL:PORT).
Additionally, the localstack name was not supposed to be important but did create an error when trying to pull the 
localstack image. Also, the localstack image had to be pulled from the command line, which was fine, but the 404 error from the UI was not very helpful.
You could probably deploy straight to your AWS account for sandboxing as a simpler approach, since you can destroy the
stack right away after testing (if you don't mind incurring some small charges, if any).

