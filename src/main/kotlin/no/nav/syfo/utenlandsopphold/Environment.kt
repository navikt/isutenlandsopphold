package no.nav.syfo.utenlandsopphold

import no.nav.syfo.utenlandsopphold.infrastructure.kafka.KafkaEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISUTENLANDSOPPHOLD_ISUTENLANDSOPPHOLD_DB"

data class Environment(
    val database: DatabaseEnvironment =
        DatabaseEnvironment(
            host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
            port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
            name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
            username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
            password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
            jdbcUrl = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_JDBC_URL"),
        ),
    val kafka: KafkaEnvironment =
        KafkaEnvironment(
            aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
            aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
            aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
            aivenSecurityProtocol = "SSL",
            aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
            aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
            aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
            aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
        ),
    val azure: AzureEnvironment =
        AzureEnvironment(
            appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
            appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
        ),
)

data class AzureEnvironment(
    val appClientId: String,
    val appWellKnownUrl: String,
)

data class DatabaseEnvironment(
    val host: String,
    val port: String,
    val name: String,
    val username: String,
    val password: String,
    val jdbcUrl: String,
)

fun getEnvVar(
    varName: String,
    defaultValue: String? = null,
) = System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal() = getEnvVar("KTOR_ENV", "local") == "local"

val isKafkaSoknadConsumerEnabled: Boolean = getEnvVar("KAFKA_SOKNAD_CONSUMER_ENABLED", "false").toBoolean()
