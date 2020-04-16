resource "aws_ecs_task_definition" "service" {
  family                = "${var.name_prefix}-analytical-workspace"
  execution_role_arn    = aws_iam_role.task_execution_iam_role.arn
  task_role_arn         = aws_iam_role.ui_task_iam_role.arn
  network_mode          = "bridge"
  container_definitions = <<TASK_DEFINITION
      [
      {
          "name": "headless_chrome",
          "image" : "${var.chrome_image}",
          "cpu": 256,
          "memory": 256,
          "essential": true,
          "links":["name:jupyterHub"]
      },
      {

          "name": "jupyterHub",
          "image": "${var.jupyterhub_image}",
          "cpu": 512,
          "memory": 512,
          "essential": true
         },
      {
          "name": "guacd",
          "image": "${var.guacd_image}",
          "cpu": 128,
          "memory": 128,
          "essential": true,
          "links":["name:headless_chrome"],
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
