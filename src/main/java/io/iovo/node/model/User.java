package io.iovo.node.model;

import io.iovo.node.crypto.Crypto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentLinkedQueue;

public class User {
    private static final Logger logger = LogManager.getLogger();

    private ConcurrentLinkedQueue<JSONObject> pendingResponses;
    private AsyncContext asyncContext;
    public String secretPhrase;

    public User() {
        pendingResponses = new ConcurrentLinkedQueue<>();
    }

    public void deinitializeKeyPair() {
        secretPhrase = null;
    }

    public BigInteger initializeKeyPair(String secretPhrase) throws Exception {
        this.secretPhrase = secretPhrase;
        byte[] publicKeyHash = MessageDigest.getInstance("SHA-256").digest(Crypto.getPublicKey(secretPhrase));
        BigInteger bigInteger = new BigInteger(1,
                new byte[] {publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4],
                        publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0]});
        return bigInteger;
    }

    public void send(JSONObject response) {
        synchronized (this) {
            if (asyncContext == null) {
                pendingResponses.offer(response);
            } else {
                JSONArray responses = new JSONArray();
                JSONObject pendingResponse;
                while ((pendingResponse = pendingResponses.poll()) != null) {
                    responses.add(pendingResponse);
                }
                responses.add(response);

                JSONObject combinedResponse = new JSONObject();
                combinedResponse.put("responses", responses);

                try {
                    asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                    ServletOutputStream servletOutputStream = asyncContext.getResponse().getOutputStream();
                    servletOutputStream.write(combinedResponse.toString().getBytes("UTF-8"));
                    servletOutputStream.close();

                    asyncContext.complete();
                    asyncContext = null;
                } catch (Exception e) {
                    logger.error("17: " + e.toString());
                }
            }
        }
    }
}
