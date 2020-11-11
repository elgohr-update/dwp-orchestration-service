resource "aws_security_group" "user_host" {
  name   = var.name_prefix
  vpc_id = var.vpc.id
  tags   = merge(var.common_tags, { "Name" : "${var.name_prefix}-user-host-sg" })
}

resource "aws_security_group_rule" "to_vpce" {
  description              = "Allow instances to connect to VPCE"
  protocol                 = "tcp"
  from_port                = 443
  to_port                  = 443
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.vpc.interface_vpce_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "vpce_from_host" {
  description              = "Accept VPCE traffic"
  protocol                 = "tcp"
  from_port                = 443
  to_port                  = 443
  security_group_id        = var.vpc.interface_vpce_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}

resource "aws_security_group_rule" "to_gh_proxy_vpce" {
  description              = "Allow instances to connect to VPCE"
  protocol                 = "tcp"
  from_port                = 3128
  to_port                  = 3128
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.github_proxy_vpce_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "gh_proxy_vpce_from_host" {
  description              = "Accept VPCE traffic"
  protocol                 = "tcp"
  from_port                = 3128
  to_port                  = 3128
  security_group_id        = var.github_proxy_vpce_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}

resource "aws_security_group_rule" "https_to_s3" {
  description       = "Allow HTTPS to S3 endpoint"
  protocol          = "tcp"
  from_port         = 443
  to_port           = 443
  security_group_id = aws_security_group.user_host.id
  prefix_list_ids   = [var.vpc.s3_prefix_list_id]
  type              = "egress"
}

resource "aws_security_group_rule" "source_fe" {
  description              = "Allow HTTPS from Frontend LB"
  protocol                 = "tcp"
  from_port                = var.guacamole_port
  to_port                  = var.guacamole_port
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.frontend_alb_sg_id
  type                     = "ingress"
}

resource "aws_security_group_rule" "egress_lb_to_host" {
  description              = "Allow LB HTTPS to user host"
  protocol                 = "tcp"
  from_port                = var.guacamole_port
  to_port                  = var.guacamole_port
  security_group_id        = var.frontend_alb_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "egress"
}

resource "aws_security_group_rule" "egress_host_to_emr_livy" {
  description              = "Allow host TCP to EMR Livy"
  protocol                 = "tcp"
  from_port                = var.livy_port
  to_port                  = var.livy_port
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.livy_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "ingress_emr_livy_from_host" {
  description              = "Allow host TCP to EMR Livy"
  protocol                 = "tcp"
  from_port                = var.livy_port
  to_port                  = var.livy_port
  security_group_id        = var.livy_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}

resource "aws_security_group_rule" "ingress_pushgw_from_host" {
  description              = "Allow Pushgateway TCP ingress from user host"
  protocol                 = "tcp"
  from_port                = var.pushgateway_port
  to_port                  = var.pushgateway_port
  security_group_id        = var.pushgateway_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}

resource "aws_security_group_rule" "egress_host_to_pushgw" {
  description              = "Allow host TCP egress to Pushgateway"
  protocol                 = "tcp"
  from_port                = var.pushgateway_port
  to_port                  = var.pushgateway_port
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.pushgateway_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "egress_host_to_emr_hive" {
  description              = "Allow host TCP from EMR Hive"
  protocol                 = "tcp"
  from_port                = var.hiveserver2_port
  to_port                  = var.hiveserver2_port
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.emr_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "ingress_emr_hive_from_host" {
  description              = "Allow host TCP to EMR Hive"
  protocol                 = "tcp"
  from_port                = var.hiveserver2_port
  to_port                  = var.hiveserver2_port
  security_group_id        = var.emr_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}

# Temporarily allow hue to connect to EMR directly until Dataworks
# certificates are added to user containers
resource "aws_security_group_rule" "egress_host_to_emr_livy_hue" {
  description              = "Allow host TCP to EMR Livy"
  protocol                 = "tcp"
  from_port                = var.livy_port
  to_port                  = var.livy_port
  security_group_id        = aws_security_group.user_host.id
  source_security_group_id = var.emr_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "ingress_emr_livy_from_host_hue" {
  description              = "Allow host TCP to EMR Livy"
  protocol                 = "tcp"
  from_port                = var.livy_port
  to_port                  = var.livy_port
  security_group_id        = var.emr_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}
