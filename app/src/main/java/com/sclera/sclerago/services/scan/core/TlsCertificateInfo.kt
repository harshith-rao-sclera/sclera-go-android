//
//  TlsCertificateInfo.kt
//  AssetOnBoard
//
//  Certificate details pulled from a device's TLS handshake.
//
//  The iOS build has to walk the raw DER by hand, because Security.framework
//  exposes only the subject summary and serial. The JVM's X509Certificate
//  already parses the issuer, the validity window and the SANs, so this file is
//  mostly a matter of pulling RDN attributes out of an X.500 principal.
//
//  These fields identify hardware unusually well — self-signed certs on NAS
//  boxes, routers and printers routinely carry the model in the CN.
//

package com.sclera.sclerago.services.scan.core

import kotlinx.serialization.Serializable
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

@Serializable
data class TlsCertificateInfo(
    val subjectCommonName: String? = null,
    val subjectOrganization: String? = null,
    val subjectSummary: String? = null,
    val issuerCommonName: String? = null,
    val issuerOrganization: String? = null,
    val serialNumber: String? = null,
    /** Epoch milliseconds, so the report stays a plain JSON number. */
    val notBefore: Long? = null,
    val notAfter: Long? = null,
    val subjectAlternativeNames: List<String> = emptyList(),
    val isSelfSigned: Boolean = false,
) {
    val isExpired: Boolean
        get() = notAfter?.let { it < System.currentTimeMillis() } ?: false

    /** The most human-meaningful label available for this certificate. */
    val displayName: String?
        get() = subjectCommonName
            ?: subjectSummary
            ?: subjectOrganization
            ?: subjectAlternativeNames.firstOrNull()

    companion object {

        /** Reads the peer chain out of a completed handshake. */
        fun from(session: SSLSession): List<TlsCertificateInfo> {
            val chain = try {
                session.peerCertificates
            } catch (_: Exception) {
                return emptyList()
            }
            return chain.filterIsInstance<X509Certificate>().mapNotNull { of(it) }
        }

        fun of(certificate: X509Certificate): TlsCertificateInfo? {
            val subject = rdn(certificate.subjectX500Principal.name)
            val issuer = rdn(certificate.issuerX500Principal.name)
            val alternativeNames = alternativeNames(certificate)

            val subjectCommonName = subject["CN"]
            // Stands in for SecCertificateCopySubjectSummary: the most specific
            // attribute the subject offers, never the raw distinguished name —
            // "CN=diskstation,O=Synology" is not a display name.
            val subjectSummary = subject["O"] ?: subject["OU"] ?: subject.values.firstOrNull()

            if (subjectCommonName == null && subjectSummary == null && alternativeNames.isEmpty()) {
                return null
            }

            return TlsCertificateInfo(
                subjectCommonName = subjectCommonName,
                subjectOrganization = subject["O"],
                subjectSummary = subjectSummary,
                issuerCommonName = issuer["CN"],
                issuerOrganization = issuer["O"],
                serialNumber = certificate.serialNumber?.toString(16),
                notBefore = certificate.notBefore?.time,
                notAfter = certificate.notAfter?.time,
                subjectAlternativeNames = alternativeNames,
                isSelfSigned = subject.isNotEmpty() && subject == issuer,
            )
        }

        /**
         * Splits an RFC 2253 distinguished name into its attributes.
         *
         * Hand-rolled rather than routed through LdapName: appliance certificates
         * are frequently malformed enough to make LdapName throw, and losing the
         * whole certificate over a stray comma would defeat the purpose.
         */
        private fun rdn(distinguishedName: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            var index = 0
            val builder = StringBuilder()
            val parts = mutableListOf<String>()
            var escaped = false
            var quoted = false

            while (index < distinguishedName.length) {
                val character = distinguishedName[index]
                when {
                    escaped -> {
                        builder.append(character)
                        escaped = false
                    }
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = !quoted
                    character == ',' && !quoted -> {
                        parts += builder.toString()
                        builder.setLength(0)
                    }
                    else -> builder.append(character)
                }
                index += 1
            }
            parts += builder.toString()

            for (part in parts) {
                val separator = part.indexOf('=')
                if (separator <= 0) continue
                val key = part.substring(0, separator).trim().uppercase()
                val value = part.substring(separator + 1).trim()
                if (value.isEmpty()) continue
                // Keep the first occurrence; certificates may repeat an attribute.
                if (!result.containsKey(key)) result[key] = value
            }
            return result
        }

        /** dNSName (type 2) and iPAddress (type 7) entries, in that order. */
        private fun alternativeNames(certificate: X509Certificate): List<String> {
            val entries = try {
                certificate.subjectAlternativeNames
            } catch (_: CertificateParsingException) {
                null
            } catch (_: Exception) {
                null
            } ?: return emptyList()

            return entries.mapNotNull { entry ->
                val type = entry.getOrNull(0) as? Int ?: return@mapNotNull null
                val value = entry.getOrNull(1) as? String ?: return@mapNotNull null
                if (type == 2 || type == 7) value else null
            }
        }
    }
}
