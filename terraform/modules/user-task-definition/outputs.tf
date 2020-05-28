output "iam_roles" {
  description = "The ECS user task and execution roles"
  value = {
    task_execution_role = aws_iam_role.ecs_task_execution_role
  }
}
