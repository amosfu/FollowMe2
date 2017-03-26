package com.shuai.followme2.bean;

import android.app.Application;
import android.util.Log;

import com.shuai.followme2.util.Utils;

import java.net.CookieManager;

import info.guardianproject.netcipher.proxy.OrbotHelper;

/**
 * Created by Amos on 2017-03-21.
 */

public class MyCustomApplication extends Application {
    private KeyObject keyObject;
    private CookieManager cookieManager;
    private OrbotHelper orbotHelper;

    public OrbotHelper getOrbotHelper() {
        return orbotHelper;
    }

    public void setOrbotHelper(OrbotHelper orbotHelper) {
        this.orbotHelper = orbotHelper;
    }

    public KeyObject getKeyObject() {
        return keyObject;
    }

    public void setKeyObject(KeyObject keyObject) {
        this.keyObject = keyObject;
    }

    public CookieManager getCookieManager() {
        return cookieManager;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        orbotHelper = OrbotHelper.get(this);
        if (orbotHelper.isInstalled()) {
            Log.i(Utils.APP_LABEL,"Orbot is installed!");
            orbotHelper.init();
            Utils.isTorEnabled = true;
        } else {
            Log.i(Utils.APP_LABEL,"Orbot is NOT installed!");
            Utils.isTorEnabled = false;
        }
    }

    public void setCookieManager(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }
}
