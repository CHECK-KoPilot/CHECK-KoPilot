resource "ncloud_mysql" "mysql" {
  service_name       = "team3-mysql"
  server_name_prefix = "team3-db"
  vpc_no             = data.ncloud_vpc.vpc.id
  subnet_no          = ncloud_subnet.private_db.id

  image_product_code = "SW.VSVR.OS.LNX64.UBNTU.SVR2004.MYSQL.8033.B050"
  product_code       = "SVR.VSVR.STAND.C002.M008.NET.SSD.B050.G002"
  data_storage_type  = "SSD"

  is_ha                        = true
  backup_file_retention_period = 7

  user_name     = "kopilot"
  user_password = var.mysql_password
  database_name = "kopilot"
}

resource "ncloud_redis" "redis" {
  service_name       = "team3-redis"
  server_name_prefix = "team3-rd"
  vpc_no             = data.ncloud_vpc.vpc.id
  subnet_no          = ncloud_subnet.private_db.id

  image_product_code = "SW.VSVR.OS.LNX64.CNTOS.0708.REDIS.7015.B050"
  product_code       = "SVR.VSVR.STAND.C002.M008.NET.SSD.B050.G002"

  is_ha = true
}
