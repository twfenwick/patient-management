variable "service_name" {
  type = string
}

variable "image_name" {
  type = string
}

variable "ports" {
  type = list(number)
}

variable "cluster_id" {
  type = string
}

variable "private_subnets" {
  type = list(string)
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
