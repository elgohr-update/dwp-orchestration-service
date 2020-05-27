resource "aws_security_group" "user_host" {
  name   = var.name_prefix
  vpc_id = var.vpc.id
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

resource "aws_security_group_rule" "https_to_s3" {
  description       = "Allow HTTPS to S3 endpoint"
  protocol          = "tcp"
  from_port         = 443
  to_port           = 443
  security_group_id = aws_security_group.user_host.id
  prefix_list_ids   = [var.vpc.s3_prefix_list_id]
  type              = "egress"
}

resource "aws_security_group_rule" "http_to_s3" {
  description       = "Allow HTTP to S3 endpoint"
  protocol          = "tcp"
  from_port         = 80
  to_port           = 80
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
  source_security_group_id = var.emr_sg_id
  type                     = "egress"
}

resource "aws_security_group_rule" "ingress_emr_livy_from_host" {
  description              = "Allow host TCP to EMR Livy"
  protocol                 = "tcp"
  from_port                = var.livy_port
  to_port                  = var.livy_port
  security_group_id        = var.emr_sg_id
  source_security_group_id = aws_security_group.user_host.id
  type                     = "ingress"
}
