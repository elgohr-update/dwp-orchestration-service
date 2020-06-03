# ---------------------------------------------------------------------------------------------------------------------
# AWS ECS Task Execution Role
# ---------------------------------------------------------------------------------------------------------------------
output "aws_iam_role_ecs_task_execution_role" {
  description = "The Task Execution Role."
  value       = aws_iam_role.ecs_task_execution_role
}

# ---------------------------------------------------------------------------------------------------------------------
# ECS Task Definition
# ---------------------------------------------------------------------------------------------------------------------
output "aws_ecs_task_definition_td" {
  description = "Full ARN of the Task Definition (including both family and revision)."
  value       = aws_ecs_task_definition.td
}
output "container_port" {
  description = "Port on which the container is listening"
  value       = var.container_port
}
output "container_name" {
  description = "Name of the container"
  value       = var.container_name
}

output "log_group" {
  description = "Orchestration Service Task log group attributes"
  value = {
    name = aws_cloudwatch_log_group.lamda_logs.name
    arn  = aws_cloudwatch_log_group.lamda_logs.arn
  }
}
