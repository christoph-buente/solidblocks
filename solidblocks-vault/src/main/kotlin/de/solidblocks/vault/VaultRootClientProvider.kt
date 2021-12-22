package de.solidblocks.vault

import de.solidblocks.cloud.model.EnvironmentRepository
import de.solidblocks.vault.VaultConstants.ROOT_TOKEN_KEY
import de.solidblocks.vault.VaultConstants.UNSEAL_KEY_PREFIX
import mu.KotlinLogging
import org.springframework.vault.authentication.TokenAuthentication
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.core.VaultTemplate
import java.net.URI

class VaultRootClientProvider(
    private val cloudName: String,
    private val environmentName: String,
    private val environmentRepository: EnvironmentRepository,
    private val vaultAddressOverride: String? = null
) {

    private fun vaultAddress(): String {

        if (vaultAddressOverride != null) {
            return vaultAddressOverride
        }

        val environment = environmentRepository.getEnvironment(cloudName, environmentName)
            ?: throw RuntimeException("environment '$environmentName' not found for cloud '$cloudName'")

        return VaultConstants.vaultAddress(environment)
    }

    private val logger = KotlinLogging.logger {}

    private var vaultTemplate: VaultTemplate? = null

    private fun getVaultCredentials(): VaultCredentials {
        val environment = environmentRepository.getEnvironment(cloudName, environmentName)
            ?: throw RuntimeException("environment '$environmentName' not found for cloud '$cloudName'")

        val rootToken = environment.configValues.firstOrNull { it.name == ROOT_TOKEN_KEY }
            ?: throw RuntimeException("vault at '${vaultAddress()}' is initialized, but no vault root token found for cloud '$cloudName'")
        val unsealKeys = environment.configValues.filter { it.name.startsWith(UNSEAL_KEY_PREFIX) }.map { it.value }

        return VaultCredentials(rootToken.value, unsealKeys)
    }

    fun createClient(): VaultTemplate {

        if (vaultTemplate != null) {
            return vaultTemplate!!
        }

        val environment = environmentRepository.getEnvironment(cloudName, environmentName)
            ?: throw RuntimeException("environment '$environmentName' not found for cloud '$cloudName'")

        val initializingVaultManager = InitializingVaultManager(vaultAddress())
        val hasUnsealKeys = environment.configValues.any { it.name.startsWith(UNSEAL_KEY_PREFIX) }

        if (!initializingVaultManager.isInitialized()) {

            if (hasUnsealKeys) {
                throw RuntimeException("environment '$environmentName' for cloud '$cloudName' has unseal keys, but is not initialized")
            }

            val result = initializingVaultManager.initializeAndUnseal()
            environmentRepository.updateEnvironment(
                cloudName,
                environmentName,
                result.unsealKeys.mapIndexed { i, key -> "$UNSEAL_KEY_PREFIX-$i" to key }.toMap()
            )
            environmentRepository.updateEnvironment(cloudName, environmentName, ROOT_TOKEN_KEY, result.rootToken)
        }

        val credentials = getVaultCredentials()

        if (initializingVaultManager.isSealed()) {
            initializingVaultManager.unseal(credentials)
        }

        vaultTemplate = VaultTemplate(
            VaultEndpoint.from(URI.create(vaultAddress())),
            TokenAuthentication(credentials.rootToken)
        )

        return vaultTemplate!!
    }
}