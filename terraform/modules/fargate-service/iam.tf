resource "aws_iam_role" "ecs_autoscale_role" {
  name               = "${var.name_prefix}-autoscaling-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

data "aws_iam_policy_document" "ecs_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["ecs.application-autoscaling.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role_policy" "ecs_autoscaling" {
  role   = aws_iam_role.ecs_autoscale_role.id
  policy = data.aws_iam_policy_document.ecs_autoscaling.json
}

data "aws_iam_policy_document" "ecs_autoscaling" {
  statement {
    sid    = "ECSAutoscaling"
    effect = "Allow"

    actions = [
      "ecs:DescribeServices",
      "ecs:UpdateService",
      "cloudwatch:DeleteAlarms",
      "cloudwatch:DescribeAlarms",
      "cloudwatch:PutMetricAlarm"
    ]

    resources = ["*"]
  }
}
