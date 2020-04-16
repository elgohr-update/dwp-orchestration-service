resource "aws_iam_role" "task_execution_iam_role" {
  name               = "${var.name_prefix}-td-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks.json
}
data "aws_iam_policy_document" "ecs_tasks" {
  statement {
    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_policy" "task_execution_iam_policy" {
  policy = data.aws_iam_policy_document.task_execution_iam_policy_document.json
  name   = "${var.name_prefix}-td-role-policy"
}

data "aws_iam_policy_document" "task_execution_iam_policy_document" {
  statement {
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
      "*",
    ]
  }
}
resource "aws_iam_role_policy_attachment" "ecs_task_execution_role_policy_attach" {
  role       = aws_iam_role.task_execution_iam_role.name
  policy_arn = aws_iam_policy.task_execution_iam_policy.arn
}
resource "aws_iam_role" "ui_task_iam_role" {
  name               = "${var.name_prefix}-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks.json
}
resource "aws_iam_policy" "ui_task_policy" {
  policy = data.aws_iam_policy_document.ui_task_policy_document.json
  name   = "${var.name_prefix}-task-role-policy"
}
data "aws_iam_policy_document" "ui_task_policy_document" {
  statement {
    actions = [
      "ec2:DescribeImages"
    ]
    resources = [
      "*",
    ]
  }
}
resource "aws_iam_role_policy_attachment" "ecs_task_role_policy_attach" {
  role       = aws_iam_role.ui_task_iam_role.name
  policy_arn = aws_iam_policy.ui_task_policy.arn
}
