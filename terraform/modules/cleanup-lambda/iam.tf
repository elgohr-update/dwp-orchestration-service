# resource "aws_iam_role" "cleanup_lambda_role" {
#   name               = "${var.name_prefix}-role"
#   assume_role_policy = data.aws_iam_policy_document.assume_role_cleanup_lambda.json
#   tags               = var.common_tags
# }

# data "aws_iam_policy_document" "assume_role_cleanup_lambda" {
#   statement {
#     actions = ["sts:AssumeRole"]

#     principals {
#       identifiers = ["lambda.amazonaws.com"]
#       type        = "Service"
#     }
#   }
# }

# resource "aws_iam_role_policy_attachment" "cleanup_lambda_basic_policy_attach" {
#   role       = aws_iam_role.cleanup_lambda_role.name
#   policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
# }

# resource "aws_iam_role_policy" "cleanup_lambda_dynamo_policy" {
#   role   = aws_iam_role.cleanup_lambda_role.id
#   policy = data.aws_iam_policy_document.cleanup_lambda_dynamo_policy_document.json
# }

# data aws_iam_policy_document cleanup_lambda_dynamo_policy_document {
#   statement {
#     sid = "cleanupLambdaDynamo"
#     actions = [
#       "dynamodb:Scan"
#     ]
#     resources = ["arn:aws:dynamodb:${var.region}:${var.account}:table/${var.table_name}"]
#   }
# }

# resource "aws_iam_role_policy" "cleanup_lambda_logging_policy" {
#   role   = aws_iam_role.cleanup_lambda_role.id
#   policy = data.aws_iam_policy_document.cleanup_lambda_logging_policy_document.json
# }

# data aws_iam_policy_document cleanup_lambda_logging_policy_document {
#   statement {
#     sid = "cleanupLambdaLogging"
#     actions = [
#       "logs:PutLogEvents",
#       "logs:CreateLogStream"
#     ]
#     resources = [aws_cloudwatch_log_group.cleanup_lambda_logs.arn]
#   }
# }

# resource "aws_iam_role_policy" "cleanup_lambda_ec2_policy_policy" {
#   role   = aws_iam_role.cleanup_lambda_role.id
#   policy = data.aws_iam_policy_document.cleanup_lambda_ec2_policy_document.json
# }

# data aws_iam_policy_document cleanup_lambda_ec2_policy_document {
#   statement {
#     sid = "cleanupLambdaEc2"
#     actions = [
#       "ec2:CreateNetworkInterface",
#       "ec2:DescribeNetworkInterfaces",
#       "ec2:DeleteNetworkInterface",
#       "ec2:DescribeSecurityGroups",
#       "ec2:DescribeSubnets",
#       "ec2:DescribeVpcs"
#     ]
#     resources = ["*"]
#   }
# }
