package de.solidblocks.rds.postgresql.test

import mu.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.containers.output.Slf4jLogConsumer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(RdsTestBedExtension::class)
class RdsPostgresqlLocalBackupIntegrationTest {

    private val logger = KotlinLogging.logger {}

    companion object {
        val database = "database1"
    }

    @Test
    fun testDatabaseKeepsDataBetweenRestarts(testBed: RdsTestBed) {

        val logConsumer = TestContainersLogConsumer(Slf4jLogConsumer(logger))

        val dataDir = initWorldReadableTempDir()
        val localBackupDir = initWorldReadableTempDir()

        val container = testBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                ), dataDir, logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }

        // on first start instance should be initialized and an initial backup should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")


        val user = UUID.randomUUID().toString()

        container.createJdbi().also {

            it.waitForReady()
            it.createUserTable()
            it.insertUser(user)

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user
            }.hasSize(1)
        }


        container.stop()
        logConsumer.clear()
        container.start()

        // on second start with persistent storage no initializing ord backup should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is not empty")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")

        container.createJdbi().also {

            it.waitForReady()

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user
            }.hasSize(1)
        }
    }

    @Test
    fun testRestoreDatabaseFromFullBackup(rdsTestBed: RdsTestBed) {

        val logConsumer = TestContainersLogConsumer(Slf4jLogConsumer(logger))

        val localBackupDir = initWorldReadableTempDir()
        val postgresContainer1 = rdsTestBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                ), initWorldReadableTempDir(), logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }

        // on first start instance should be initialized and an initial backup should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")


        val user = UUID.randomUUID().toString()

        postgresContainer1.createJdbi().also {

            it.waitForReady()
            it.createUserTable()
            it.insertUser(user)

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user
            }.hasSize(1)
        }


        postgresContainer1.execInContainer("/rds/bin/backup-full.sh")

        postgresContainer1.stop()
        logConsumer.clear()

        val postgresContainer2 = rdsTestBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                ), initWorldReadableTempDir(), logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }


        // on second start without persistent storage restore should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")

        postgresContainer2.createJdbi().also {

            it.waitForReady()

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user
            }.hasSize(1)
        }
    }

    @Test
    fun testRestoreDatabasePitr(rdsTestBed: RdsTestBed) {

        val logConsumer = TestContainersLogConsumer(Slf4jLogConsumer(logger))
        val localBackupDir = initWorldReadableTempDir()

        val checkpointTimeout = 30L
        val postgresContainer1 = rdsTestBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                        "DB_POSTGRES_EXTRA_CONFIG" to "checkpoint_timeout = ${checkpointTimeout}",
                        ), initWorldReadableTempDir(), logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }

        // on first start instance should be initialized and an initial backup should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")


        val user1 = UUID.randomUUID().toString()
        val user2 = UUID.randomUUID().toString()
        val user3 = UUID.randomUUID().toString()

        postgresContainer1.createJdbi().also {

            it.waitForReady()
            it.createUserTable()
            it.insertUser(user1)

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user1
            }.hasSize(1)
        }

        postgresContainer1.execInContainer("/rds/bin/backup-incr.sh")

        postgresContainer1.createJdbi().also {
            it.insertUser(user2)
            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user2
            }.hasSize(1)
        }

        Thread.sleep((checkpointTimeout + 10) *1000)
        val user2Timestamp = Instant.now()

        postgresContainer1.execInContainer("/rds/bin/backup-incr.sh")
        postgresContainer1.createJdbi().also {
            it.insertUser(user3)
            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user3
            }.hasSize(1)
        }

        postgresContainer1.execInContainer("/rds/bin/backup-incr.sh")
        postgresContainer1.stop()
        logConsumer.clear()

        val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

        val postgresContainer2 = rdsTestBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                        "DB_RESTORE_PITR" to formatter.format(user2Timestamp),
                ), initWorldReadableTempDir(), logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }

        // on second start without persistent storage restore should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")

        postgresContainer2.createJdbi().also {

            it.waitForReady()

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user1
            }.hasSize(1)
            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user2
            }.hasSize(1)
            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user3
            }.hasSize(0)
        }
    }

    @Test
    fun testRestoreDatabaseFromDifferentialBackup(rdsTestBed: RdsTestBed) {

        val logConsumer = TestContainersLogConsumer(Slf4jLogConsumer(logger))

        val localBackupDir = initWorldReadableTempDir()
        val postgresContainer1 = rdsTestBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                ), initWorldReadableTempDir(), logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }


        // on first start instance should be initialized and an initial backup should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")


        val user1 = UUID.randomUUID().toString()

        postgresContainer1.createJdbi().also {

            it.waitForReady()
            it.createUserTable()
            it.insertUser(user1)

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user1
            }.hasSize(1)
        }


        postgresContainer1.execInContainer("/rds/bin/backup-full.sh")

        val user2 = UUID.randomUUID().toString()
        postgresContainer1.createJdbi().also {
            it.insertUser(user2)
            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user2
            }.hasSize(1)
        }


        postgresContainer1.execInContainer("/rds/bin/backup-diff.sh")

        postgresContainer1.stop()
        logConsumer.clear()

        val postgresContainer2 = rdsTestBed.createAndStartPostgresContainer(
                mapOf(
                        "DB_BACKUP_LOCAL" to "1",
                ), initWorldReadableTempDir(), logConsumer
        ) {
            it.withFileSystemBind(localBackupDir.absolutePath, "/storage/backup")
        }

        // on second start without persistent storage restore should be executed
        logConsumer.waitForLogLine("[solidblocks-rds-postgresql] provisioning completed")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] data dir is empty")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] initializing database instance")
        logConsumer.assertHasLogLine("[solidblocks-rds-postgresql] restoring database from backup")
        logConsumer.assertHasNoLogLine("[solidblocks-rds-postgresql] executing initial backup")
        logConsumer.waitForLogLine("database system is ready to accept connections")

        postgresContainer2.createJdbi().also {

            it.waitForReady()

            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user1
            }.hasSize(1)
            assertThat(it.selectAllUsers()).filteredOn {
                it["name"] == user2
            }.hasSize(1)
        }
    }


}
