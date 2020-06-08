data "aws_iam_policy_document" "ecs-tasks" {
  statement {
    sid = "AllowECSTasksAssumeRole"
    actions = [
      "sts:AssumeRole"
    ]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "task_role" {
  statement {
    sid = "AllowDynamoDBActionsOnUserTable"
    actions = [
      "dynamodb:CreateTable",
      "dynamodb:DeleteItem",
      "dynamodb:GetItem",
      "dynamodb:ListTables",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
    ]
    resources = ["*"]
  }
  statement {
    sid = "AllowEC2DescribeImage"
    actions = [
      "ec2:DescribeImages",
    ]

    resources = ["*"]
  }
  statement {
    sid = "AllowECSActionsToCreateUserContainers"
    actions = [
      "ecs:CreateService",
      "ecs:DeleteService",
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
      "ecs:RunTask",
      "ecs:UpdateService",
    ]
    resources = ["*"]
  }
  statement {
    sid = "AllowELBActionsToAttachUserContainers"
    actions = [
      "elasticloadbalancing:CreateRule",
      "elasticloadbalancing:CreateTargetGroup",
      "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:DeleteRule",
      "elasticloadbalancing:DescribeListeners",
      "elasticloadbalancing:DescribeLoadBalancers",
      "elasticloadbalancing:DescribeRules",
      "elasticloadbalancing:DescribeTargetGroupAttributes",
    ]
    resources = ["*"]
  }
  statement {
    sid = "AllowIAMActionsForUserContainerRoles"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreatePolicy",
      "iam:CreateRole",
      "iam:DetachRolePolicy",
      "iam:DeletePolicy",
      "iam:DeleteRole",
      "iam:PassRole",
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "ecs_task_role" {
  name   = "${var.name_prefix}-task_role"
  policy = data.aws_iam_policy_document.task_role.json
}

resource "aws_iam_role" "ecs_task_execution_role" {
  name               = "${var.name_prefix}-ecs-task-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs-tasks.json
}

resource "aws_iam_role" "ecs_task_role" {
  name               = "${var.name_prefix}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs-tasks.json
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_role_policy_attach" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy_attachment" "ecs_task_role_policy_attach" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_task_role.arn
}
