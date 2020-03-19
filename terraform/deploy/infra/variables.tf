# ---------------------------------------------------------------------------------------------------------------------
# AWS ROLES AND REGION
# ---------------------------------------------------------------------------------------------------------------------

variable "assume_role" {
  type        = string
  description = "(Required) The role to assume when doing an apply"
}

variable "region" {
  type        = string
  description = "(Required) The region to deploy into"
}

variable "vpc_region" {
  type        = string
  description = "(Required) The region the VPC we are deploying into is in"
}
