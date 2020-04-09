resource "aws_iam_role" "task_execution_iam_role" {
  name               = "${var.name_prefix}-td-role"
  assume_role_policy = data.aws_iam_policy_document.task_execution_iam_policy.json
}
data "aws_iam_policy_document" "task_execution_iam_policy" {
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
resource "aws_iam_role" "ui_task_iam_role" {
  name               = "${var.name_prefix}-task-role"
  assume_role_policy = data.aws_iam_policy_document.ui_task_policy.json
}
data "aws_iam_policy_document" "ui_task_policy" {
  statement {
    actions = [
      "ec2:DescribeImages",
    ]

    resources = [
      "*",
    ]
  }
}
