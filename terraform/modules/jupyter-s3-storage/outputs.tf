output "jupyterhub_bucket" {
  description = "The name of the JupyterHub storage bucket"
  value       = aws_s3_bucket.jupyter_storage
}

output "jupyterhub_kms" {
  description = "The name of the JupyterHub storage bucket"
  value       = aws_kms_key.jupyter_bucket_master_key
}
