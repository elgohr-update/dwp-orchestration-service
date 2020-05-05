output "ecs_user_host" {
  value = module.ecs-user-host.outputs
}

output "orchestration_service_fqdn" {
  value = module.ecs-fargate-service.fqdn
}
