package com.shuai.followme2.bean;

import android.app.Application;

import java.net.CookieManager;

/**
 * Created by Amos on 2017-03-21.
 */

public class MyCustomApplication extends Application {
    private KeyObject keyObject;
    private CookieManager cookieManager;
    private boolean isTorEnabled;

    public KeyObject getKeyObject() {
        return keyObject;
    }

    public void setKeyObject(KeyObject keyObject) {
        this.keyObject = keyObject;
    }

    public CookieManager getCookieManager() {
        return cookieManager;
    }

    public void setCookieManager(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    public boolean isTorEnabled() {
        return isTorEnabled;
    }

    public void setTorEnabled(boolean torEnabled) {
        isTorEnabled = torEnabled;
    }
}
