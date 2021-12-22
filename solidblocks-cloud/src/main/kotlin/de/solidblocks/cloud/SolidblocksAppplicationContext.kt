package de.solidblocks.cloud

import de.solidblocks.base.ProvisionerRegistry
import de.solidblocks.base.lookups.Lookups
import de.solidblocks.cloud.model.CloudRepository
import de.solidblocks.cloud.model.EnvironmentRepository
import de.solidblocks.cloud.model.SolidblocksDatabase
import de.solidblocks.provisioner.Provisioner
import de.solidblocks.provisioner.consul.Consul
import de.solidblocks.provisioner.hetzner.Hetzner
import de.solidblocks.provisioner.minio.Minio
import de.solidblocks.provisioner.minio.MinioCredentials
import de.solidblocks.provisioner.vault.Vault
import de.solidblocks.vault.VaultRootClientProvider

class SolidblocksAppplicationContext(
    jdbcUrl: String,
    val vaultAddressOverride: String? = null,
    val minioCredentialsProvider: (() -> MinioCredentials)? = null
) {

    private var vaultRootClientProvider: VaultRootClientProvider? = null

    val cloudRepository: CloudRepository
    val environmentRepository: EnvironmentRepository
    val cloudManager: CloudManager

    init {
        val database = SolidblocksDatabase(jdbcUrl)
        database.ensureDBSchema()
        cloudRepository = CloudRepository(database.dsl)
        environmentRepository = EnvironmentRepository(database.dsl, cloudRepository)
        cloudManager = CloudManager(cloudRepository, environmentRepository)
    }

    fun vaultRootClientProvider(cloud: String, environment: String): VaultRootClientProvider {
        if (vaultRootClientProvider == null) {
            vaultRootClientProvider =
                VaultRootClientProvider(cloud, environment, environmentRepository, vaultAddressOverride)
        }

        return vaultRootClientProvider!!
    }

    fun createCloudProvisioner(cloud: String, environment: String): CloudProvisioner {
        return CloudProvisioner(
            cloud,
            environment,
            vaultRootClientProvider(cloud, environment),
            createProvisioner(cloud, environment),
            environmentRepository
        )
    }

    fun createProvisioner(cloud: String, environmentName: String): Provisioner {

        val provisionerRegistry = ProvisionerRegistry()
        val provisioner = Provisioner(provisionerRegistry)

        val environment = environmentRepository.getEnvironment(cloud, environmentName)
            ?: throw RuntimeException("environment '$environmentName' not found for cloud '$cloud'")

        Hetzner.registerProvisioners(provisionerRegistry, environment, provisioner)
        Hetzner.registerLookups(provisionerRegistry, provisioner)
        Lookups.registerLookups(provisionerRegistry, provisioner)
        Consul.registerProvisioners(provisionerRegistry, Consul.consulClient(environment))

        Vault.registerProvisioners(provisionerRegistry) {
            vaultRootClientProvider(cloud, environmentName).createClient()
        }

        Minio.registerProvisioners(
            provisionerRegistry,
            minioCredentialsProvider ?: {
                MinioCredentials(Minio.minioAddress(environment), "xx", "ss")
            }
        )

        return provisioner
    }
}