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
    id                   = string
    aws_subnets_private  = list(any)
    interface_vpce_sg_id = string
    s3_prefix_list_id    = string
  })
}

variable "auto_scaling" {
  type = object({
    min_size              = number
    max_size              = number
    max_instance_lifetime = number
  })
}

variable frontend_alb_sg_id {
  type        = string
  description = "(Required) Source ALB Security group"
}

variable guacamole_port {
  type        = number
  description = "Port used for listening by the user guacamole container"
}

variable management_account {
  type        = string
  description = "(Required) - The mgmt account where images reside"
}

variable livy_port {
  type        = number
  description = "Port that EMR livy listens on"
  default     = 8998
}

variable emr_sg_id {
  type        = string
  description = "Security Group id of EMR cluster"
}
