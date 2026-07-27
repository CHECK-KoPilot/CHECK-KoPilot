resource "ncloud_access_control_group" "db_acg" {
  name        = "team3-db-acg"
  vpc_no      = data.ncloud_vpc.vpc.id
  description = "Database Access Control Group for team3"
}

resource "ncloud_access_control_group_rule" "db_acg_rule" {
  access_control_group_no = ncloud_access_control_group.db_acg.id

  inbound {
    protocol    = "TCP"
    ip_block    = ncloud_subnet.private_node.subnet
    port_range  = "3306"
    description = "Allow MySQL traffic from NKS Node Subnet"
  }

  inbound {
    protocol    = "TCP"
    ip_block    = ncloud_subnet.private_node.subnet
    port_range  = "6379"
    description = "Allow Redis traffic from NKS Node Subnet"
  }

  outbound {
    protocol   = "TCP"
    ip_block   = "0.0.0.0/0"
    port_range = "1-65535"
  }
}
