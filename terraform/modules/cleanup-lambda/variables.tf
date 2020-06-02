variable "name_prefix" {
  type        = string
  description = "(Required) Name prefix for resources created"
}

variable "common_tags" {
  type        = map(string)
  description = "(Required) common tags to apply to aws resources"
}

variable "region" {
  type        = string
  description = "(Required) The region to deploy into"
}

variable "account" {
  type        = string
  description = "(Required) The account number of the environment"
}

variable "table_name" {
  type        = string
  description = "(Required) The DynamoDB active user table name"
}

variable "fqdn" {
  type        = string
  description = "(Required) The fqdn of the load balancer that sits infront of Orchestartion Service"
}

variable "aws_subnets_private" {
  type        = list
  description = "(Required) The subnet in which the lambda will run"
}

variable "alb_sg" {
  type        = string
  description = "(Required) The ALB security group"
}

variable "vpc_id" {
  type        = string
  description = "(Required) The VPC ID"
}
