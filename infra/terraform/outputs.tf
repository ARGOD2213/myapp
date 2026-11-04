output "app_url" {
  value = "https://${aws_cloudfront_distribution.web.domain_name}"
}

output "ecr_backend_repository" {
  value = aws_ecr_repository.backend.name
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.backend.name
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.web.id
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}

output "db_endpoint" {
  value = aws_db_instance.postgres.address
}
