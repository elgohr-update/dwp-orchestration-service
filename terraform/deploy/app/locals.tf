locals {
  log_driver = "awslogs"
  log_options = {
    "awslogs-region"        = var.region
    "awslogs-group"         = "/ecs/service/${var.name_prefix}"
    "awslogs-stream-prefix" = "ecs"
  }
  guacamole_port = 8443
  ecr_endpoint   = "${local.account[local.management_account[local.environment]]}.dkr.ecr.${var.region}.amazonaws.com"
}
