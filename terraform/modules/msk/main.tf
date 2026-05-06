resource "aws_security_group" "msk" {
  count  = var.create_msk ? 1 : 0
  name   = "msk-security-group"
  vpc_id = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "msk" {
  count             = var.create_msk ? 1 : 0
  security_group_id = aws_security_group.msk[count.index].id
  from_port         = 9092
  to_port           = 9094
  ip_protocol       = "tcp"
  cidr_ipv4         = "10.0.0.0/16"
}

resource "aws_vpc_security_group_egress_rule" "msk" {
  count             = var.create_msk ? 1 : 0
  security_group_id = aws_security_group.msk[count.index].id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_msk_cluster" "this" {
  count                  = var.create_msk ? 1 : 0
  cluster_name           = "kafka-cluster"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 2 # must match number of private subnets (one broker per subnet)

  broker_node_group_info {
    instance_type   = "kafka.t3.small"
    client_subnets  = var.private_subnets
    security_groups = [aws_security_group.msk[count.index].id]
  }
}
