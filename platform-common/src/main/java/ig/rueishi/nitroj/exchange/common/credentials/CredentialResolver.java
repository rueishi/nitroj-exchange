package ig.rueishi.nitroj.exchange.common.credentials;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;

import java.util.Map;

/**
 * Startup-only credential resolution contract.
 *
 * <p>Responsibility: resolves repository-safe credential pointers into typed
 * credentials with an explicit signing primitive. Role in system: venue plugins
 * use this boundary before creating logon customizers so HMAC and Ed25519
 * credential shapes cannot be accidentally interchanged. Relationships:
 * Coinbase still consumes {@link CredentialsConfig} directly, while V14 Binance
 * uses the primitive tag to reject mismatched credentials at startup. Lifecycle:
 * called on cold startup/reconnect setup, never on trading hot paths.</p>
 */
public interface CredentialResolver {
    enum SigningPrimitive {
        HMAC_SHA256,
        ED25519
    }

    /**
     * Resolves credentials from a config record and process environment.
     *
     * @param configured repository-safe config record
     * @param environment process environment snapshot
     * @return resolved credential bundle with primitive metadata
     */
    ResolvedCredential resolve(CredentialsConfig configured, Map<String, String> environment);

    record ResolvedCredential(
        SigningPrimitive primitive,
        CredentialsConfig credentials
    ) {
        public ResolvedCredential {
            if (primitive == null) {
                throw new NullPointerException("primitive");
            }
            if (credentials == null) {
                throw new NullPointerException("credentials");
            }
        }
    }
}
