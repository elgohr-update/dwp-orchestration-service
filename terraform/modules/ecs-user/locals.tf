locals {
  log_group = "/aws/ecs/${var.name_prefix}/user-container-logs/"
  cloudwatch_agent_config_file = templatefile("${path.module}/templates/cloudwatch_agent.json",
    {
      cloudwatch_log_group = local.log_group
    }
  )
}
