resource "ncloud_nks_cluster" "cluster" {
  name                 = "team3-cluster"
  cluster_type         = "SVR.VNKS.STAND.C002.M008.NET.SSD.B050.G002"
  login_key_name       = var.nks_login_key_name
  lb_private_subnet_no = ncloud_subnet.public_lb.id
  kube_network_plugin  = "cilium"
  subnet_no_list       = [ncloud_subnet.private_node.id]
  vpc_no               = ncloud_vpc.vpc.id
  zone                 = "FKR-1"
}

resource "ncloud_nks_node_pool" "node_pool" {
  cluster_uuid     = ncloud_nks_cluster.cluster.uuid
  node_pool_name   = "team3-np"
  node_count       = 2
  software_code    = "SW.VSVR.OS.LNX64.UBNTU.SVR2004.B050"
  server_spec_code = "SVR.VSVR.STAND.C004.M016.NET.SSD.B050.G002" 
}
