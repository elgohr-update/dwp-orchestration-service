#
## ---------------------------------------------------------------------------------------------------------------------
## Analytical UI Container Log Group
## ---------------------------------------------------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "user_container_log_group" {
  name              = local.log_group
  tags              = var.common_tags
  retention_in_days = 180
}
