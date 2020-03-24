resource "aws_security_group" "user_host" {
  name   = var.name_prefix
  vpc_id = var.vpc.id
}
