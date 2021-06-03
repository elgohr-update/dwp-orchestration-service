output "ecs_user_host" {
  value = module.ecs-user-host.outputs
}

output "alb_sg" {
  value = module.ecs-fargate-service.lb_sg
}

output "orchestration_service_fqdn" {
  value = module.ecs-fargate-service.fqdn
}
