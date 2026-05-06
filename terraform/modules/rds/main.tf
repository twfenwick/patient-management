resource "aws_security_group" "rds" {
  name   = "${var.db_name}-rds-sg"
  vpc_id = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "rds" {
  security_group_id = aws_security_group.rds.id
  from_port         = 5432
  to_port           = 5432
  ip_protocol       = "tcp"
  cidr_ipv4         = "10.0.0.0/16"
}

resource "aws_vpc_security_group_egress_rule" "rds" {
  security_group_id = aws_security_group.rds.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_db_subnet_group" "this" {
  name = "${var.db_name}-subnet-group"
  subnet_ids = var.private_subnets
}

resource "aws_db_instance" "this" {
  identifier = var.db_name
  allocated_storage = 20
  engine = "postgres"
  instance_class = "db.t3.micro"
  db_name = var.db_name
  username = "admin_user"
  password = "password" # Need to put this into env var or vault
  db_subnet_group_name = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot = true
}

output "address" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

resource "aws_route53_health_check" "this" {
  count             = var.enable_health_check ? 1 : 0
  ip_address        = var.enable_health_check ? aws_db_instance.this.address : null
  port              = aws_db_instance.this.port
  type              = "TCP"
  request_interval  = 30
  failure_threshold = 3

  tags = {
    Name = "${var.db_name}-health-check"
  }
}

