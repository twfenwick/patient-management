data "aws_caller_identity" "current" {}

variable "vpc_id" {
  type = string
}

resource "aws_security_group" "service" {
  name   = "${var.service_name}-sg"
  vpc_id = var.vpc_id
}
# Note: we use aws_vpc_security_group_ingress_rule and aws_vpc_security_group_egress_rule instead of inline
# ingress/egress blocks in aws_security_group to allow for dynamic port definitions. Also, it is best practice
# due to issues with handling of multiple CIDR blocks (see aws_security_group_rule docs).
resource "aws_vpc_security_group_ingress_rule" "service" {
  for_each          = toset([for p in var.ports : tostring(p)])
  security_group_id = aws_security_group.service.id
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  ip_protocol       = "tcp"
  cidr_ipv4         = "10.0.0.0/16"
}

# |  auth-service-sg      |  sg-08add7ef8cd469645  | sgr-0f8992070c00cb6e6  |  4005
# |  billing-service-sg   |  sg-00c96db7efcf2a096  | sgr-084b68e2519a1bf30  |  9001 | sgr-0f11500fb44ba7bf0  |  4001
# |  analytics-service-sg |  sg-0f599ed143e7019be  | sgr-06467658a3e5458c1  |  4002
# |  patient-service-sg   |  sg-010ac9e823e4f1742  | sgr-0cf665afa0726b78d  |  4000

# resource "aws_vpc_security_group_egress_rule" "service" {
#   security_group_id = aws_security_group.service.id
#   ip_protocol       = "-1"
#   cidr_ipv4         = "0.0.0.0/0"
# }

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.service_name}"
  retention_in_days = 1
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.service_name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = var.execution_role_arn

  container_definitions = jsonencode([{
    name  = var.service_name
    # 3. Update image references in modules/fargate/main.tf
    image = "${data.aws_caller_identity.current.account_id}.dkr.ecr.us-east-1.amazonaws.com/${var.image_name}:latest"
    # image = var.image_name
    portMappings = [for port in var.ports : {
      containerPort = port
      hostPort      = port
      protocol      = "tcp"
    }]
    environment = [for k, v in var.environment_vars : {
      name  = k
      value = v
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/${var.service_name}"
        "awslogs-region"        = "us-east-1"
        "awslogs-stream-prefix" = var.service_name
      }
    }
  }])
}

resource "aws_service_discovery_service" "this" {
  name = var.service_name
  dns_config {
    namespace_id = var.namespace_id
    dns_records {
      ttl  = 10
      type = "A"
    }
  }
}

resource "aws_ecs_service" "this" {
  name            = var.service_name
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnets
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = false
  }
  service_registries {
    registry_arn = aws_service_discovery_service.this.arn
  }
}
