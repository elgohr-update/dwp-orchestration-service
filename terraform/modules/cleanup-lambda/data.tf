data "archive_file" "cleanup_lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/lambda"
  output_path = "${path.module}/${var.name_prefix}.zip"
}
