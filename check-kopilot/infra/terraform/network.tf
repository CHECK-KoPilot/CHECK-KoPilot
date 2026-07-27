data "ncloud_vpc" "vpc" {
  name = "hackathon"
}

resource "ncloud_subnet" "public_lb" {
  vpc_no         = data.ncloud_vpc.vpc.id
  subnet         = "192.168.150.0/24"
  zone           = "FKR-1"
  network_acl_no = data.ncloud_vpc.vpc.default_network_acl_no
  subnet_type    = "PUBLIC"
  name           = "team3-nks-lb-sub"
  usage_type     = "LOADB"
}

resource "ncloud_subnet" "private_node" {
  vpc_no         = data.ncloud_vpc.vpc.id
  subnet         = "192.168.151.0/24"
  zone           = "FKR-1"
  network_acl_no = data.ncloud_vpc.vpc.default_network_acl_no
  subnet_type    = "PRIVATE"
  name           = "team3-nks-node-sub"
  usage_type     = "GEN"
}

resource "ncloud_subnet" "private_db" {
  vpc_no         = data.ncloud_vpc.vpc.id
  subnet         = "192.168.152.0/24"
  zone           = "FKR-1"
  network_acl_no = data.ncloud_vpc.vpc.default_network_acl_no
  subnet_type    = "PRIVATE"
  name           = "team3-db-sub"
  usage_type     = "GEN"
}

resource "ncloud_nat_gateway" "nat_gw" {
  vpc_no = data.ncloud_vpc.vpc.id
  zone   = "FKR-1"
  name   = "team3-nat-gw"
}

resource "ncloud_route" "nat_route" {
  route_table_no         = data.ncloud_vpc.vpc.default_private_route_table_no
  destination_cidr_block = "0.0.0.0/0"
  target_type            = "NATGW"
  target_name            = ncloud_nat_gateway.nat_gw.name
  target_no              = ncloud_nat_gateway.nat_gw.id
}
