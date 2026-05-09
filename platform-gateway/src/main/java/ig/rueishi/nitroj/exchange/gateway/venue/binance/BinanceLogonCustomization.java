package ig.rueishi.nitroj.exchange.gateway.venue.binance;

import ig.rueishi.nitroj.exchange.common.CredentialsConfig;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.HeaderEncoder;
import ig.rueishi.nitroj.exchange.fix.fix44.builder.LogonEncoder;
import uk.co.real_logic.artio.builder.AbstractLogonEncoder;
import uk.co.real_logic.artio.builder.AbstractLogoutEncoder;
import uk.co.real_logic.artio.session.SessionCustomisationStrategy;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Objects;

/**
 * Binance FIX 4.4 Ed25519 Logon customisation.
 */
public final class BinanceLogonCustomization implements SessionCustomisationStrategy {
    static final int ENCRYPT_METHOD_NONE = 0;
    static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    static final int MESSAGE_HANDLING_SEQUENTIAL = 2;

    private final String apiKey;
    private final PrivateKey privateKey;
    private final int messageHandling;

    public BinanceLogonCustomization(final CredentialsConfig credentials) {
        this(credentials, MESSAGE_HANDLING_SEQUENTIAL);
    }

    public BinanceLogonCustomization(final CredentialsConfig credentials, final int messageHandling) {
        BinanceCredentialResolver.validatePrimitive(credentials);
        this.apiKey = Objects.requireNonNull(credentials.apiKey(), "apiKey");
        this.privateKey = BinanceCredentialResolver.decodePrivateKey(credentials.secretBase64());
        if (messageHandling <= 0) {
            throw new IllegalArgumentException("messageHandling must be positive");
        }
        this.messageHandling = messageHandling;
    }

    @Override
    public void configureLogon(final AbstractLogonEncoder abstractLogon, final long sessionId) {
        if (!(abstractLogon instanceof LogonEncoder logon)) {
            throw new IllegalArgumentException("Binance logon requires generated FIX 4.4 LogonEncoder");
        }
        final HeaderEncoder header = logon.header();
        final String payload = buildPayload(
            header.msgTypeAsString(),
            header.senderCompIDAsString(),
            header.targetCompIDAsString(),
            header.msgSeqNum(),
            header.sendingTimeAsString());
        final byte[] signature = sign(privateKey, payload).getBytes(StandardCharsets.US_ASCII);

        logon.encryptMethod(ENCRYPT_METHOD_NONE);
        logon.heartBtInt(HEARTBEAT_INTERVAL_SECONDS);
        logon.resetSeqNumFlag(true);
        logon.username(apiKey);
        logon.rawDataLength(signature.length);
        logon.rawData(signature);
        logon.messageHandling(messageHandling);
    }

    @Override
    public void configureLogout(final AbstractLogoutEncoder logout, final long sessionId) {
        // Binance FIX requires no proprietary logout authentication fields.
    }

    static String buildPayload(
        final String msgType,
        final String senderCompId,
        final String targetCompId,
        final int msgSeqNum,
        final String sendingTime) {

        return msgType + '\001'
            + senderCompId + '\001'
            + targetCompId + '\001'
            + msgSeqNum + '\001'
            + sendingTime;
    }

    static String sign(final PrivateKey privateKey, final String payload) {
        try {
            final Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (final Exception ex) {
            throw new IllegalStateException("Binance Ed25519 logon signature failed", ex);
        }
    }
}
