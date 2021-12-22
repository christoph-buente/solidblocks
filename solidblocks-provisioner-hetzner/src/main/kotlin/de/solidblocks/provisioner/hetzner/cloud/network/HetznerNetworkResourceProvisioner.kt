package de.solidblocks.provisioner.hetzner.cloud.network

import de.solidblocks.api.resources.ResourceDiff
import de.solidblocks.api.resources.infrastructure.IInfrastructureResourceProvisioner
import de.solidblocks.api.resources.infrastructure.IResourceLookupProvider
import de.solidblocks.core.Result
import de.solidblocks.provisioner.hetzner.cloud.BaseHetznerProvisioner
import me.tomsdevsn.hetznercloud.HetznerCloudAPI
import me.tomsdevsn.hetznercloud.objects.request.NetworkRequest
import mu.KotlinLogging

class HetznerNetworkResourceProvisioner(hetznerCloudAPI: HetznerCloudAPI) :
    IResourceLookupProvider<INetworkLookup, NetworkRuntime>,
    IInfrastructureResourceProvisioner<Network,
        NetworkRuntime>, BaseHetznerProvisioner<Network, NetworkRuntime, HetznerCloudAPI>(hetznerCloudAPI) {

    private val logger = KotlinLogging.logger {}

    override fun apply(
        resource: Network
    ): Result<*> {

        val request = NetworkRequest.builder()
        request.name(resource.id)
        request.ipRange(resource.ipRange)

        return checkedApiCall {
            it.createNetwork(request.build())
        }
    }

    override fun diff(resource: Network): Result<ResourceDiff> {
        return lookup(resource).mapResourceResultOrElse(
            {
                ResourceDiff(resource, false)
            },
            {
                ResourceDiff(resource, true)
            }
        )
    }

    private fun destroy(id: Long): Result<*> {
        return checkedApiCall {
            it.deleteNetwork(id)
        }
    }

    override fun destroyAll(): Boolean {
        logger.info { "destroying all networks" }

        return checkedApiCall {
            it.allNetworks.networks
        }.mapSuccessNonNullBoolean {
            it.map { network ->
                logger.info { "destroying network '${network.name}'" }
                destroy(network.id)
            }.any { it.success() }
        }
    }

    override fun destroy(resource: Network): Boolean {
        return lookup(resource).mapNonNullResult {
            destroy(it.id.toLong())
        }.mapSuccessNonNullBoolean { true }
    }

    override fun getResourceType(): Class<*> {
        return Network::class.java
    }

    override fun lookup(lookup: INetworkLookup): Result<NetworkRuntime> {
        return checkedApiCall {
            it.getNetworksByName(lookup.id()).networks.firstOrNull()
        }.mapNonNullResult {
            NetworkRuntime(it.id.toString())
        }
    }

    override fun getLookupType(): Class<*> {
        return INetworkLookup::class.java
    }
}