package io.iovo.node;

import io.iovo.node.crypto.Crypto;
import io.iovo.node.model.Account;
import io.iovo.node.model.Block;
import io.iovo.node.model.User;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static io.iovo.node.Iovo.*;
import static io.iovo.node.utils.TimeUtils.getEpochTime;

public class Forging {

    private static final int TRANSPARENT_FORGING_BLOCK = 30000;

    public static void forgeBlocks() {
        scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
            private final ConcurrentMap<Account, Block> lastBlocks = new ConcurrentHashMap<>();
            private final ConcurrentMap<Account, BigInteger> hits = new ConcurrentHashMap<>();

            @Override
            public void run() {
                try {
                    HashMap<Account, User> unlockedAccounts = new HashMap<>();
                    for (User user : users.values()) {
                        if (user.secretPhrase != null) {
                            Account account = accounts.get(Account.getId(Crypto.getPublicKey(user.secretPhrase)));
                            if (account != null && account.getEffectiveBalance() > 0) {
                                unlockedAccounts.put(account, user);
                            }
                        }
                    }

                    for (Map.Entry<Account, User> unlockedAccountEntry : unlockedAccounts.entrySet()) {
                        Account account = unlockedAccountEntry.getKey();
                        User user = unlockedAccountEntry.getValue();
                        Block lastBlock = Block.getLastBlock();
                        if (lastBlocks.get(account) != lastBlock) {
                            MessageDigest digest = MessageDigest.getInstance("SHA-256");
                            byte[] generationSignatureHash;
                            if (lastBlock.height < TRANSPARENT_FORGING_BLOCK) {
                                byte[] generationSignature = Crypto.sign(lastBlock.generationSignature, user.secretPhrase);
                                generationSignatureHash = digest.digest(generationSignature);
                            } else {
                                digest.update(lastBlock.generationSignature);
                                generationSignatureHash = digest.digest(Crypto.getPublicKey(user.secretPhrase));
                            }
                            BigInteger hit = new BigInteger(1, new byte[] {generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5],
                                    generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});
                            lastBlocks.put(account, lastBlock);
                            hits.put(account, hit);

                            JSONObject response = new JSONObject();
                            response.put("response", "setBlockGenerationDeadline");
                            response.put("deadline", hit.divide(BigInteger.valueOf(Block.getBaseTarget()).multiply(BigInteger.valueOf(account.getEffectiveBalance()))).longValue() - (getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp));
                            user.send(response);
                        }

                        int elapsedTime = getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp;
                        if (elapsedTime > 0) {
                            BigInteger target = BigInteger.valueOf(Block.getBaseTarget()).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
                            if (hits.get(account).compareTo(target) < 0) {
                                account.generateBlock(user.secretPhrase);
                            }
                        }
                    }
                } catch (Exception e) { }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}
