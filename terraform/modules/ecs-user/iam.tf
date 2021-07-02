resource "aws_iam_role" "user_host" {
  name               = "${var.name_prefix}Role"
  assume_role_policy = data.aws_iam_policy_document.assume_role_ec2.json
  tags               = var.common_tags
}

resource "aws_iam_role_policy_attachment" "user_host_ec2_tagging" {
  role       = aws_iam_role.user_host.name
  policy_arn = aws_iam_policy.user_host_ec2_tagging.arn
}

resource "aws_iam_policy" "user_host_ec2_tagging" {
  name        = "UserHostEc2TaggingPolicy"
  description = "Allow user host instances to modify tags"
  policy      = data.aws_iam_policy_document.user_host_ec2_tagging.json
}

data "aws_iam_policy_document" "user_host_ec2_tagging" {
  statement {
    sid    = "EnableEC2PermissionsHost"
    effect = "Allow"

    actions = [
      "ec2:ModifyInstanceMetadataOptions",
      "ec2:*Tags",
    ]
    resources = ["arn:aws:ec2:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:instance/*"]
  }
}

data "aws_iam_policy_document" "assume_role_ec2" {
  statement {
    sid     = "AllowEC2ToAssumeRole"
    actions = ["sts:AssumeRole"]

    principals {
      identifiers = ["ec2.amazonaws.com"]
      type        = "Service"
    }
  }
}

resource "aws_iam_instance_profile" "user_host" {
  name = aws_iam_role.user_host.name
  role = aws_iam_role.user_host.id
}

resource "aws_iam_role_policy_attachment" "ecs" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role"
  role       = aws_iam_role.user_host.id
}

resource "aws_iam_role_policy_attachment" "ssm" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  role       = aws_iam_role.user_host.id
}

resource "aws_iam_role_policy_attachment" "cloudwatch_logging" {
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
  role       = aws_iam_role.user_host.id
}

data "aws_iam_policy_document" "user_host" {
  statement {
    sid     = "AllowUserHostECRGetAuthToken"
    effect  = "Allow"
    actions = ["ecr:GetAuthorizationToken"]

    resources = ["*"]
  }

  statement {
    sid    = "AllowUserHostPullForUserContainers"
    effect = "Allow"
    actions = [
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability"
    ]

    resources = ["arn:aws:ecr:${data.aws_region.current.name}:${var.management_account}:repository/orchestration-service/*"]
  }

  statement {
    sid    = "AllowUserHostListPackagesBucket"
    effect = "Allow"
    actions = [
      "s3:ListBucket",
    ]
    resources = [
      "arn:aws:s3:::${var.s3_packages.bucket}",
    ]
  }

  statement {
    sid    = "AllowUserHostDownloadPackages"
    effect = "Allow"
    actions = [
      "s3:Get*",
      "s3:List*",
      "kms:Decrypt"
    ]
    resources = [
      "arn:aws:s3:::${var.s3_packages.bucket}/${var.s3_packages.key_prefix}/*",
      var.s3_packages.cmk_arn
    ]
  }

  statement {
    sid       = "AllowSetInstanceHealth"
    effect    = "Allow"
    actions   = ["autoscaling:SetInstanceHealth"]
    resources = [aws_autoscaling_group.user_host.arn]
  }

}

resource "aws_iam_role_policy" "user_host" {
  policy = data.aws_iam_policy_document.user_host.json
  role   = aws_iam_role.user_host.id
}
