package no.nav.syfo.utenlandsopphold

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
