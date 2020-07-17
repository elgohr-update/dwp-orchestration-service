# ---------------------------------------------------------------------------------------------------------------------
# ECS Task Definition
# ---------------------------------------------------------------------------------------------------------------------
module "ecs-fargate-task-definition" {
  source                       = "../../modules/fargate-task-definition"
  name_prefix                  = var.name_prefix
  container_name               = var.name_prefix
  container_image              = local.container_image
  container_port               = var.container_port
  container_cpu                = var.container_cpu
  container_memory             = var.container_memory
  container_memory_reservation = var.container_memory_reservation
  common_tags                  = local.common_tags
  role_arn                     = "arn:aws:iam::${local.account[local.environment]}:role/${var.assume_role}"
  management_role_arn          = "arn:aws:iam::${local.account[local.management_account[local.environment]]}:role/${var.assume_role}"
  account                      = lookup(local.account, local.environment)
  log_configuration = {
    secretOptions = []
    logDriver     = "awslogs"
    options = {
      "awslogs-group"         = "/aws/ecs/${data.aws_ecs_cluster.ecs_main_cluster.cluster_name}/${var.name_prefix}"
      "awslogs-region"        = var.region
      "awslogs-stream-prefix" = "ecs"
    }
  }
  environment = [
    {
      name  = "orchestrationService.debug"
      value = local.environment == "development" ? "true" : "false"
    },
    {
      name  = "orchestrationService.load_balancer_name"
      value = data.terraform_remote_state.aws_analytical_env_infra.outputs.alb.name
    },
    {
      name  = "orchestrationService.load_balancer_port"
      value = "443"
    },
    {
      name  = "orchestrationService.aws_region"
      value = var.region
    },
    {
      name  = "orchestrationService.cognito_user_pool_id"
      value = data.terraform_remote_state.aws_analytical_env_cognito.outputs.cognito.user_pool_id
    },
    {
      name  = "orchestrationService.ecs_cluster_name"
      value = module.ecs-user-host.outputs.ecs_cluster.name
    },
    {
      name  = "orchestrationService.emr_cluster_hostname"
      value = data.terraform_remote_state.aws_analytical_env_app.outputs.emr_hostname
    },
    {
      name  = "orchestrationService.user_container_url"
      value = data.terraform_remote_state.aws_analytical_env_infra.outputs.alb_fqdn
    },
    {
      name  = "orchestrationService.user_container_port"
      value = local.guacamole_port
    },
    {
      name  = "orchestrationService.user_task_execution_role_arn"
      value = module.user-task-definition.iam_roles.task_execution_role.arn
    },
    {
      name  = "orchestrationService.user_task_security_groups"
      value = module.ecs-user-host.outputs.security_group_id
    },
    {
      name  = "orchestrationService.user_task_subnets"
      value = join(",", data.terraform_remote_state.aws_analytical_env_infra.outputs.vpc.aws_subnets_private[*].id)
    },
    {
      name  = "orchestrationService.ecr_endpoint"
      value = local.ecr_endpoint
    },
    {
      name  = "orchestrationService.jupyterhub_bucket_arn"
      value = module.jupyter_s3_storage.jupyterhub_bucket.arn
    },
    {
      name  = "orchestrationService.aws_account_number"
      value = local.account[local.environment]
    },
    {
      name  = "PROXY_HOST"
      value = data.terraform_remote_state.analytical_service_infra.outputs.vpc.internet_proxy_vpce.dns_name
    },
    {
      name = "NON_PROXY_HOSTS"
      value = join("|", [
        "*.s3.${var.region}.amazonaws.com",
        "s3.${var.region}.amazonaws.com",
        "ecr.${var.region}.amazonaws.com",
        "*.dkr.ecr.${var.region}.amazonaws.com",
        "dkr.ecr.${var.region}.amazonaws.com",
        "logs.${var.region}.amazonaws.com",
        "kms.${var.region}.amazonaws.com",
        "kms-fips.${var.region}.amazonaws.com",
        "ec2.${var.region}.amazonaws.com",
        "monitoring.${var.region}.amazonaws.com",
        "${var.region}.queue.amazonaws.com",
        "glue.${var.region}.amazonaws.com",
        "sts.${var.region}.amazonaws.com",
        "*.${var.region}.compute.internal",
        "dynamodb.${var.region}.amazonaws.com",
        "elasticloadbalancing.${var.region}.amazonaws.com",
        "ecs.${var.region}.amazonaws.com",
        "application-autoscaling.${var.region}.amazonaws.com",
        "events.${var.region}.amazonaws.com",
      ])
    },
    {
      name  = "orchestrationService.container_log_group"
      value = module.ecs-user-host.outputs.user_container_log_group
    },
    {
      name  = "TAGS"
      value = jsonencode(local.common_tags)
    }
  ]
}
#
## ---------------------------------------------------------------------------------------------------------------------
## ECS Service
## ---------------------------------------------------------------------------------------------------------------------
module "ecs-fargate-service" {
  source          = "../../modules/fargate-service"
  name_prefix     = var.name_prefix
  region          = var.region
  vpc_id          = data.terraform_remote_state.analytical_service_infra.outputs.vpc.aws_vpc.id
  private_subnets = data.terraform_remote_state.analytical_service_infra.outputs.vpc.aws_subnets_private.*.id

  ecs_cluster_name        = data.aws_ecs_cluster.ecs_main_cluster.cluster_name
  ecs_cluster_arn         = data.aws_ecs_cluster.ecs_main_cluster.arn
  task_definition_arn     = module.ecs-fargate-task-definition.aws_ecs_task_definition_td.arn
  container_name          = module.ecs-fargate-task-definition.container_name
  container_port          = module.ecs-fargate-task-definition.container_port
  desired_count           = var.desired_count
  platform_version        = var.platform_version
  security_groups         = var.security_groups
  enable_ecs_managed_tags = var.enable_ecs_managed_tags
  role_arn = {
    management-dns = "arn:aws:iam::${local.account[local.management_account[local.environment]]}:role/${var.assume_role}"
  }
  interface_vpce_sg_id      = data.terraform_remote_state.analytical_service_infra.outputs.interface_vpce_sg_id
  s3_prefixlist_id          = data.terraform_remote_state.analytical_service_infra.outputs.s3_prefix_list_id
  dynamodb_prefixlist_id    = data.terraform_remote_state.analytical_service_infra.outputs.dynamodb_prefix_list_id
  common_tags               = local.common_tags
  parent_domain_name        = local.parent_domain_name[local.environment]
  root_dns_suffix           = local.root_dns_name[local.environment]
  cert_authority_arn        = data.terraform_remote_state.aws_certificate_authority.outputs.root_ca.arn
  internet_proxy_vpce_sg_id = data.terraform_remote_state.analytical_service_infra.outputs.vpc.internet_proxy_vpce.sg_id
}

data "aws_ami" "hardened" {
  most_recent = true
  owners      = ["self", local.account["management"]]

  filter {
    name   = "name"
    values = ["dw-ecs-ami-*"]
  }
}

module "ecs-user-host" {
  source = "../../modules/ecs-user"
  ami_id = data.aws_ami.hardened.id
  auto_scaling = {
    max_size              = 1
    min_size              = 1
    max_instance_lifetime = 604800
  }
  common_tags        = merge(local.common_tags, { Name = "${var.name_prefix}-user-host" })
  instance_type      = "t3.2xlarge"
  name_prefix        = "${var.name_prefix}-user-host"
  frontend_alb_sg_id = data.terraform_remote_state.aws_analytical_env_infra.outputs.alb_sg.id
  guacamole_port     = local.guacamole_port
  emr_sg_id          = data.terraform_remote_state.aws_analytical_env_app.outputs.emr_sg_id
  management_account = local.account[local.management_account[local.environment]]
  vpc = {
    id                   = data.terraform_remote_state.aws_analytical_env_infra.outputs.vpc.aws_vpc
    aws_subnets_private  = data.terraform_remote_state.aws_analytical_env_infra.outputs.vpc.aws_subnets_private
    interface_vpce_sg_id = data.terraform_remote_state.aws_analytical_env_infra.outputs.interface_vpce_sg_id
    s3_prefix_list_id    = data.terraform_remote_state.aws_analytical_env_infra.outputs.s3_prefix_list_id
  }
}
#
## ---------------------------------------------------------------------------------------------------------------------
## ECS UserService
## ---------------------------------------------------------------------------------------------------------------------
module "user-task-definition" {
  source      = "../../modules/user-task-definition"
  name_prefix = "${var.name_prefix}-user"

  common_tags = local.common_tags
}

#
## ---------------------------------------------------------------------------------------------------------------------
## JupyterHub S3 Storage
## ---------------------------------------------------------------------------------------------------------------------
module "jupyter_s3_storage" {
  source      = "../../modules/jupyter-s3-storage"
  name_prefix = "${var.name_prefix}-jupyter-s3-storage"

  common_tags    = local.common_tags
  logging_bucket = data.terraform_remote_state.security-tools.outputs.logstore_bucket.id
  vpc_id         = data.terraform_remote_state.aws_analytical_env_infra.outputs.vpc.aws_vpc
}

#
## ---------------------------------------------------------------------------------------------------------------------
## Cleanup Lambda
## ---------------------------------------------------------------------------------------------------------------------
module "cleanup_lambda" {
  source                 = "../../modules/cleanup-lambda"
  name_prefix            = "${var.name_prefix}-cleanup-lambda"
  common_tags            = local.common_tags
  account                = local.account[local.environment]
  region                 = var.region
  table_name             = "orchestration_service_user_tasks"
  fqdn                   = module.ecs-fargate-service.fqdn
  aws_subnets_private    = data.terraform_remote_state.analytical_service_infra.outputs.vpc.aws_subnets_private.*.id
  alb_sg                 = module.ecs-fargate-service.lb_sg.id
  vpc_id                 = data.terraform_remote_state.analytical_service_infra.outputs.vpc.aws_vpc.id
  interface_vpce_sg_id   = data.terraform_remote_state.analytical_service_infra.outputs.interface_vpce_sg_id
  dynamodb_prefixlist_id = data.terraform_remote_state.analytical_service_infra.outputs.dynamodb_prefix_list_id
}

## ---------------------------------------------------------------------------------------------------------------------
## Cloudwatch Log Metrics/Alarms
## ---------------------------------------------------------------------------------------------------------------------

module "orchestration_service_failed_to_destroy_alarm" {
  source  = "dwp/metric-filter-alarm/aws"
  version = "1.1.5"

  log_group_name      = module.ecs-fargate-task-definition.log_group.name
  metric_namespace    = "/app/${var.name_prefix}"
  pattern             = "{ $.message = \"Failed to destroy*\"}"
  alarm_name          = "Orchestration Service Failed To Destroy Resources"
  alarm_action_arns   = [data.terraform_remote_state.security-tools.outputs.sns_topic_london_monitoring.arn]
  evaluation_periods  = "1"
  period              = 60
  threshold           = 0
  statistic           = "Sum"
  comparison_operator = "GreaterThanThreshold"
}

module "guacamole_invalid_user_desktop_alarm" {
  source  = "dwp/metric-filter-alarm/aws"
  version = "1.1.5"

  log_group_name   = module.ecs-user-host.outputs.user_container_log_group
  metric_namespace = "/app/${var.name_prefix}-user-host-guacamole"
  # No consistent JSON logging for these container_logs, so pattern is plaintext search
  pattern             = "Cognito user tried to access desktop for"
  alarm_name          = "Cognito user tried to access another desktop"
  alarm_action_arns   = [data.terraform_remote_state.security-tools.outputs.sns_topic_london_monitoring.arn]
  evaluation_periods  = "1"
  period              = 60
  threshold           = 0
  statistic           = "Sum"
  comparison_operator = "GreaterThanThreshold"
}
