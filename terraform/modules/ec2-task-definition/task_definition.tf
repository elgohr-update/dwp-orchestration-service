resource "aws_ecs_task_definition" "service" {
  family                = "${var.name_prefix}-ui-service"
  execution_role_arn    = aws_iam_role.ecs_task_execution_role.arn
  task_role_arn         = aws_iam_role.ecs_task_role.arn
  network_mode          = "bridge"
  container_definitions = <<TASK_DEFINITION
      [
      {

          "name": "jupyterHub",
          "image": "${var.jupyterhub_image}",
          "cpu": 512,
          "memory": 512,
          "essential": true
       },
      {
          "name": "headless_chrome",
          "image" : "${var.chrome_image}",
          "cpu": 256,
          "memory": 256,
          "essential": true,
          "links":["jupyterHub"]
      },
      {
          "name": "guacd",
          "image": "${var.guacd_image}",
          "cpu": 128,
          "memory": 128,
          "essential": true,
          "links":["headless_chrome"],
          "portMappings": [
            {
              "containerPort": 4822,
              "hostPort": 0
            }
          ]
      }
]
TASK_DEFINITION
}
