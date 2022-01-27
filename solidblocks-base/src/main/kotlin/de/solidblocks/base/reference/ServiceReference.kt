package de.solidblocks.base.reference

open class ServiceReference(cloud: String, environment: String, tenant: String, val service: String) :
    TenantReference(cloud, environment, tenant) {
    fun asTenant() = TenantReference(cloud, environment, tenant)
}