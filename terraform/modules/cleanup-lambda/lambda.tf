resource "aws_lambda_function" "cleanup_lambda" {
  filename         = data.archive_file.cleanup_lambda_zip.output_path
  function_name    = var.name_prefix
  role             = aws_iam_role.cleanup_lambda_role.arn
  handler          = "lambda_function.lambda_handler"
  runtime          = "python3.8"
  source_code_hash = data.archive_file.cleanup_lambda_zip.output_base64sha256
  tags             = merge(var.common_tags, { Name = var.name_prefix, "ProtectSensitiveData" = "False" })
  timeout          = 60

  vpc_config {
    subnet_ids         = var.aws_subnets_private
    security_group_ids = [aws_security_group.cleanup_lambda_sg.id]
    vpc_id             = var.vpc_id
  }

  environment {
    variables = {
      TABLE_NAME = var.table_name
      REGION     = var.region
      ENDPOINT   = "${var.fqdn}/cleanup"
    }
  }
}
