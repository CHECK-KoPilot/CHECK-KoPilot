resource "ncloud_vpc" "vpc" {
  name            = "team3-vpc"
  ipv4_cidr_block = "10.0.0.0/16"
}

resource "ncloud_subnet" "public_lb" {
  vpc_no         = ncloud_vpc.vpc.id
  subnet         = "10.0.1.0/24"
  zone           = "FKR-1"
  network_acl_no = ncloud_vpc.vpc.default_network_acl_no
  subnet_type    = "PUBLIC"
  name           = "team3-nks-lb-sub"
  usage_type     = "LOADB"
}

resource "ncloud_subnet" "private_node" {
  vpc_no         = ncloud_vpc.vpc.id
  subnet         = "10.0.2.0/24"
  zone           = "FKR-1"
  network_acl_no = ncloud_vpc.vpc.default_network_acl_no
  subnet_type    = "PRIVATE"
  name           = "team3-nks-node-sub"
  usage_type     = "GEN"
}

resource "ncloud_subnet" "private_db" {
  vpc_no         = ncloud_vpc.vpc.id
  subnet         = "10.0.3.0/24"
  zone           = "FKR-1"
  network_acl_no = ncloud_vpc.vpc.default_network_acl_no
  subnet_type    = "PRIVATE"
  name           = "team3-db-sub"
  usage_type     = "GEN"
}

resource "ncloud_nat_gateway" "nat_gw" {
  vpc_no = ncloud_vpc.vpc.id
  zone   = "FKR-1"
  name   = "team3-nat-gw"
}

resource "ncloud_route" "nat_route" {
  route_table_no         = ncloud_vpc.vpc.default_private_route_table_no
  destination_cidr_block = "0.0.0.0/0"
  target_type            = "NATGW"
  target_name            = ncloud_nat_gateway.nat_gw.name
  target_no              = ncloud_nat_gateway.nat_gw.id
}
