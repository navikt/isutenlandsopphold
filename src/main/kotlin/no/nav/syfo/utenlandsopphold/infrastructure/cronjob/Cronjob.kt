package no.nav.syfo.utenlandsopphold.infrastructure.cronjob

/**
 * Kontrakt for periodiske bakgrunnsjobber som kjøres av [CronjobRunner]. Hver [run]-kjøring
 * returnerer ett [Result] per delresultat (f.eks. per søknad/behandlingsutfall den forsøkte å behandle),
 * slik at runneren kan logge antall vellykkede og feilede uten at én feil stopper resten.
 */
interface Cronjob {
    suspend fun run(): List<Result<Any>>

    val initialDelayMinutes: Long
    val intervalDelayMinutes: Long
}
