locals {
  log_group = "/aws/ecs/${var.name_prefix}/user-container-logs/"
  cloudwatch_agent_config_file = templatefile("${path.module}/templates/cloudwatch_agent.json",
    {
      cloudwatch_log_group = local.log_group
    }
  )
  autoscaling_tags = [
    for key, value in var.common_tags :
    {
      key                 = key
      value               = value
      propagate_at_launch = true
    }
  ]
}
