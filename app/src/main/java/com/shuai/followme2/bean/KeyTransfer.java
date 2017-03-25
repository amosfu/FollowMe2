package com.shuai.followme2.bean;

/**
 * Created by Amos on 2017-03-19.
 */
public class KeyTransfer {
    private byte[] key;

    public KeyTransfer() {
    }

    public KeyTransfer(byte[] key) {
        this.key = key;
    }

    public byte[] getKey() {
        return key;
    }
}

