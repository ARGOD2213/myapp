variable "environment" {
  description = "staging or prod (one Terraform workspace/state per environment)"
  type        = string
  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be staging or prod."
  }
}

variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "initial_image_tag" {
  description = "Image tag the very first task definition points at (the CD pipeline replaces it)."
  type        = string
  default     = "bootstrap"
}

variable "github_repository" {
  description = "owner/repo allowed to assume the deploy role"
  type        = string
  default     = "ARGOD2213/myapp"
}

variable "create_github_oidc_provider" {
  description = "Set false if the account already has the GitHub OIDC provider."
  type        = bool
  default     = true
}
