resource "aws_cloudwatch_event_rule" "fire_at_three_seventeen" {
  name                = "between-three-and-four"
  description         = "Fires every day at 03:17am"
  schedule_expression = "cron(17 3 * * ? *)"
}

resource "aws_cloudwatch_event_target" "cleanup_lambda_target" {
  rule      = aws_cloudwatch_event_rule.fire_at_three_seventeen.name
  target_id = "lambda"
  arn       = aws_lambda_function.cleanup_lambda.arn
}

resource "aws_lambda_permission" "allow_cloudwatch_to_call_cleanup_lambda" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.cleanup_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.fire_at_three_seventeen.arn
}

resource "aws_cloudwatch_log_group" "cleanup_lambda_logs" {
  name              = var.name_prefix
  retention_in_days = 180
}
