module networking {
  source = "../../modules/networking"

  common_tags = local.common_tags
  name        = local.name

  region = var.vpc_region

  role_arn = {
    orchestration-service = "arn:aws:iam::${local.account[local.environment]}:role/${var.assume_role}"
  }

  vpc = {
    cidr_block          = data.terraform_remote_state.emr_cluster_broker_infra.outputs.vpc.aws_vpc.cidr_block
    id                  = data.terraform_remote_state.emr_cluster_broker_infra.outputs.vpc.aws_vpc.id,
    main_route_table_id = data.terraform_remote_state.emr_cluster_broker_infra.outputs.vpc.aws_vpc.main_route_table_id
  }
}
