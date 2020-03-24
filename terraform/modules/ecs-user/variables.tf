variable "name_prefix" {
  type        = string
  description = "(Required) Name prefix for resources we create, defaults to repository name"
}

variable "common_tags" {
  type        = map(string)
  description = "(Required) common tags to apply to aws resources"
}

variable "ami_id" {
  type = string
}

variable "instance_type" {
  type = string
}

variable "vpc" {
  type = object({
    id                  = string
    aws_subnets_private = list(any)
  })
}

variable "auto_scaling" {
  type = object({
    min_size              = number
    max_size              = number
    max_instance_lifetime = number
  })
}
