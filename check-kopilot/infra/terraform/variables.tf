variable "access_key" {
  description = "NCP Access Key"
  type        = string
  sensitive   = true
}

variable "secret_key" {
  description = "NCP Secret Key"
  type        = string
  sensitive   = true
}

variable "region" {
  description = "NCP Region (e.g., FKR)"
  type        = string
  default     = "FKR"
}

variable "nks_login_key_name" {
  description = "SSH key name for NKS Worker"
  type        = string
}

variable "mysql_password" {
  description = "Cloud DB for MySQL root password"
  type        = string
  sensitive   = true
}
