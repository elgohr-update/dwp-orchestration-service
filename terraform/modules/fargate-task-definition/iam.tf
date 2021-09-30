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
    sid = "AllowDynamoDBCreateTable"
    actions = [
      "dynamodb:CreateTable",
    ]
    resources = ["*"]
  }
  statement {
    sid = "AllowDynamoDbListALlTables"
    actions = [
      "dynamodb:ListTables",
    ]
    resources = ["*"]
  }
  statement {
    sid = "AllowDynamoDBActionsOnUserTable"
    actions = [
      "dynamodb:DeleteItem",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
    ]
    resources = ["arn:aws:dynamodb:eu-west-2:${var.account}:table/orchestration_service_user_tasks"]
  }
  statement {
    sid = "AllowSpecificEC2DescribeActions"
    actions = [
      "ec2:DescribeImages",
      "ec2:DescribeVpcs",
      "ec2:DescribeInternetGateways"
    ]

    resources = ["*"]
  }
  statement {
    sid = "AllowECSActionsToCreateUserContainers"
    actions = [
      "ecs:CreateService",
      "ecs:DeleteService",
      "ecs:DescribeServices",
      "ecs:RunTask",
      "ecs:UpdateService",
    ]
    resources = ["*"]
    condition {
      test     = "ArnEquals"
      values   = ["arn:aws:ecs:eu-west-2:${var.account}:cluster/orchestration-service-user-host"]
      variable = "ecs:cluster"
    }
  }
  statement {
    sid = "ECSTaskDefinitionsActions"
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    // Doesn't accept conditions or resource limitations
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
    // Unable to restrict conditions or resources effectively
    resources = ["*"]
  }
  statement {
    sid = "AllowIAMPolicyActionsForUserContainerRoles"
    actions = [
      "iam:CreatePolicy",
      "iam:DeletePolicy",
    ]
    resources = [
      "arn:aws:iam::${var.account}:policy/analytical-*-iamPolicyTaskArn",
      "arn:aws:iam::${var.account}:policy/analytical-*-iamPolicyUserArn",
    ]
  }
  statement {
    sid = "AllowIAMRoleActionsForUserContainerRoles"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateRole",
      "iam:DetachRolePolicy",
      "iam:DeleteRole",
      "iam:PassRole",
      "iam:GetRole",
      "iam:ListRoles",
      "iam:ListAttachedRolePolicies",
    ]
    resources = [
      "arn:aws:iam::${var.account}:role/orchestration-service-user-*",
      "arn:aws:iam::${var.account}:role/app/os/*",
    ]
  }
  statement {
    sid = "AllowKMSKeyDescribeForUserContainer"
    actions = [
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowGetRdsCredentials"
    effect = "Allow"
    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:ListSecretVersionIds"
    ]
    resources = [
      var.rds_credentials_secret_arn,
    ]
  }

  statement {
    sid    = "AllowRdsDataExecute"
    effect = "Allow"
    actions = [
      "rds-data:ExecuteStatement"
    ]
    resources = ["*"] // RDS-data does not support resource ARNs, access is restricted by credential permissions
  }

  statement {
    sid       = "AllowInvokeAPLambda"
    actions   = ["lambda:InvokeFunction"]
    resources = [var.ap_lambda_arn]
  }

}

resource "aws_iam_policy" "ecs_task_role" {
  name   = "${var.name_prefix}-task_role"
  policy = data.aws_iam_policy_document.task_role.json
}

resource "aws_iam_role" "ecs_task_execution_role" {
  name               = "${var.name_prefix}-ecs-task-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs-tasks.json
  tags               = merge(var.common_tags, { "Name" : "${var.name_prefix}-ecs-exec-role" })
}

resource "aws_iam_role" "ecs_task_role" {
  name               = "${var.name_prefix}-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs-tasks.json
  tags               = merge(var.common_tags, { "Name" : "${var.name_prefix}-ecs-task-role" })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_role_policy_attach" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy_attachment" "ecs_task_role_policy_attach" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_task_role.arn
}
