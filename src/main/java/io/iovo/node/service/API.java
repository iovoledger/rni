package io.iovo.node.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.iovo.node.Iovo;
import io.iovo.node.conf.Configuration;
import io.iovo.node.crypto.Crypto;
import io.iovo.node.model.Account;
import io.iovo.node.model.Block;
import io.iovo.node.model.Peer;
import io.iovo.node.model.Transaction;
import io.iovo.node.service.model.*;
import io.iovo.node.utils.TimeUtils;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.streams.ChannelInputStream;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.iovo.node.Iovo.*;
import static io.iovo.node.model.Block.BLOCK_HEADER_LENGTH;
import static io.iovo.node.utils.ConvertUtils.convert;
import static io.iovo.node.utils.TimeUtils.getEpochTime;
import static java.lang.Long.parseUnsignedLong;

public class API {
    private static final Logger logger = LogManager.getLogger();

    private Iovo instance;
    private Undertow server;
    private final Gson gson = new GsonBuilder().create();

    public API(Iovo iovo) {
        this.instance = iovo;
    }

    public void init() {
        server = Undertow.builder()
                .addHttpListener(Configuration.getInt(Configuration.API_PORT), Configuration.getString(Configuration.API_HOST))
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws IOException {
                        HttpString requestMethod = exchange.getRequestMethod();

                        if (Methods.OPTIONS.equals(requestMethod)) {
                            String allowedMethods = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";
                            exchange.setStatusCode(StatusCodes.OK);
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MimeMappings.DEFAULT_MIME_MAPPINGS.get("txt"));
                            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, 0);
                            exchange.getResponseHeaders().put(Headers.ALLOW, allowedMethods);
                            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
                            exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Headers"), "Origin, X-Requested-With, Content-Type, Accept, X-IOTA-API-Version");
                            exchange.getResponseSender().close();
                            return;
                        }

                        if (exchange.isInIoThread()) {
                            exchange.dispatch(this);
                            return;
                        }

                        processRequest(exchange);
                    }
                }).build();
        server.start();
    }

    private void processRequest(final HttpServerExchange exchange) throws IOException {
        final ChannelInputStream cis = new ChannelInputStream(exchange.getRequestChannel());
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

        final long beginningTime = System.currentTimeMillis();
        final String body = IOUtils.toString(cis, StandardCharsets.UTF_8);
        final Response response;
//        if (!exchange.getRequestHeaders().contains("X-IOVO-API-Version")) {
//            response = ErrorResponse.create("Invalid API Version");
//        }
        if (body.length() > Configuration.getInt(Configuration.MAX_BODY_LENGTH)) {
            response = new ErrorResponse("Request too long");
        } else {
            HttpString method = exchange.getRequestMethod();

            if (Methods.GET.equals(method)) {
                response = processGet(exchange.getRelativePath(), exchange.getSourceAddress());
            } else {
                response = process(body, exchange.getSourceAddress());
            }
        }
        sendResponse(exchange, response, beginningTime);
    }

    private Response processGet(final String pathString, InetSocketAddress sourceAddress) {

        final String command = pathString.replace("/api/", "");

        switch (command) {
            case "getState": {
                return getStateResponse();
            }
            case "getCumulativeDifficulty": {
                return getCumulativeDifficulty();
            }
            case "getMilestoneBlocksIds": {
                return getMilestoneBlocksIdsResponse();
            }
            case "getPeers": {
                return getPeersResponse();
            }
            case "getUnconfirmedTransactionIds": {
                return getUnconfirmedTransactionIdsResponse();

            }
            // TODO: IOVO-17 MORE FUNCTIONS - GET HEIGHT, GET PEERS
            default: {
                return new ErrorResponse("Command [" + command + "] is unknown");
            }
        }
    }

    private Response process(final String requestString, InetSocketAddress sourceAddress) {
        final Map<String, Object> request = gson.fromJson(requestString, Map.class);
        if (request == null) {
            return new ExceptionResponse("Invalid request payload: '" + requestString + "'");
        }

        final String command = (String) request.get("command");
        if (command == null) {
            return new ErrorResponse("COMMAND parameter has not been specified in the request.");
        }

        switch (command) {
            case "getBalance": {
                return getBalanceResponse(request);
            }
            case "getState": {
                return getStateResponse();
            }
            case "getCumulativeDifficulty": {
                return getCumulativeDifficulty();
            }
            case "getMilestoneBlocksIds": {
                return getMilestoneBlocksIdsResponse();
            }
            case "getNextBlocksIds": {
                return getNextBlocksIds(request);
            }
            case "getNextBlocks": {
                return getNextBlocks(request);
            }
            case "processBlock": {
                return processBlock(request);
            }
            case "getPeers": {
                return getPeersResponse();
            }
            case "getInfo": {
                return getInfoResponse(sourceAddress, request);
            }
            case "sendMoney": {
                return sendMoneyResponse(request);
            }
            case "getBlock": {
                return getBlockResponse(request);
            }
            case "getUnconfirmedTransactionIds": {
                return getUnconfirmedTransactionIdsResponse();
            }
            case "processTransactions": {
                return processTransactionsResponse(request);
            }
            // TODO: IOVO-17 MORE FUNCTIONS - GET HEIGHT, GET PEERS
            default: {
                return new ErrorResponse("Command [" + command + "] is unknown");
            }
        }
    }

    private Response processTransactionsResponse(Map<String,Object> request) {
        Transaction.processTransactions(request, "transactions");
        return new ProcessTransactionResponse();
    }

    private Response getUnconfirmedTransactionIdsResponse() {
        List<String> transactionIds = new ArrayList<>();
        for (Transaction transaction : unconfirmedTransactions.values()) {
            transactionIds.add(transaction.getStringId());
        }
        return new GetUnconfirmedTransactionIdsResponse(transactionIds);
    }

    private Response getBlockResponse(Map<String,Object> request) {
        String block = (String) request.get("block");
        if (block == null) {
            return new ErrorResponse("\"block\" not specified");
        } else {
            try {
                Block blockData = blocks.get(parseUnsignedLong(block));
                if (blockData == null) {
                    return new ErrorResponse("Unknown block");
                } else {
                    int height = blockData.height;
                    String generator = "";//convert(blockData.getGeneratorAccountId());
                    int timestamp = blockData.timestamp;
                    int numberOfTransactions = blockData.transactions.length;
                    int totalAmount = blockData.totalAmount;
                    int totalFee = blockData.totalFee;
                    int payloadLength = blockData.payloadLength;
                    int version = blockData.version;
                    String baseTarget = convert(blockData.baseTarget);
                    String previousBlock = "";
                    if (blockData.previousBlock != 0) {
                        previousBlock = convert(blockData.previousBlock);
                    }
                    String nextBlock = "";
                    if (blockData.nextBlock != 0) {
                        nextBlock = convert(blockData.nextBlock);
                    }
                    String payloadHash = convert(blockData.payloadHash);
                    String generationSignature = convert(blockData.generationSignature);
                    String previousBlockHash = "";
                    if (blockData.version > 1) {
                        previousBlockHash = "";//convert(blockData.previousBlockHash);
                    }
                    String blockSignature = convert(blockData.blockSignature);
                    List<String> transactions = new ArrayList<>();
                    for (long transactionId : blockData.transactions) {
                        transactions.add(convert(transactionId));
                    }
                    return new GetBlockResponse(height, generator, timestamp, numberOfTransactions, totalAmount,
                            totalFee, payloadLength, version, baseTarget, previousBlock, nextBlock, payloadHash,
                            generationSignature, previousBlockHash, blockSignature, transactions);
                }
            } catch (Exception e) {
                return new ErrorResponse("Incorrect block");
            }
        }
    }

    private Response sendMoneyResponse(Map<String,Object> request) {
        // CHECK BLOCKCHAIN IS UP TO DATE
        String secretPhrase = (String) request.get("secretPhrase");
        String recipientValue = (String) request.get("recipient");
        String amountValue = (String) request.get("amount");
        String feeValue = (String) request.get("fee");
        String deadlineValue = (String) request.get("deadline");
        String referencedTransactionValue = (String) request.get("referencedTransaction");
        if (secretPhrase == null) {
            return new ErrorResponse("\"secretPhrase\" not specified");
        } else if (recipientValue == null) {
            return new ErrorResponse("\"recipient\" not specified");
        } else if (amountValue == null) {
            return new ErrorResponse("\"amount\" not specified");
        } else if (feeValue == null) {
            return new ErrorResponse("\"fee\" not specified");
        } else if (deadlineValue == null) {
            return new ErrorResponse("\"deadline\" not specified");
        } else {
            //TODO: fix ugly error handling
            try {
                long recipient = parseUnsignedLong(recipientValue);
                try {
                    int amount = Integer.parseInt(amountValue);
                    if (amount <= 0 || amount >= MAX_BALANCE) {
                        throw new Exception();
                    }
                    try {
                        int fee = Integer.parseInt(feeValue);
                        if (fee <= 0 || fee >= MAX_BALANCE) {
                            throw new Exception();
                        }
                        try {
                            short deadline = Short.parseShort(deadlineValue);
                            if (deadline < 1) {
                                throw new Exception();
                            }
                            long referencedTransaction = referencedTransactionValue == null ? 0 : parseUnsignedLong(referencedTransactionValue);
                            byte[] publicKey = Crypto.getPublicKey(secretPhrase);
                            Account account = accounts.get(Account.getId(publicKey));
                            if (account == null) {
                                return new ErrorResponse("Not enough funds");
                            } else {
                                if ((amount + fee) * 100L > account.getUnconfirmedBalance()) {
                                    return new ErrorResponse("Not enough funds");
                                } else {
                                    Transaction transaction = new Transaction(Transaction.TYPE_PAYMENT, Transaction.SUBTYPE_PAYMENT_ORDINARY_PAYMENT, getEpochTime(System.currentTimeMillis()), deadline, publicKey, recipient, amount, fee, referencedTransaction, new byte[64]);
                                    transaction.sign(secretPhrase);
                                    JSONObject peerRequest = new JSONObject();
                                    peerRequest.put("requestType", "processTransactions");
                                    JSONArray transactionsData = new JSONArray();
                                    transactionsData.add(transaction.getJSONObject());
                                    peerRequest.put("transactions", transactionsData);
                                    String transactionId = transaction.getStringId();
                                    String transactionBytes = convert(transaction.getBytes());
                                    Peer.sendToAllPeers(peerRequest); // firstly was sendToSomePeers
                                    nonBroadcastedTransactions.put(transaction.id, transaction);
                                    return new SendMoneyResponse(transactionId, transactionBytes);
                                }
                            }
                        } catch (Exception e) {
                            return new ErrorResponse("Incorrect deadline");
                        }
                    } catch (Exception e) {
                        return new ErrorResponse("Incorrect fee");
                    }
                } catch (Exception e) {
                    return new ErrorResponse("Incorrect amount");
                }
            } catch (Exception e) {
                return new ErrorResponse("Incorrect recipient");
            }
        }
    }

    private Response getInfoResponse(InetSocketAddress sourceAddress, Map<String, Object> request) {
        Peer peer = Peer.addPeer(sourceAddress.getAddress().getHostAddress(), sourceAddress.getAddress().getHostAddress());
        if (peer != null) {
            if (peer.state == Peer.STATE_DISCONNECTED) {
                peer.setState(Peer.STATE_CONNECTED);
            }
           // TODO: peer.updateDownloadedVolume(cis.getCount());
        }
        if (peer != null) {
            String announcedAddress = (String) request.get("announcedAddress");
            if (announcedAddress != null) {
                announcedAddress = announcedAddress.trim();
                if (announcedAddress.length() > 0) {
                    peer.announcedAddress = announcedAddress;
                }
            }
            String application = (String) request.get("application");
            if (application == null) {
                application = "?";
            } else {
                application = application.trim();
                if (application.length() > 20) {
                    application = application.substring(0, 20) + "...";
                }
            }
            peer.application = application;
            String version = (String)request.get("version");
            if (version == null) {
                version = "?";
            } else {
                version = version.trim();
                if (version.length() > 10) {
                    version = version.substring(0, 10) + "...";
                }
            }
            peer.version = version;
            String platform = (String)request.get("platform");
            if (platform == null) {
                platform = "?";
            } else {
                platform = platform.trim();
                if (platform.length() > 10) {
                    platform = platform.substring(0, 10) + "...";
                }
            }
            peer.platform = platform;
            peer.shareAddress = Boolean.TRUE.equals(request.get("shareAddress"));
            if (peer.analyzeHallmark(sourceAddress.getAddress().getHostAddress(), (String)request.get("hallmark"))) {
                peer.setState(Peer.STATE_CONNECTED);
            }
        }
        String myPlatform = "";
        String hallmark = "";
        if (myHallmark != null && myHallmark.length() > 0) {
            hallmark = myHallmark;
        }
        return new GetInfoResponse(hallmark, "IOVO", VERSION, myPlatform, shareMyAddress);
    }

    private Response getPeersResponse() {
        List<String> peers = new ArrayList<>();
        peers.addAll(Iovo.peers.keySet());
        return new GetPeersResponse(peers);
    }

    private Response processBlock(Map<String, Object> request) {
        boolean accepted;
        Block block = Block.getBlockFromRequest(request);
        if (block == null) {
            accepted = false;
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(BLOCK_HEADER_LENGTH + block.payloadLength);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(block.getBytes());
            List<Map<String, Object>> transactionsData = ((List) request.get("transactions"));
            for (Map<String, Object> transaction : transactionsData) {
                buffer.put(Transaction.getTransactionFromRequest(transaction).getBytes());
            }
            accepted = Block.pushBlock(buffer, true);
        }
        return new ProcessBlockResponse(accepted);
    }

    private Response getNextBlocks(Map<String, Object> request) {
        List<Block> nextBlocks = new ArrayList<>();
        int totalLength = 0;
        Block block = Iovo.blocks.get((new BigInteger((String)request.get("blockId")).longValue()));
        while (block != null) {
            block = Iovo.blocks.get(block.nextBlock);
            if (block != null) {
                int length = BLOCK_HEADER_LENGTH + block.payloadLength;
                if (totalLength + length > 1048576) {
                    break;
                }
                nextBlocks.add(block);
                totalLength += length;
            }
        }
        List<GetNextBlocksResponse.BlockObject> nextBlocksList = new ArrayList<>();
        for (Block nextBlock : nextBlocks) {
            nextBlocksList.add(new GetNextBlocksResponse.BlockObject(nextBlock, Iovo.transactions));
        }
        return new GetNextBlocksResponse(nextBlocksList);
    }

    private Response getNextBlocksIds(Map<String, Object> request) {
        JSONArray nextBlockIds = new JSONArray();
        Block block = Iovo.blocks.get((new BigInteger((String)request.get("blockId")).longValue()));
        while (block != null && nextBlockIds.size() < 1440) {
            block = Iovo.blocks.get(block.nextBlock);
            if (block != null) {
                try {
                    nextBlockIds.add(convert(block.getId()));
                } catch (Exception e) { }
            }
        }
        return new GetNextBlocksIdsResponse(nextBlockIds);
    }

    private Response getMilestoneBlocksIdsResponse() {
        List<String> milestoneBlockIds = new ArrayList<>();
        Block block = Block.getLastBlock();
        int jumpLength = block.height * 4 / 1461 + 1;
        while (block.height > 0) {
            try {
                milestoneBlockIds.add(convert(block.getId()));
            } catch (Exception e) { }
            for (int i = 0; i < jumpLength && block.height > 0; i++) {
                block = Iovo.blocks.get(block.previousBlock);
            }
        }
        return new GetMilestoneBlocksIdsResponse(milestoneBlockIds);
    }

    private Response getCumulativeDifficulty() {
        return new GetCumulativeDifficultyResponse(Block.getLastBlock().cumulativeDifficulty.toString());
    }

    private Response getStateResponse() {
        return new GetStateResponse(VERSION, getEpochTime(System.currentTimeMillis()), convert(Iovo.lastBlock),
                Iovo.blocks.size(), Iovo.transactions.size(), Iovo.accounts.size());
    }

    private Response getBalanceResponse(Map<String, Object> request) {
        String account = (String) request.get("account");
        logger.info("Request for balance of " + account);
        Account accountData = Iovo.accounts.get((new BigInteger(account)).longValue());
        long balance, unconfirmedBalance, effectiveBalance;
        if (accountData == null) {
            balance = 0;
            unconfirmedBalance = 0;
            effectiveBalance = 0;
        } else {
            balance = accountData.getBalance();
            unconfirmedBalance = accountData.getUnconfirmedBalance();
            effectiveBalance = accountData.getEffectiveBalance() * 100L;
        }
        return new GetBalanceResponse(balance, unconfirmedBalance, effectiveBalance, account);
    }

    private void sendResponse(HttpServerExchange exchange, Response res, long beginningTime) {
        res.setDuration((int) (System.currentTimeMillis() - beginningTime));
        final String response = gson.toJson(res);

        if (res instanceof ErrorResponse) {
            exchange.setStatusCode(400); // bad request
        }// else if (res instanceof AccessLimitedResponse) {
//            exchange.setStatusCode(401); // api method not allowed
//        } else if (res instanceof ExceptionResponse) {
//            exchange.setStatusCode(500); // internal error
//        }

        setupResponseHeaders(exchange);

        ByteBuffer responseBuf = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        exchange.setResponseContentLength(responseBuf.array().length);
        StreamSinkChannel sinkChannel = exchange.getResponseChannel();
        sinkChannel.getWriteSetter().set(channel -> {
            if (responseBuf.remaining() > 0)
                try {
                    sinkChannel.write(responseBuf);
                    if (responseBuf.remaining() == 0) {
                        exchange.endExchange();
                    }
                } catch (IOException e) {
                    logger.error("Lost connection to client - cannot send response");
                    exchange.endExchange();
                    sinkChannel.getWriteSetter().set(null);
                }
            else {
                exchange.endExchange();
            }
        });
        sinkChannel.resumeWrites();
    }

    private static void setupResponseHeaders(final HttpServerExchange exchange) {
        final HeaderMap headerMap = exchange.getResponseHeaders();
        headerMap.add(new HttpString("Access-Control-Allow-Origin"), "*");
        headerMap.add(new HttpString("Keep-Alive"), "timeout=500, max=100");
    }

    public void shutdown() {
        if (server != null) {
            server.stop();
        }
    }
}
