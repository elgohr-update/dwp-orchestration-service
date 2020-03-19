output outputs {
  value = {
    aws_availability_zones      = data.aws_availability_zones.current
    aws_subnets_private         = aws_subnet.private
    aws_route_table_private_ids = aws_route_table.private[*].id
  }
}
