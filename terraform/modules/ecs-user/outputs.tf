output "outputs" {
  value = {
    ecs_cluster = {
      arn  = aws_ecs_cluster.user_host.arn
      name = aws_ecs_cluster.user_host.name
    }
    user_container_log_group = aws_cloudwatch_log_group.user_container_log_group.name
    security_group_id        = aws_security_group.user_host.id
  }
}
