output "outputs" {
  value = {
    ecs_cluster       = aws_ecs_cluster.user_host
    security_group_id = aws_security_group.user_host.id
  }
}