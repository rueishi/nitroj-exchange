package ig.rueishi.nitroj.exchange.simulator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Embeddable Binance FIX simulator used by automated tests.
 *
 * <p>Responsibility: provides a deterministic test double for Binance FIX
 * order flow and market data. Role in system: TASK-406 tests can call the
 * simulator directly, while V14 live-wire tests can connect to its local TCP FIX
 * acceptor and exercise session-level logon, market-data subscription,
 * order-entry, cancel, logout, and disconnect behavior without Binance network
 * access. Relationships: {@link SimulatorConfig} supplies the reusable simulator
 * envelope, {@link SimulatorOrderBook} tracks pending orders, and
 * {@link BinanceSimulatorScenarios} applies fill/reject/disconnect behavior.
 * Lifecycle: build, start, run scripted actions, assert, reset, stop. Design
 * intent: keep this fixture deterministic and local while preserving the Binance
 * Ed25519 logon contract used by the production gateway.
 */
public final class BinanceExchangeSimulator implements AutoCloseable {
    private static final char SOH = '\001';
    private static final String BEGIN_STRING = "FIX.4.4";

    /**
     * Supported execution scenarios for submitted orders.
     */
    public enum FillMode {
        IMMEDIATE,
        PARTIAL_THEN_FULL,
        REJECT_ALL,
        NO_FILL,
        DELAYED_FILL,
        DISCONNECT_ON_FILL
    }

    private final SimulatorConfig config;
    private final String apiKey;
    private final PublicKey publicKey;
    private final LongSupplier clock;
    private volatile FillMode fillMode;
    private final SimulatorOrderBook orderBook = new SimulatorOrderBook();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final BinanceSimulatorScenarios scenarios = new BinanceSimulatorScenarios(this, scheduler);

    private final CopyOnWriteArrayList<ReceivedOrder> receivedOrders = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ReceivedCancel> receivedCancels = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ExecutionReport> executionReports = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<L3OrderEvent> l3Events = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<L2PriceLevelEvent> l2Events = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> l3FixMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> l2FixMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> inboundFixMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> outboundFixMessages = new CopyOnWriteArrayList<>();
    private final AtomicInteger fillCount = new AtomicInteger();
    private final AtomicInteger rejectCount = new AtomicInteger();
    private final AtomicInteger logonCount = new AtomicInteger();
    private final AtomicInteger wireLogonCount = new AtomicInteger();
    private final AtomicInteger marketDataCount = new AtomicInteger();
    private final AtomicInteger l3SequenceGapCount = new AtomicInteger();
    private final AtomicInteger malformedL3Count = new AtomicInteger();
    private final AtomicInteger rejectedWireSessionCount = new AtomicInteger();
    private final AtomicInteger malformedInboundFixCount = new AtomicInteger();

    private volatile MarketDataTick lastMarketDataTick;
    private volatile boolean connected;
    private volatile int nextL3SeqNum = 1;
    private volatile int nextL2SeqNum = 1;
    private volatile boolean running;
    private volatile Socket activeSocket;
    private volatile ScheduledFuture<?> marketDataTask;
    private ServerSocket acceptorSocket;
    private Thread acceptorThread;

    private BinanceExchangeSimulator(final Builder builder) {
        SimulatorConfig configFromBuilder = builder.config.toBuilder()
            .fillMode(toCoinbaseFillMode(builder.fillMode))
            .instrument(builder.instrumentSymbol, builder.initialBid, builder.initialAsk)
            .build();
        this.config = configFromBuilder;
        this.apiKey = builder.apiKey;
        this.publicKey = decodePublicKey(builder.publicKeyBase64);
        this.clock = builder.clock;
        this.fillMode = builder.fillMode;
    }

    /**
     * Starts the simulator and opens its local TCP FIX acceptor endpoint.
     *
     * <p>Earlier simulator tests relied on start-up recording an in-process logon;
     * that compatibility behavior remains. In addition, TASK-125 starts a local
     * socket acceptor that reads SOH-delimited FIX messages and routes them
     * through the same session handler used by direct tests. This is intentionally
     * a small deterministic FIX fixture, not a full exchange matching engine.
     *
     * @throws IOException if the configured port cannot be bound
     */
    public void start() throws IOException {
        try {
            acceptorSocket = new ServerSocket(config.port());
        } catch (IOException ex) {
            throw new IOException("Simulator port in use or unavailable: " + config.port(), ex);
        }
        running = true;
        acceptorThread = Thread.ofPlatform().name("binance-fix-simulator-" + config.port()).start(this::acceptLoop);
        connected = true;
        recordLogon();
        marketDataTask = scheduler.scheduleAtFixedRate(
            this::recordMarketDataTick,
            0,
            config.marketDataIntervalMs(),
            TimeUnit.MILLISECONDS);
    }

    /**
     * Stops publication, releases the reserved port, and shuts down scheduler work.
     */
    public void stop() {
        running = false;
        connected = false;
        if (marketDataTask != null) {
            marketDataTask.cancel(true);
        }
        closeQuietly(activeSocket);
        if (acceptorSocket != null) {
            try {
                acceptorSocket.close();
            } catch (IOException ignored) {
                // Test cleanup must be best-effort; port release failures are not actionable here.
            }
        }
        if (acceptorThread != null) {
            try {
                acceptorThread.join(500L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        scheduler.shutdownNow();
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Updates the current synthetic market for a symbol.
     *
     * @param symbol FIX symbol
     * @param bid new bid
     * @param ask new ask
     */
    public void setMarket(final String symbol, final double bid, final double ask) {
        lastMarketDataTick = new MarketDataTick(symbol, bid, ask, System.currentTimeMillis());
    }

    public void setMarket(final double bid, final double ask) {
        setMarket(primaryInstrument().symbol(), bid, ask);
    }

    public void setFillMode(final FillMode mode) {
        fillMode = mode;
    }

    public FillMode fillMode() {
        return fillMode;
    }

    public SimulatorConfig config() {
        return config;
    }

    /**
     * Schedules a disconnect with cancel-on-logout disabled.
     *
     * @param delayMs delay before disconnect
     */
    public void scheduleDisconnect(final long delayMs) {
        scenarios.scheduleDisconnect(delayMs);
    }

    /**
     * Schedules a disconnect and optionally clears live orders.
     *
     * @param delayMs delay before disconnect
     * @param cancelAllOnLogout whether pending orders are cleared
     */
    public void scheduleDisconnect(final long delayMs, final boolean cancelAllOnLogout) {
        scenarios.scheduleDisconnect(delayMs, cancelAllOnLogout);
    }

    /**
     * Records an L2 or L3 market-data subscription request.
     *
     * <p>Unsupported subscription models fail immediately so E2E fixtures can
     * prove negative gateway behavior without relying on live venue rejects.</p>
     *
     * @param marketDataModel requested model, currently {@code L2} or {@code L3}
     */
    public void subscribeMarketData(final String marketDataModel) {
        acceptMarketDataSubscribe(marketDataModel);
    }

    /**
     * Submits a simulated NewOrderSingle into the simulator.
     *
     * @param side BUY or SELL
     * @param price limit price
     * @param qty order quantity
     * @return generated client order ID
     */
    public String submitNewOrder(final String side, final double price, final double qty) {
        return submitNewOrder("CL-" + System.nanoTime(), side, primaryInstrument().symbol(), price, qty);
    }

    public String submitNewOrder(
        final String clOrdId,
        final String side,
        final String symbol,
        final double price,
        final double qty
    ) {
        acceptOrder(clOrdId, side, symbol, price, qty);
        return clOrdId;
    }

    public void submitCancel(final String cancelClOrdId, final String origClOrdId) {
        acceptCancel(cancelClOrdId, origClOrdId);
    }

    /**
     * Injects a manual fill for a pending order.
     *
     * @param clOrdId client order ID
     * @param fillPrice fill price
     * @param fillQty fill quantity
     */
    public void injectFill(final String clOrdId, final double fillPrice, final double fillQty) {
        SimulatorOrderBook.SimOrder order = orderBook.get(clOrdId);
        if (order != null) {
            recordFill(order, fillPrice, fillQty, true);
        }
    }

    /**
     * Awaits and asserts that a matching order was received.
     *
     * @param side expected side
     * @param price expected limit price
     * @param qty expected quantity
     */
    public void assertOrderReceived(final String side, final double price, final double qty) {
        assertEventually(() -> receivedOrders.stream().anyMatch(order ->
            order.side().equals(side)
                && closeTo(order.limitPrice(), price)
                && closeTo(order.qty(), qty)
        ), "Expected order not received: side=" + side + " price=" + price + " qty=" + qty
            + "\nActual orders: " + receivedOrders);
    }

    public void assertOrderReceived(final char side, final double price, final double qty) {
        assertOrderReceived(side == '1' ? "BUY" : "SELL", price, qty);
    }

    public void assertCancelReceived(final String origClOrdId) {
        assertEventually(() -> receivedCancels.stream().anyMatch(cancel -> cancel.origClOrdId().equals(origClOrdId)),
            "Expected cancel for: " + origClOrdId);
    }

    public void assertOrderCount(final int expected) {
        assertEventually(() -> receivedOrders.size() == expected,
            "Expected " + expected + " orders, got " + receivedOrders.size());
    }

    public void assertNoOrdersReceived() {
        assertOrderCount(0);
    }

    public int getFillCount() {
        return fillCount.get();
    }

    public int getRejectCount() {
        return rejectCount.get();
    }

    public int getLogonCount() {
        return logonCount.get();
    }

    public int getMarketDataCount() {
        return marketDataCount.get();
    }

    public List<ReceivedOrder> receivedOrders() {
        return List.copyOf(receivedOrders);
    }

    public List<ReceivedCancel> receivedCancels() {
        return List.copyOf(receivedCancels);
    }

    public List<ExecutionReport> executionReports() {
        return List.copyOf(executionReports);
    }

    public List<L3OrderEvent> l3Events() {
        return List.copyOf(l3Events);
    }

    public List<L2PriceLevelEvent> l2Events() {
        return List.copyOf(l2Events);
    }

    public List<String> l3FixMessages() {
        return List.copyOf(l3FixMessages);
    }

    public List<String> l2FixMessages() {
        return List.copyOf(l2FixMessages);
    }

    public List<String> inboundFixMessages() {
        return List.copyOf(inboundFixMessages);
    }

    public List<String> outboundFixMessages() {
        return List.copyOf(outboundFixMessages);
    }

    public int getWireLogonCount() {
        return wireLogonCount.get();
    }

    public int getRejectedWireSessionCount() {
        return rejectedWireSessionCount.get();
    }

    public int getMalformedInboundFixCount() {
        return malformedInboundFixCount.get();
    }

    public int getL3SequenceGapCount() {
        return l3SequenceGapCount.get();
    }

    public int getMalformedL3Count() {
        return malformedL3Count.get();
    }

    public MarketDataTick lastMarketDataTick() {
        return lastMarketDataTick;
    }

    public boolean isConnected() {
        return connected;
    }

    public int pendingOrderCount() {
        return orderBook.pendingOrderCount();
    }

    /**
     * Clears received messages, counters, pending order state, and market-data sequences.
     *
     * <p>Replay and live-wire tests depend on this being a full deterministic
     * boundary: after reset, emitting the same scripted L2/L3 snapshots and order
     * scenarios produces the same event ordering, sequence numbers, counters, and
     * pending-order state as a fresh simulator instance.</p>
     */
    public void reset() {
        receivedOrders.clear();
        receivedCancels.clear();
        executionReports.clear();
        l3Events.clear();
        l2Events.clear();
        l3FixMessages.clear();
        l2FixMessages.clear();
        inboundFixMessages.clear();
        outboundFixMessages.clear();
        orderBook.reset();
        fillCount.set(0);
        rejectCount.set(0);
        marketDataCount.set(0);
        l3SequenceGapCount.set(0);
        malformedL3Count.set(0);
        wireLogonCount.set(0);
        rejectedWireSessionCount.set(0);
        malformedInboundFixCount.set(0);
        nextL3SeqNum = 1;
        nextL2SeqNum = 1;
    }

    /**
     * Emits a deterministic Binance-style L2 snapshot for the primary instrument.
     *
     * @return first generated price-level event
     */
    public L2PriceLevelEvent emitL2Snapshot() {
        final SimulatorConfig.Instrument instrument = primaryInstrument();
        final L2PriceLevelEvent bid = emitL2Level("BUY", instrument.symbol(), instrument.bid(), 1.0, "SNAPSHOT");
        emitL2Level("SELL", instrument.symbol(), instrument.ask(), 1.0, "SNAPSHOT");
        return bid;
    }

    public L2PriceLevelEvent emitL2Update(
        final String side,
        final String symbol,
        final double price,
        final double size,
        final String action) {
        return emitL2Level(side, symbol, price, size, action);
    }

    /**
     * Emits a deterministic Binance-style L3 snapshot for the primary instrument.
     *
     * @return generated event
     */
    public L3OrderEvent emitL3Snapshot() {
        SimulatorConfig.Instrument instrument = primaryInstrument();
        L3OrderEvent bid = emitL3Order("SNAP-BID-1", "BUY", instrument.symbol(), instrument.bid(), 1.0, "SNAPSHOT");
        emitL3Order("SNAP-ASK-1", "SELL", instrument.symbol(), instrument.ask(), 1.0, "SNAPSHOT");
        return bid;
    }

    public L3OrderEvent emitL3AddOrder(
        final String orderId,
        final String side,
        final String symbol,
        final double price,
        final double size) {
        return emitL3Order(orderId, side, symbol, price, size, "ADD");
    }

    public L3OrderEvent emitL3ChangeOrder(
        final String orderId,
        final String side,
        final String symbol,
        final double price,
        final double size) {
        return emitL3Order(orderId, side, symbol, price, size, "CHANGE");
    }

    public L3OrderEvent emitL3DeleteOrder(
        final String orderId,
        final String side,
        final String symbol,
        final double price) {
        return emitL3Order(orderId, side, symbol, price, 0.0, "DELETE");
    }

    public String emitMalformedL3Message(final String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("malformed L3 reason is required");
        }
        malformedL3Count.incrementAndGet();
        String message = "8=FIXT.1.1\u00019=0\u000135=X\u000158=" + reason + "\u000110=000\u0001";
        l3FixMessages.add(message);
        return message;
    }

    public String emitMalformedL2Message(final String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("malformed L2 reason is required");
        }
        final String message = "8=FIXT.1.1\u00019=0\u000135=X\u000158=" + reason + "\u000110=000\u0001";
        l2FixMessages.add(message);
        return message;
    }

    public void emitL3SequenceGap(final int skippedMessages) {
        if (skippedMessages <= 0) {
            throw new IllegalArgumentException("skippedMessages must be positive");
        }
        nextL3SeqNum += skippedMessages;
        l3SequenceGapCount.incrementAndGet();
    }

    public void reconnect() {
        connected = true;
        recordLogon();
    }

    void acceptOrder(
        final String clOrdId,
        final String side,
        final String symbol,
        final double limitPrice,
        final double qty
    ) {
        SimulatorOrderBook.SimOrder order = new SimulatorOrderBook.SimOrder(
            clOrdId, "SIM-" + clOrdId, symbol, side, limitPrice, qty);
        receivedOrders.add(new ReceivedOrder(clOrdId, side, symbol, limitPrice, qty, clock.getAsLong()));
        orderBook.add(order);
        executionReports.add(ExecutionReport.ack(order));
        scenarios.applyNewOrderScenario(order);
    }

    void acceptCancel(final String cancelClOrdId, final String origClOrdId) {
        receivedCancels.add(new ReceivedCancel(cancelClOrdId, origClOrdId));
        SimulatorOrderBook.SimOrder order = orderBook.remove(origClOrdId);
        if (order != null) {
            executionReports.add(ExecutionReport.cancel(order, cancelClOrdId));
        } else {
            executionReports.add(ExecutionReport.cancelReject(cancelClOrdId, origClOrdId));
        }
    }

    void recordFill(
        final SimulatorOrderBook.SimOrder order,
        final double fillPrice,
        final double fillQty,
        final boolean finalFill
    ) {
        fillCount.incrementAndGet();
        executionReports.add(ExecutionReport.fill(order, fillPrice, fillQty, finalFill));
        if (finalFill) {
            orderBook.remove(order.clOrdId());
        }
    }

    void recordReject(final SimulatorOrderBook.SimOrder order, final int rejectReason, final String text) {
        rejectCount.incrementAndGet();
        executionReports.add(ExecutionReport.reject(order, rejectReason, text));
        orderBook.remove(order.clOrdId());
    }

    void recordLogon() {
        logonCount.incrementAndGet();
    }

    void recordMarketDataTick() {
        SimulatorConfig.Instrument instrument = primaryInstrument();
        lastMarketDataTick = new MarketDataTick(instrument.symbol(), instrument.bid(), instrument.ask(),
            clock.getAsLong());
        marketDataCount.incrementAndGet();
    }

    void markDisconnected(final boolean cancelAllOnLogout) {
        connected = false;
        closeQuietly(activeSocket);
        if (cancelAllOnLogout) {
            orderBook.reset();
        }
    }

    void acceptMarketDataSubscribe(final String marketDataModel) {
        if (!"L2".equals(marketDataModel)) {
            throw new IllegalArgumentException("Unsupported simulator market-data model: " + marketDataModel);
        }
    }

    private L2PriceLevelEvent emitL2Level(
        final String side,
        final String symbol,
        final double price,
        final double size,
        final String action) {

        validateL2Level(side, symbol, price, size, action);
        final L2PriceLevelEvent event = new L2PriceLevelEvent(
            nextL2SeqNum++, side, symbol, price, size, action, clock.getAsLong());
        l2Events.add(event);
        l2FixMessages.add(toFixMarketByPrice(event));
        return event;
    }

    private void validateL2Level(
        final String side,
        final String symbol,
        final double price,
        final double size,
        final String action) {

        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new IllegalArgumentException("Unsupported L2 side: " + side);
        }
        if (!config.instruments().containsKey(symbol)) {
            throw new IllegalArgumentException("Unknown L2 symbol: " + symbol);
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("L2 price must be positive and finite");
        }
        if (!Double.isFinite(size) || size < 0.0) {
            throw new IllegalArgumentException("L2 size must be non-negative and finite");
        }
        if (!List.of("SNAPSHOT", "ADD", "CHANGE", "DELETE").contains(action)) {
            throw new IllegalArgumentException("Unsupported L2 action: " + action);
        }
    }

    private L3OrderEvent emitL3Order(
        final String orderId,
        final String side,
        final String symbol,
        final double price,
        final double size,
        final String action) {

        validateL3Order(orderId, side, symbol, price, size, action);
        L3OrderEvent event = new L3OrderEvent(nextL3SeqNum++, orderId, side, symbol, price, size, action,
            clock.getAsLong());
        l3Events.add(event);
        l3FixMessages.add(toFixMarketByOrder(event));
        return event;
    }

    private void validateL3Order(
        final String orderId,
        final String side,
        final String symbol,
        final double price,
        final double size,
        final String action) {

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("L3 orderId is required");
        }
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new IllegalArgumentException("Unsupported L3 side: " + side);
        }
        if (!config.instruments().containsKey(symbol)) {
            throw new IllegalArgumentException("Unknown L3 symbol: " + symbol);
        }
        if (!Double.isFinite(price) || price <= 0.0) {
            throw new IllegalArgumentException("L3 price must be positive and finite");
        }
        if (!Double.isFinite(size) || size < 0.0) {
            throw new IllegalArgumentException("L3 size must be non-negative and finite");
        }
        if (!List.of("SNAPSHOT", "ADD", "CHANGE", "DELETE").contains(action)) {
            throw new IllegalArgumentException("Unsupported L3 action: " + action);
        }
    }

    private static String toFixMarketByOrder(final L3OrderEvent event) {
        final char mdEntryType = "BUY".equals(event.side()) ? '0' : '1';
        final char updateAction = switch (event.action()) {
            case "CHANGE" -> '1';
            case "DELETE" -> '2';
            default -> '0';
        };
        return "8=FIXT.1.1\u00019=0\u000135=X\u000134=" + event.seqNum()
            + "\u000155=" + event.symbol()
            + "\u0001268=1"
            + "\u0001279=" + updateAction
            + "\u0001269=" + mdEntryType
            + "\u0001278=" + event.orderId()
            + "\u0001270=" + event.price()
            + "\u0001271=" + event.size()
            + "\u000110=000\u0001";
    }

    private static String toFixMarketByPrice(final L2PriceLevelEvent event) {
        final char mdEntryType = "BUY".equals(event.side()) ? '0' : '1';
        final char updateAction = switch (event.action()) {
            case "CHANGE" -> '1';
            case "DELETE" -> '2';
            default -> '0';
        };
        return "8=FIXT.1.1\u00019=0\u000135=X\u000134=" + event.seqNum()
            + "\u000155=" + event.symbol()
            + "\u0001268=1"
            + "\u0001279=" + updateAction
            + "\u0001269=" + mdEntryType
            + "\u0001270=" + event.price()
            + "\u0001271=" + event.size()
            + "\u00011023=1"
            + "\u000110=000\u0001";
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket socket = acceptorSocket.accept();
                if (activeSocket != null && !activeSocket.isClosed()) {
                    rejectedWireSessionCount.incrementAndGet();
                    writeFix(socket.getOutputStream(), "5", Map.of("58", "duplicate session"));
                    closeQuietly(socket);
                    continue;
                }
                activeSocket = socket;
                Thread.ofPlatform()
                    .name("binance-fix-simulator-session-" + config.port())
                    .start(() -> handleWireSession(socket));
            } catch (IOException ex) {
                if (running) {
                    malformedInboundFixCount.incrementAndGet();
                }
            }
        }
    }

    private void handleWireSession(final Socket socket) {
        try (socket) {
            final InputStream input = socket.getInputStream();
            final OutputStream output = socket.getOutputStream();
            final ByteArrayOutputStream pending = new ByteArrayOutputStream(512);
            final byte[] readBuffer = new byte[256];
            int read;
            while (running && (read = input.read(readBuffer)) >= 0) {
                pending.write(readBuffer, 0, read);
                drainFixMessages(pending, output);
            }
        } catch (IOException ex) {
            if (running) {
                malformedInboundFixCount.incrementAndGet();
            }
        } finally {
            if (activeSocket == socket) {
                activeSocket = null;
            }
            connected = false;
        }
    }

    private void drainFixMessages(final ByteArrayOutputStream pending, final OutputStream output) throws IOException {
        String data = pending.toString(StandardCharsets.US_ASCII);
        int end;
        while ((end = data.indexOf(SOH + "10=")) >= 0) {
            final int checksumEnd = data.indexOf(SOH, end + 1);
            if (checksumEnd < 0) {
                break;
            }
            final String message = data.substring(0, checksumEnd + 1);
            inboundFixMessages.add(message);
            try {
                handleWireMessage(message, output);
            } catch (RuntimeException ex) {
                malformedInboundFixCount.incrementAndGet();
                writeFix(output, "j", Map.of("58", ex.getMessage() == null ? "malformed FIX" : ex.getMessage()));
            }
            data = data.substring(checksumEnd + 1);
        }
        pending.reset();
        pending.write(data.getBytes(StandardCharsets.US_ASCII));
    }

    private void handleWireMessage(final String message, final OutputStream output) throws IOException {
        final Map<String, String> fields = parseFixFields(message);
        final String msgType = fields.get("35");
        if (msgType == null) {
            malformedInboundFixCount.incrementAndGet();
            writeFix(output, "j", Map.of("58", "missing MsgType"));
            return;
        }
        switch (msgType) {
            case "A" -> handleWireLogon(fields, output);
            case "0" -> writeFix(output, "0", Map.of());
            case "5" -> {
                writeFix(output, "5", Map.of("58", "logout"));
                connected = false;
                closeQuietly(activeSocket);
            }
            case "V" -> handleWireMarketDataRequest(fields, output);
            case "D" -> handleWireNewOrder(fields, output);
            case "F" -> handleWireCancel(fields, output);
            default -> writeFix(output, "j", Map.of("58", "unsupported MsgType " + msgType));
        }
    }

    private void handleWireLogon(final Map<String, String> fields, final OutputStream output) throws IOException {
        final boolean compIdsMatch = config.targetCompId().equals(fields.get("49"))
            && config.senderCompId().equals(fields.get("56"));
        final boolean apiKeyMatches = apiKey.equals(fields.get("553"));
        final boolean signatureMatches = verifyLogonSignature(fields);
        if (!BEGIN_STRING.equals(fields.get("8")) || !compIdsMatch || !apiKeyMatches || !signatureMatches) {
            rejectedWireSessionCount.incrementAndGet();
            writeFix(output, "5", Map.of("58", "logon rejected"));
            return;
        }
        connected = true;
        wireLogonCount.incrementAndGet();
        writeFix(output, "A", Map.of("98", "0", "108", "30", "25035", fields.getOrDefault("25035", "2")));
    }

    private boolean verifyLogonSignature(final Map<String, String> fields) {
        final String rawData = fields.get("96");
        if (rawData == null || rawData.isBlank()) {
            return false;
        }
        try {
            final Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(buildLogonPayload(
                fields.getOrDefault("35", "A"),
                fields.getOrDefault("49", ""),
                fields.getOrDefault("56", ""),
                fields.getOrDefault("34", ""),
                fields.getOrDefault("52", "")).getBytes(StandardCharsets.US_ASCII));
            return verifier.verify(Base64.getDecoder().decode(rawData));
        } catch (Exception ex) {
            return false;
        }
    }

    public static String buildLogonPayload(
        final String msgType,
        final String senderCompId,
        final String targetCompId,
        final String msgSeqNum,
        final String sendingTime) {

        return msgType + SOH + senderCompId + SOH + targetCompId + SOH + msgSeqNum + SOH + sendingTime;
    }

    private void handleWireMarketDataRequest(final Map<String, String> fields, final OutputStream output)
        throws IOException {
        final String model = fields.getOrDefault("1022", fields.getOrDefault("25035", "L2"));
        final String symbol = fields.getOrDefault("55", primaryInstrument().symbol());
        acceptMarketDataSubscribe(model);
        if (!config.instruments().containsKey(symbol)) {
            writeFix(output, "Y", Map.of("55", symbol, "58", "unknown symbol"));
            return;
        }
        if ("L2".equals(model)) {
            final SimulatorConfig.Instrument instrument = config.instruments().get(symbol);
            sendWireMessage(output, toFixMarketByPrice(
                emitL2Update("BUY", symbol, instrument.bid(), 1.0, "SNAPSHOT")));
            sendWireMessage(output, toFixMarketByPrice(
                emitL2Update("SELL", symbol, instrument.ask(), 1.0, "SNAPSHOT")));
        } else if ("L3".equals(model)) {
            sendWireMessage(output, toFixMarketByOrder(
                emitL3AddOrder("WIRE-BID-1", "BUY", symbol, config.instruments().get(symbol).bid(), 1.0)));
            sendWireMessage(output, toFixMarketByOrder(
                emitL3AddOrder("WIRE-ASK-1", "SELL", symbol, config.instruments().get(symbol).ask(), 1.0)));
        }
    }

    private void handleWireNewOrder(final Map<String, String> fields, final OutputStream output) throws IOException {
        final String clOrdId = required(fields, "11");
        final String symbol = required(fields, "55");
        final String side = "1".equals(required(fields, "54")) ? "BUY" : "SELL";
        final double price = parseDouble(required(fields, "44"), "44");
        final double qty = parseDouble(required(fields, "38"), "38");
        final int reportStart = executionReports.size();
        acceptOrder(clOrdId, side, symbol, price, qty);
        sendReportsFrom(reportStart, output);
    }

    private void handleWireCancel(final Map<String, String> fields, final OutputStream output) throws IOException {
        final String cancelClOrdId = required(fields, "11");
        final String origClOrdId = required(fields, "41");
        final int reportStart = executionReports.size();
        acceptCancel(cancelClOrdId, origClOrdId);
        sendReportsFrom(reportStart, output);
    }

    private void sendReportsFrom(final int reportStart, final OutputStream output) throws IOException {
        final List<ExecutionReport> reports = executionReports();
        for (int i = reportStart; i < reports.size(); i++) {
            final ExecutionReport report = reports.get(i);
            final Map<String, String> fields = new LinkedHashMap<>();
            fields.put("11", report.clOrdId());
            fields.put("37", "SIM-" + report.clOrdId());
            fields.put("17", "EXEC-" + i);
            fields.put("150", report.execType());
            fields.put("39", report.ordStatus());
            fields.put("31", Double.toString(report.lastPx()));
            fields.put("32", Double.toString(report.lastQty()));
            fields.put("103", Integer.toString(report.rejectReason()));
            fields.put("58", report.text());
            writeFix(output, "8", fields);
        }
    }

    private void writeFix(final OutputStream output, final String msgType, final Map<String, String> fields)
        throws IOException {
        final Map<String, String> body = new LinkedHashMap<>();
        body.put("35", msgType);
        body.put("49", config.senderCompId());
        body.put("56", config.targetCompId());
        body.put("34", Integer.toString(outboundFixMessages.size() + 1));
        body.putAll(fields);
        final StringBuilder builder = new StringBuilder();
        builder.append("8=").append(BEGIN_STRING).append(SOH);
        builder.append("9=0").append(SOH);
        body.forEach((tag, value) -> builder.append(tag).append('=').append(value).append(SOH));
        builder.append("10=000").append(SOH);
        sendWireMessage(output, builder.toString());
    }

    private void sendWireMessage(final OutputStream output, final String message) throws IOException {
        outboundFixMessages.add(message);
        output.write(message.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private static Map<String, String> parseFixFields(final String message) {
        final Map<String, String> fields = new LinkedHashMap<>();
        for (String token : message.split(String.valueOf(SOH))) {
            if (token.isEmpty()) {
                continue;
            }
            final int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            fields.put(token.substring(0, equals), token.substring(equals + 1));
        }
        return fields;
    }

    private static String required(final Map<String, String> fields, final String tag) {
        final String value = fields.get(tag);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required FIX tag " + tag);
        }
        return value;
    }

    private static double parseDouble(final String value, final String tag) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric FIX tag " + tag + ": " + value, ex);
        }
    }

    private static PublicKey decodePublicKey(final String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new IllegalArgumentException("Binance simulator Ed25519 public key is required");
        }
        try {
            return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Binance simulator Ed25519 public key must be X.509 Base64", ex);
        }
    }

    private static void closeQuietly(final Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best-effort simulator cleanup.
            }
        }
    }

    private SimulatorConfig.Instrument primaryInstrument() {
        return config.instruments().values().iterator().next();
    }

    private static boolean closeTo(final double left, final double right) {
        return Math.abs(left - right) < 0.000001;
    }

    private static CoinbaseExchangeSimulator.FillMode toCoinbaseFillMode(final FillMode mode) {
        return switch (mode) {
            case IMMEDIATE -> CoinbaseExchangeSimulator.FillMode.IMMEDIATE;
            case PARTIAL_THEN_FULL -> CoinbaseExchangeSimulator.FillMode.PARTIAL_THEN_FULL;
            case REJECT_ALL -> CoinbaseExchangeSimulator.FillMode.REJECT_ALL;
            case NO_FILL -> CoinbaseExchangeSimulator.FillMode.NO_FILL;
            case DELAYED_FILL -> CoinbaseExchangeSimulator.FillMode.DELAYED_FILL;
            case DISCONNECT_ON_FILL -> CoinbaseExchangeSimulator.FillMode.DISCONNECT_ON_FILL;
        };
    }

    private static FillMode fromCoinbaseFillMode(final CoinbaseExchangeSimulator.FillMode mode) {
        return switch (mode) {
            case IMMEDIATE -> FillMode.IMMEDIATE;
            case PARTIAL_THEN_FULL -> FillMode.PARTIAL_THEN_FULL;
            case REJECT_ALL -> FillMode.REJECT_ALL;
            case NO_FILL -> FillMode.NO_FILL;
            case DELAYED_FILL -> FillMode.DELAYED_FILL;
            case DISCONNECT_ON_FILL -> FillMode.DISCONNECT_ON_FILL;
        };
    }

    private static void assertEventually(final BooleanCondition condition, final String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError(message);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link BinanceExchangeSimulator}. */
    public static final class Builder {
        private SimulatorConfig config = BinanceSimulatorScenarios.defaultConfig();
        private FillMode fillMode = FillMode.IMMEDIATE;
        private String instrumentSymbol = "BTCUSDT";
        private double initialBid = 65_000.00;
        private double initialAsk = 65_001.00;
        private String apiKey = BinanceSimulatorScenarios.DEFAULT_API_KEY;
        private String publicKeyBase64 = BinanceSimulatorScenarios.DEFAULT_PUBLIC_KEY_BASE64;
        private LongSupplier clock = System::currentTimeMillis;

        public Builder config(final SimulatorConfig value) { config = value; fillMode = fromCoinbaseFillMode(value.fillMode()); return this; }
        public Builder port(final int value) { config = config.withPort(value); return this; }
        public Builder fillMode(final FillMode value) { fillMode = value; return this; }
        public Builder instrument(final String symbol, final double bid, final double ask) {
            instrumentSymbol = symbol;
            initialBid = bid;
            initialAsk = ask;
            return this;
        }
        public Builder initialMarket(final double bid, final double ask) {
            initialBid = bid;
            initialAsk = ask;
            return this;
        }
        public Builder fillDelayMs(final long ms) { config = config.withFillDelay(ms); return this; }
        public Builder apiKey(final String value) { apiKey = value; return this; }
        public Builder publicKeyBase64(final String value) { publicKeyBase64 = value; return this; }
        public Builder clock(final LongSupplier value) { clock = value; return this; }

        public BinanceExchangeSimulator build() {
            return new BinanceExchangeSimulator(this);
        }
    }

    @FunctionalInterface
    private interface BooleanCondition {
        boolean matches();
    }

    public record ReceivedOrder(String clOrdId, String side, String symbol, double limitPrice, double qty, long receivedMs) {
    }

    public record ReceivedCancel(String clOrdId, String origClOrdId) {
    }

    public record MarketDataTick(String symbol, double bid, double ask, long timestampMs) {
    }

    public record L3OrderEvent(
        int seqNum,
        String orderId,
        String side,
        String symbol,
        double price,
        double size,
        String action,
        long timestampMs
    ) {
    }

    public record L2PriceLevelEvent(
        int seqNum,
        String side,
        String symbol,
        double price,
        double size,
        String action,
        long timestampMs
    ) {
    }

    public record ExecutionReport(
        String clOrdId,
        String execType,
        String ordStatus,
        double lastPx,
        double lastQty,
        int rejectReason,
        String text
    ) {
        static ExecutionReport ack(final SimulatorOrderBook.SimOrder order) {
            return new ExecutionReport(order.clOrdId(), "0", "0", 0.0, 0.0, -1, "");
        }

        static ExecutionReport fill(
            final SimulatorOrderBook.SimOrder order,
            final double lastPx,
            final double lastQty,
            final boolean finalFill
        ) {
            return new ExecutionReport(order.clOrdId(), "F", finalFill ? "2" : "1", lastPx, lastQty, -1, "");
        }

        static ExecutionReport reject(final SimulatorOrderBook.SimOrder order, final int reason, final String text) {
            return new ExecutionReport(order.clOrdId(), "8", "8", 0.0, 0.0, reason, text);
        }

        static ExecutionReport cancel(final SimulatorOrderBook.SimOrder order, final String cancelClOrdId) {
            return new ExecutionReport(cancelClOrdId, "4", "4", 0.0, 0.0, -1, order.clOrdId());
        }

        static ExecutionReport cancelReject(final String cancelClOrdId, final String origClOrdId) {
            return new ExecutionReport(cancelClOrdId, "9", "4", 0.0, 0.0, -1, origClOrdId);
        }
    }
}
