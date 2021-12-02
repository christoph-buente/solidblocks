package de.solidblocks.cloud.config

import de.solidblocks.cloud.config.model.CloudConfiguration
import de.solidblocks.cloud.config.model.CloudEnvironmentConfiguration

class Constants {
    class ConfigKeys {
        companion object {
            const val GITHUB_TOKEN_RO_KEY = "github_token_ro"
            const val GITHUB_USERNAME_KEY = "github_username"

            const val HETZNER_CLOUD_API_TOKEN_RO_KEY = "hetzner_cloud_api_key_ro"
            const val HETZNER_CLOUD_API_TOKEN_RW_KEY = "hetzner_cloud_api_key_rw"

            const val HETZNER_DNS_API_TOKEN_RW_KEY = "hetzner_dns_api_key_rw"

            const val CONSUL_MASTER_TOKEN_KEY = "consul_master_token"
            const val CONSUL_SECRET_KEY = "consul_secret"

            const val SSH_PUBLIC_KEY = "ssh_public_key"
            const val SSH_PRIVATE_KEY = "ssh_private_key"

            const val SSH_IDENTITY_PUBLIC_KEY = "ssh_identity_public_key"
            const val SSH_IDENTITY_PRIVATE_KEY = "ssh_identity_private_key"
        }
    }

    class Vault {
        companion object {
            val CONTROLLER_POLICY_NAME = "controller"
            val BACKUP_POLICY_NAME = "backup"

            fun vaultAddr(cloud: CloudConfiguration, environment: CloudEnvironmentConfiguration) =
                "https://vault.${environment.name}.${cloud.rootDomain}:8200"
        }
    }
}