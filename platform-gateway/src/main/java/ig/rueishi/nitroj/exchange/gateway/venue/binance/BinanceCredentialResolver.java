package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.credentials.CredentialResolver;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves Binance FIX credentials and rejects non-Ed25519 primitives.
 */
public final class BinanceCredentialResolver implements CredentialResolver {
    public static final String PRIMITIVE_TAG = "ED25519";
    public static final String ENV_API_KEY = "BINANCE_API_KEY";
    public static final String ENV_PRIVATE_KEY_BASE64 = "BINANCE_ED25519_PRIVATE_KEY_BASE64";
    public static final String ENV_PRIMITIVE = "BINANCE_CREDENTIAL_PRIMITIVE";

    @Override
    public ResolvedCredential resolve(final CredentialsConfig configured, final Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        final CredentialsConfig resolved = hasCredentialValues(configured)
            ? configured
            : new CredentialsConfig(
                configured == null ? null : configured.vaultPath(),
                requiredEnv(environment, ENV_API_KEY),
                requiredEnv(environment, ENV_PRIVATE_KEY_BASE64),
                environment.getOrDefault(ENV_PRIMITIVE, PRIMITIVE_TAG));

        validatePrimitive(resolved);
        decodePrivateKey(resolved.secretBase64());
        return new ResolvedCredential(SigningPrimitive.ED25519, resolved);
    }

    public static void validatePrimitive(final CredentialsConfig credentials) {
        Objects.requireNonNull(credentials, "credentials");
        if (!hasText(credentials.apiKey())) {
            throw new IllegalStateException("Missing Binance FIX API key");
        }
        if (!hasText(credentials.secretBase64())) {
            throw new IllegalStateException("Missing Binance Ed25519 private key");
        }
        if (hasText(credentials.passphrase()) && !PRIMITIVE_TAG.equals(credentials.passphrase())) {
            throw new IllegalStateException("Binance credential primitive must be " + PRIMITIVE_TAG);
        }
    }

    static PrivateKey decodePrivateKey(final String privateKeyBase64) {
        try {
            final byte[] encoded = Base64.getDecoder().decode(Objects.requireNonNull(privateKeyBase64, "privateKeyBase64"));
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (final IllegalArgumentException ex) {
            throw new IllegalStateException("Binance Ed25519 private key is not valid Base64", ex);
        } catch (final Exception ex) {
            throw new IllegalStateException("Binance Ed25519 private key must be PKCS#8 Ed25519", ex);
        }
    }

    private static boolean hasCredentialValues(final CredentialsConfig credentials) {
        return credentials != null
            && hasText(credentials.apiKey())
            && hasText(credentials.secretBase64());
    }

    private static String requiredEnv(final Map<String, String> environment, final String name) {
        final String value = environment.get(name);
        if (!hasText(value)) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }
}
