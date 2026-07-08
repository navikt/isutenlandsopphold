package no.nav.syfo.utenlandsopphold.testutil

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import no.nav.syfo.common.auth.WellKnown
import no.nav.syfo.common.util.JWT_CLAIM_NAVIDENT
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

const val KEY_ID = "localhost-signer"
const val TEST_AZURE_APP_CLIENT_ID = "isutenlandsopphold-client-id"
const val TEST_ISSUER = "https://sts.issuer.net/veileder/v2"

fun wellKnownInternalAzureAD(): WellKnown {
    val uri = Paths.get("src/test/resources/jwkset.json").toUri().toURL()
    return WellKnown(
        issuer = TEST_ISSUER,
        jwksUri = uri.toString(),
    )
}

// Mock av JWT-token levert av Azure AD. KeyId må matche kid i jwkset.json
fun generateJWT(
    audience: String = TEST_AZURE_APP_CLIENT_ID,
    issuer: String = TEST_ISSUER,
    navIdent: String? = "Z999999",
    subject: String? = null,
    expiry: LocalDateTime? = LocalDateTime.now().plusHours(1),
): String {
    val now = Date()
    val key = getDefaultRSAKey()
    val alg = Algorithm.RSA256(key.toRSAPublicKey(), key.toRSAPrivateKey())

    return JWT
        .create()
        .withKeyId(KEY_ID)
        .withSubject(subject ?: "subject")
        .withIssuer(issuer)
        .withAudience(audience)
        .withJWTId(UUID.randomUUID().toString())
        .withClaim("ver", "1.0")
        .withClaim("nonce", "myNonce")
        .withClaim("auth_time", now)
        .withClaim("nbf", now)
        .withClaim("iat", now)
        .withClaim("exp", Date.from(expiry?.atZone(ZoneId.systemDefault())?.toInstant()))
        .withClaim(JWT_CLAIM_NAVIDENT, navIdent)
        .sign(alg)
}

private fun getDefaultRSAKey(): RSAKey = getJWKSet().getKeyByKeyId(KEY_ID) as RSAKey

private fun getJWKSet(): JWKSet {
    val jwkSet = String(Files.readAllBytes(Paths.get("src/test/resources/jwkset.json")), StandardCharsets.UTF_8)
    return JWKSet.parse(jwkSet)
}
