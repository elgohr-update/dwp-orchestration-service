output "aws_ecs_task_definition" {
  description = "The ECS task definition"
  value       = aws_ecs_task_definition.service
}
