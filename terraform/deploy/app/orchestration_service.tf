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
  account                      = lookup(local.account, local.environment)
  log_configuration = {
    secretOptions = []
    logDriver     = "awslogs"
    options = {
      "awslogs-group"         = "/ecs/${data.aws_ecs_cluster.ecs_main_cluster.cluster_name}/${var.name_prefix}"
      "awslogs-region"        = var.region
      "awslogs-stream-prefix" = "ecs"
    }
  }
  environment = [
    {
      name  = "orchestrationService.user_container_task_definition"
      value = "${var.name_prefix}-analytical-workspace"
    },
    {
      name  = "orchestrationService.load_balancer_name"
      value = "${var.name_prefix}-lb"
    },
    {
      name  = "orchestrationService.aws_region"
      value = var.region
    },
    {
      name  = "orchestrationService.cognito_user_pool_id"
      value = data.terraform_remote_state.aws_analytical_env_cognito.outputs.cognito.user_pool_id
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
  vpc_id          = data.terraform_remote_state.emr_cluster_broker_infra.outputs.vpc.aws_vpc.id
  private_subnets = data.terraform_remote_state.emr_cluster_broker_infra.outputs.vpc.aws_subnets_private.*.id

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
  interface_vpce_sg_id = data.terraform_remote_state.emr_cluster_broker_infra.outputs.interface_vpce_sg_id
  s3_prefixlist_id     = data.terraform_remote_state.emr_cluster_broker_infra.outputs.s3_prefix_list_id
  common_tags          = local.common_tags
  parent_domain_name   = local.parent_domain_name[local.environment]
  root_dns_prefix      = local.root_dns_prefix[local.environment]
  cert_authority_arn   = data.terraform_remote_state.aws_certificate_authority.outputs.root_ca.arn
}

data "aws_ami" "hardened" {
  most_recent = true
  owners      = ["self", local.account["management"], "amazon"]

  filter {
    name   = "name"
    values = ["amzn-ami-*-amazon-ecs-optimized"]
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
  common_tags   = merge(local.common_tags, { Name = "${var.name_prefix}-user-host" })
  instance_type = "t3.2xlarge"
  name_prefix   = "${var.name_prefix}-user-host"
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
module "ec2_task_definition" {
  source      = "../../modules/ec2-task-definition"
  region      = var.region
  name_prefix = "${var.name_prefix}-task-definition"

  chrome_image     = "${local.account[local.environment]}.dkr.ecr.${var.region}.amazonaws.com/aws-analytical-env/hardened-guac-chrome"
  guacd_image      = "${local.account[local.environment]}.dkr.ecr.${var.region}.amazonaws.com/aws-analytical-env/guacd"
  jupyterhub_image = "${local.account[local.environment]}.dkr.ecr.${var.region}.amazonaws.com/aws-analytical-env/jupyterhub"

}
