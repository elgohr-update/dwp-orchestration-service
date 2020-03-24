resource "aws_ecs_cluster" "user_host" {
  name               = var.name_prefix
  capacity_providers = [aws_ecs_capacity_provider.user_host.name]
}

resource "aws_ecs_capacity_provider" "user_host" {
  name = var.name_prefix
  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.user_host.arn
    managed_termination_protection = "ENABLED"

    managed_scaling {
      maximum_scaling_step_size = 10
      minimum_scaling_step_size = 1
      status                    = "ENABLED"
    }

  }
}
