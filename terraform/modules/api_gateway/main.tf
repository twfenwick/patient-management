data "aws_caller_identity" "current" {}

resource "aws_security_group" "alb" {
  name   = "api-gateway-alb-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "service" {
  name   = "api-gateway-service-sg"
  vpc_id = var.vpc_id

  # ingress {
  #   from_port       = 4004
  #   to_port         = 4004
  #   protocol        = "tcp"
  #   security_groups = [aws_security_group.alb.id]
  # }

  # egress {
  #   from_port   = 0
  #   to_port     = 0
  #   protocol    = "-1"
  #   cidr_blocks = ["0.0.0.0/0"]
  # }
}

resource "aws_vpc_security_group_ingress_rule" "service" {
  security_group_id = aws_security_group.service.id
  from_port         = 4004
  to_port           = 4004
  ip_protocol       = "tcp"
  referenced_security_group_id = aws_security_group.alb.id
}
#
# resource "aws_vpc_security_group_egress_rule" "service" {
#   security_group_id = aws_security_group.service.id
#   ip_protocol       = "-1"
#   cidr_ipv4         = "0.0.0.0/0"
# }

# |  api-gateway-service-sg |  sg-04deff197524e5cc6  |  sgr-056b0037b44befbeb  |  4004 |

resource "aws_lb" "this" {
  name               = "api-gateway-lb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnets
}

resource "aws_lb_target_group" "this" {
  name        = "api-gateway-tg"
  port        = 4004
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 5
    interval            = 30
  }
}

resource "aws_lb_listener" "this" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/api-gateway"
  retention_in_days = 1
}

resource "aws_ecs_task_definition" "this" {
  family                   = "api-gateway"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = var.execution_role_arn

  container_definitions = jsonencode([{
    name  = "api-gateway"
    # 3. Update image references in modules/fargate/main.tf
    image = "${data.aws_caller_identity.current.account_id}.dkr.ecr.us-east-1.amazonaws.com/api-gateway:latest"
    # image = "api-gateway"
    portMappings = [{
      containerPort = 4004
      hostPort      = 4004
      protocol      = "tcp"
    }]
    environment = [for k, v in var.environment_vars : {
      name  = k
      value = v
    }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = "/ecs/api-gateway"
        "awslogs-region"        = "us-east-1"
        "awslogs-stream-prefix" = "api-gateway"
      }
    }
  }])
}

resource "aws_service_discovery_service" "this" {
  name = "api-gateway"
  dns_config {
    namespace_id = var.namespace_id
    dns_records {
      ttl  = 10
      type = "A"
    }
  }
}

resource "aws_ecs_service" "this" {
  name                              = "api-gateway"
  cluster                           = var.cluster_id
  task_definition                   = aws_ecs_task_definition.this.arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.public_subnets
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.this.arn
    container_name   = "api-gateway"
    container_port   = 4004
  }

  service_registries {
    registry_arn = aws_service_discovery_service.this.arn
  }
}
