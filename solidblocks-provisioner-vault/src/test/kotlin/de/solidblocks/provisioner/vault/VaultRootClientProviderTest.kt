package de.solidblocks.provisioner.vault

import de.solidblocks.cloud.model.CloudRepository
import de.solidblocks.cloud.model.EnvironmentRepository
import de.solidblocks.test.TestEnvironment
import de.solidblocks.test.TestEnvironmentExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File

class KDockerComposeContainer(file: File) : DockerComposeContainer<KDockerComposeContainer>(file)

@Testcontainers
@ExtendWith(TestEnvironmentExtension::class)
class VaultRootClientProviderTest {

    companion object {
        @Container
        val environment: DockerComposeContainer<*> =
            KDockerComposeContainer(File("src/test/resources/docker-compose.yml"))
                .apply {
                    withExposedService("vault", 8200)
                    start()
                }

        fun vaultAddress() = "http://localhost:${environment.getServicePort("vault", 8200)}"
    }

    @Test
    fun testInitAndUnseal(testEnvironment: TestEnvironment) {

        val reference = testEnvironment.createEnvironment()

        val cloudRepository = CloudRepository(testEnvironment.dsl)
        val environmentRepository = EnvironmentRepository(testEnvironment.dsl, cloudRepository)

        val provider = VaultRootClientProvider(reference, environmentRepository, vaultAddress())

        val environmentBefore = environmentRepository.getEnvironment(reference)!!
        assertTrue(environmentBefore.configValues.none { it.name == "vault-unseal-key-0" })
        assertTrue(environmentBefore.configValues.none { it.name == "vault-unseal-key-1" })
        assertTrue(environmentBefore.configValues.none { it.name == "vault-unseal-key-2" })
        assertTrue(environmentBefore.configValues.none { it.name == "vault-unseal-key-3" })
        assertTrue(environmentBefore.configValues.none { it.name == "vault-unseal-key-4" })

        val vaultClient = provider.createClient()
        val environment = environmentRepository.getEnvironment(reference)!!

        assertTrue(environment.configValues.any { it.name == "vault-unseal-key-0" })
        assertTrue(environment.configValues.any { it.name == "vault-unseal-key-1" })
        assertTrue(environment.configValues.any { it.name == "vault-unseal-key-2" })
        assertTrue(environment.configValues.any { it.name == "vault-unseal-key-3" })
        assertTrue(environment.configValues.any { it.name == "vault-unseal-key-4" })
        assertTrue(environment.configValues.any { it.name == "vault-root-token" })

        assertFalse(vaultClient.opsForSys().unsealStatus.isSealed)
        assertTrue(vaultClient.opsForSys().isInitialized)

        val vaultClient1 = provider.createClient()
        assertFalse(vaultClient1.opsForSys().unsealStatus.isSealed)

        Assertions.assertThat(provider.createClient().list("/")).hasSize(0)
    }
}
