resource "aws_security_group" "cleanup_lambda_sg" {
  name        = "${var.name_prefix}-sg"
  description = "Control access to lambda"
  vpc_id      = var.vpc_id
  tags        = merge(var.common_tags, { Name = "${var.name_prefix}-sg" })
}

resource aws_security_group_rule ingress_from_cleanup_lambda {
  description              = "ingress_from_cleanup_lambda"
  from_port                = 443
  protocol                 = "tcp"
  security_group_id        = var.alb_sg
  to_port                  = 443
  type                     = "ingress"
  source_security_group_id = aws_security_group.cleanup_lambda_sg.id
}

resource aws_security_group_rule egress_from_cleanup_lambda {
  description              = "egress_from_cleanup_lambda"
  from_port                = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.cleanup_lambda_sg.id
  to_port                  = 443
  type                     = "egress"
  source_security_group_id = var.alb_sg
}

resource aws_security_group_rule egress_from_cleanup_lambda_to_vpce {
  description              = "egress_from_cleanup_lambda_to_vpce"
  from_port                = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.cleanup_lambda_sg.id
  to_port                  = 443
  type                     = "egress"
  source_security_group_id = var.interface_vpce_sg_id
}

resource aws_security_group_rule egress_to_dynamodb_pl {
  description       = "egress_to_dynamodb_pl"
  from_port         = 443
  protocol          = "tcp"
  security_group_id = aws_security_group.cleanup_lambda_sg.id
  to_port           = 443
  type              = "egress"
  prefix_list_ids   = [var.dynamodb_prefixlist_id]
}
