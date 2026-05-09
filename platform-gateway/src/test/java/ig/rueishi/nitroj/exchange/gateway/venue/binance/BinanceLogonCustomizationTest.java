package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.LogonEncoder;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BinanceLogonCustomizationTest {
    @Test
    void buildPayload_usesBinanceSohSeparatedOrder() {
        assertThat(BinanceLogonCustomization.buildPayload("A", "sender", "SPOT", 7, "20260508-12:00:00.000"))
            .isEqualTo("A\001sender\001SPOT\0017\00120260508-12:00:00.000");
    }

    @Test
    void configureLogon_setsEd25519FieldsAndVerifiableSignature() throws Exception {
        final var keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final CredentialsConfig credentials = new CredentialsConfig(
            "vault",
            "api-key",
            Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
            BinanceCredentialResolver.PRIMITIVE_TAG);
        final LogonEncoder logon = new LogonEncoder();
        logon.header().senderCompID("sender");
        logon.header().targetCompID("SPOT");
        logon.header().msgSeqNum(7);
        final byte[] sendingTime = "20260508-12:00:00.000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        logon.header().sendingTime(sendingTime);

        new BinanceLogonCustomization(credentials, 2).configureLogon(logon, 99L);

        assertThat(logon.encryptMethod()).isZero();
        assertThat(logon.heartBtInt()).isEqualTo(30);
        assertThat(logon.resetSeqNumFlag()).isTrue();
        assertThat(logon.usernameAsString()).isEqualTo("api-key");
        assertThat(logon.rawDataLength()).isEqualTo(logon.rawData().length);
        assertThat(logon.messageHandling()).isEqualTo(2);

        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519")
            .generatePublic(new X509EncodedKeySpec(keyPair.getPublic().getEncoded())));
        verifier.update(BinanceLogonCustomization.buildPayload(
            "A", "sender", "SPOT", 7, "20260508-12:00:00.000").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getDecoder().decode(logon.rawData()))).isTrue();
    }

    @Test
    void constructor_rejectsNonEd25519Primitive() throws Exception {
        final CredentialsConfig credentials = new CredentialsConfig(
            "vault", "api-key", BinanceCredentialResolverTest.privateKeyBase64(), "HMAC_SHA256");

        assertThatThrownBy(() -> new BinanceLogonCustomization(credentials))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ED25519");
    }
}
