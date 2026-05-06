
variable "cluster_id" {
  type = string
}

variable "public_subnets" {
  type = list(string)
}

variable "private_subnets" {
  type = list(string)
}

variable "vpc_id" {
  type = string
}

variable "namespace_id" {
  type = string
}

variable "environment_vars" {
  type    = map(string)
  default = {}
}

variable "execution_role_arn" {
  type = string
}
