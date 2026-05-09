package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.common.credentials.CredentialResolver.SigningPrimitive;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BinanceCredentialResolverTest {
    @Test
    void resolve_environmentCredentials_returnsEd25519Credentials() throws Exception {
        final String privateKey = privateKeyBase64();

        final var resolved = new BinanceCredentialResolver().resolve(
            new CredentialsConfig("secret/trading/binance/venue-2", null, null, null),
            Map.of(
                BinanceCredentialResolver.ENV_API_KEY, "api-key",
                BinanceCredentialResolver.ENV_PRIVATE_KEY_BASE64, privateKey));

        assertThat(resolved.primitive()).isEqualTo(SigningPrimitive.ED25519);
        assertThat(resolved.credentials().vaultPath()).isEqualTo("secret/trading/binance/venue-2");
        assertThat(resolved.credentials().apiKey()).isEqualTo("api-key");
        assertThat(resolved.credentials().secretBase64()).isEqualTo(privateKey);
        assertThat(resolved.credentials().passphrase()).isEqualTo(BinanceCredentialResolver.PRIMITIVE_TAG);
    }

    @Test
    void resolve_hmacPrimitiveMismatch_rejected() throws Exception {
        final CredentialsConfig credentials = new CredentialsConfig(
            "vault", "api-key", privateKeyBase64(), "HMAC_SHA256");

        assertThatThrownBy(() -> new BinanceCredentialResolver().resolve(credentials, Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ED25519");
    }

    @Test
    void resolve_malformedPrivateKey_rejected() {
        final CredentialsConfig credentials = new CredentialsConfig(
            "vault", "api-key", "not-base64", BinanceCredentialResolver.PRIMITIVE_TAG);

        assertThatThrownBy(() -> new BinanceCredentialResolver().resolve(credentials, Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private key");
    }

    @Test
    void resolve_missingEnvironmentValue_rejected() {
        assertThatThrownBy(() -> new BinanceCredentialResolver().resolve(
            new CredentialsConfig("vault", null, null, null),
            Map.of(BinanceCredentialResolver.ENV_API_KEY, "api-key")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(BinanceCredentialResolver.ENV_PRIVATE_KEY_BASE64);
    }

    static String privateKeyBase64() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    }
}
