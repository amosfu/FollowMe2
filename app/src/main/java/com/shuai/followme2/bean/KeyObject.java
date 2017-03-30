package com.shuai.followme2.bean;

import com.shuai.followme2.util.Utils;

import java.io.Serializable;
import org.apache.commons.codec.binary.Base64;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyAgreement;

/**
 * Created by Amos on 2017-03-19.
 */
public class KeyObject implements Serializable {
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private Map<String, PublicKey> receivedPublicKeyMap = new ConcurrentHashMap<>();
    private Map<String, KeyAgreement> keyAgreementMap = new ConcurrentHashMap<>();
    private Map<String, byte[]> secretKeyMap = new ConcurrentHashMap<>();
    private String sharedSecret;

    public KeyObject(String userpass) throws NoSuchAlgorithmException {
        this.sharedSecret = userpass;
        KeyPair keyPair = Utils.KEY_PAIR_GENERATOR.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    public byte[] generateKeyExchangeMsg() {
        return Utils.encryptJsonObject(new KeyTransfer(publicKey.getEncoded()), sharedSecret.getBytes());
    }

    public Map<String, PublicKey> parseKeyExchangeMsg(byte[] keyExchangeMsg) throws Exception {
        try {
            Map<String, String> encryptedKeyMap = (Map<String, String>) Utils.decodeJsonToObject(keyExchangeMsg, Map.class);
            if (encryptedKeyMap != null && !encryptedKeyMap.isEmpty()) {
                Map<String, KeyTransfer> keyTransferMap = new LinkedHashMap<>();
                for (String id : encryptedKeyMap.keySet()) {
                    String keyStr = encryptedKeyMap.get(id);
                    keyTransferMap.put(id, Utils.decryptJsonObject(Base64.decodeBase64(keyStr), sharedSecret.getBytes(), KeyTransfer.class));
                }
                if (keyTransferMap != null && !keyTransferMap.isEmpty()) {
                    // decode received public keys
                    for (String followerID : keyTransferMap.keySet()) {
                        PublicKey tempReceivedKey = KeyFactory.getInstance("DH").generatePublic(new X509EncodedKeySpec(keyTransferMap.get(followerID).getKey()));
                        receivedPublicKeyMap.put(followerID, tempReceivedKey);
                        // generate DH key for followers
                        KeyAgreement keyAgreement = keyAgreementMap.get(followerID);
                        if (keyAgreement == null) {
                            keyAgreement = KeyAgreement.getInstance("DH");
                            keyAgreement.init(privateKey);
                            keyAgreementMap.put(followerID, keyAgreement);
                        }
                        keyAgreement.doPhase(tempReceivedKey, true); // TODO may cause problems
                        secretKeyMap.put(followerID, keyAgreement.generateSecret());
                    }
                }
            }
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return receivedPublicKeyMap;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Map<String, PublicKey> getReceivedPublicKeyMap() {
        return receivedPublicKeyMap;
    }

    public void setReceivedPublicKeyMap(Map<String, PublicKey> receivedPublicKeyMap) {
        this.receivedPublicKeyMap = receivedPublicKeyMap;
    }

    public Map<String, KeyAgreement> getKeyAgreementMap() {
        return keyAgreementMap;
    }

    public void setKeyAgreementMap(Map<String, KeyAgreement> keyAgreementMap) {
        this.keyAgreementMap = keyAgreementMap;
    }

    public Map<String, byte[]> getSecretKeyMap() {
        return secretKeyMap;
    }

    public void setSecretKeyMap(Map<String, byte[]> secretKeyMap) {
        this.secretKeyMap = secretKeyMap;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }
}
