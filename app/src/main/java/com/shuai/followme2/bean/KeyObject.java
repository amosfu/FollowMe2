package com.shuai.followme2.bean;

import com.shuai.followme2.util.Utils;

import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

/**
 * Created by Amos on 2017-03-19.
 */
public class KeyObject implements Serializable {
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private PublicKey receivedPublicKey;
    private KeyAgreement keyAgreement;
    private byte[] secretKey;
    private String sharedSecret;

    public KeyObject(String userpass) throws NoSuchAlgorithmException {
        this.sharedSecret = userpass;
        KeyPair keyPair = Utils.KEY_PAIR_GENERATOR.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
        try {
            keyAgreement = KeyAgreement.getInstance("DH");
            keyAgreement.init(privateKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] generateKeyExchangeMsg() {
        return Utils.encryptJsonObject(new KeyTransfer(publicKey.getEncoded()), sharedSecret.getBytes());
    }

    public PublicKey parseKeyExchangeMsg(byte[] keyExchangeMsg) throws Exception {
        try {
            KeyTransfer keyTransfer = Utils.decryptJsonObject(keyExchangeMsg, sharedSecret.getBytes(), KeyTransfer.class);
            receivedPublicKey = KeyFactory.getInstance("DH").generatePublic(new X509EncodedKeySpec(keyTransfer.getKey()));
            keyAgreement.doPhase(receivedPublicKey, true);
            secretKey = Utils.shortenSecretKey(keyAgreement.generateSecret(), 256);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return receivedPublicKey;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public synchronized PublicKey getReceivedPublicKey() {
        return receivedPublicKey;
    }

    public synchronized void setReceivedPublicKey(PublicKey receivedPublicKey) {
        this.receivedPublicKey = receivedPublicKey;
    }

    public synchronized byte[] getSecretKey() {
        return secretKey;
    }

    public synchronized void setSecretKey(byte[] secretKey) {
        this.secretKey = secretKey;
    }


}
