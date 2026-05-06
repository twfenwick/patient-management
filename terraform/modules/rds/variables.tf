variable "db_name" {
  type = string
}

variable "private_subnets" {
  type = list(string)
}

variable "enable_health_check" {
  type    = bool
  default = true
}

variable "vpc_id" {
  type = string
}
