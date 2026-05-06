variable "create_msk" {
  type    = bool
  default = true  # skip for localstack
}

variable "private_subnets" {
  type = list(string)
}

variable "vpc_id" {
  type = string
}
