package com.shuai.followme2.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Created by Amos on 2017-03-09.
 */

public class Utils {
    public static final String APP_LABEL = "FollowMe2";
    public static final String SERVER_DOMAIN = "https://whiteboard-afu.rhcloud.com";
    //public static final String SERVER_DOMAIN = "http://10.0.2.2:8080";

    public static KeyPairGenerator KEY_PAIR_GENERATOR;
    public static KeyFactory KEY_FACTORY;

    private static final TorClient TOR_CLIENT = new TorClient();
    private static boolean isTorEnabled = false;
    public static CountDownLatch torLock = new CountDownLatch(1);

    public static boolean isTorEnabled() {
        return isTorEnabled;
    }

    public static void setIsTorEnabled(boolean isTorEnabled) {
        Utils.isTorEnabled = isTorEnabled;
    }

    static {
        try {
            KEY_PAIR_GENERATOR = KeyPairGenerator.getInstance("DH");
            BigInteger p = new BigInteger("f460d489678f7ec903293517e9193fd156c821b3e2b027c644eb96aedc85a54c971468cea07df15e9ecda0e2ca062161add38b9aa8aefcbd7ac18cd05a6bfb1147aaa516a6df694ee2cb5164607c618df7c65e75e274ff49632c34ce18da534ee32cfc42279e0f4c29101e89033130058d7f77744dddaca541094f19c394d485", 16);
            BigInteger g = new BigInteger("9ce2e29b2be0ebfd7b3c58cfb0ee4e9004e65367c069f358effaf2a8e334891d20ff158111f54b50244d682b720f964c4d6234079d480fcc2ce66e0fa3edeb642b0700cd62c4c02a483c92d2361e41a23706332bd3a8aaed07fe53bba376cefbce12fa46265ad5ea5210a3d96f5260f7b6f29588f61a4798e40bdc75bbb2b457", 16);
            int l = 512;

            DHParameterSpec dhSpec = new DHParameterSpec(p, g, l);
            KEY_PAIR_GENERATOR.initialize(dhSpec);
            KEY_FACTORY = KeyFactory.getInstance("DH");

        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
    }

    public static void startOrchid(ProgressDialog progressDialog, Activity loginActivity) {
        TOR_CLIENT.addInitializationListener(createInitalizationListner(progressDialog,loginActivity));
        TOR_CLIENT.start();
        TOR_CLIENT.enableSocksListener();//or client.enableSocksListener(yourPortNum);
    }

    public static TorInitializationListener createInitalizationListner(final ProgressDialog progressDialog, final Activity loginActivity) {
        return new TorInitializationListener() {
            @Override
            public void initializationProgress(String message, final int percent) {
                final String msg = ">>> [ " + percent + "% ]: " + message;
                Log.i(APP_LABEL, msg);
                loginActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(percent);
                        progressDialog.setMessage(msg);
                    }
                });
            }

            @Override
            public void initializationCompleted() {
                Log.i(APP_LABEL, "Tor is ready to go!");
                torLock.countDown();
            }
        };
    }

    private synchronized static HttpsURLConnection getHttpsURLConnection(URL url) throws Exception {
        if (isTorEnabled) {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 9150));
            return (HttpsURLConnection) url.openConnection(proxy);
        }
        return (HttpsURLConnection) url.openConnection();
    }


    public static byte[] encryptJsonObject(Object input, byte[] key) {
        byte[] encrypted = null;
        String json = encodeObjectToJson(input);
        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA-256");
            // generate 256bit AES key
            byte[] digestedKey = sha3.digest(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(shortenSecretKey(digestedKey, 128), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            encrypted = cipher.doFinal(json.getBytes());
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return encrypted;
    }

    public static <T> T decryptJsonObject(byte[] input, byte[] key, Class<T> outputClass) {
        T decryptedObject = null;
        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA-256");
            // generate 256bit AES key
            byte[] digestedKey = sha3.digest(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(shortenSecretKey(digestedKey, 128), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            String json = new String(cipher.doFinal(input));
            decryptedObject = decodeJsonToObject(json, outputClass);
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return decryptedObject;
    }

    public static byte[] sendByteArrAsFileViaHTTP(byte[] payload, CookieManager cookieManager, String url, Boolean isKey) {
        byte[] response = null;
        try {
            String attachmentName = isKey ? "keyUpload" : "dataUpload";
            String attachmentFileName = attachmentName + ".bmp";

            ContentBody contentPart = new ByteArrayBody(payload, attachmentName);
            MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            reqEntity.addPart(attachmentName, contentPart);

            URL httpsUrl = new URL(url);
            HttpsURLConnection conn = getHttpsURLConnection(httpsUrl);
//            conn.setReadTimeout(10000);
//            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            if (cookieManager.getCookieStore().getCookies().size() > 0) {
                // While joining the Cookies, use ',' or ';' as needed. Most of the servers are using ';'
                conn.setRequestProperty("Cookie",
                        TextUtils.join(";", cookieManager.getCookieStore().getCookies()));
            }

            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.addRequestProperty("Content-length", reqEntity.getContentLength() + "");
            conn.addRequestProperty(reqEntity.getContentType().getName(), reqEntity.getContentType().getValue());

            OutputStream os = conn.getOutputStream();
            reqEntity.writeTo(conn.getOutputStream());
            os.close();
            conn.connect();

            if (conn.getResponseCode() == HttpsURLConnection.HTTP_OK) {
                response = IOUtils.toByteArray(conn.getInputStream());
            }
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return response;
    }

    public static String encodeObjectToJson(Object input) {
        String json = "";
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            json = ow.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return json;
    }

    public static <T> T decodeJsonToObject(String input, Class<T> outputClass) {
        T output = null;
        try {
            output = new ObjectMapper().readValue(input, outputClass);
        } catch (IOException e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return output;
    }

    /**
     * 1024 bit symmetric key size is so big for DES so we must shorten the key size. You can get first 8 longKey of the
     * byte array or can use a key factory
     *
     * @param longKey
     * @return
     */
    public static byte[] shortenSecretKey(final byte[] longKey, int newKeyLength) {
        try {
            // Use 8 bytes (64 bits) for DES, 6 bytes (48 bits) for Blowfish
            final byte[] shortenedKey = new byte[newKeyLength / 8];
            System.arraycopy(longKey, 0, shortenedKey, 0, shortenedKey.length);
            return shortenedKey;

            // Below lines can be more secure
            // final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            // final DESKeySpec       desSpec    = new DESKeySpec(longKey);
            //
            // return keyFactory.generateSecret(desSpec).getEncoded();
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] sendHTTPSWithNameValuePair(String url, List<NameValuePair> nameValuePair, CookieManager cookieManager) {
        byte[] response = null;
        // Url Encoding the POST parameters
        try {
            URL httpsUrl = new URL(url);
            HttpsURLConnection conn = getHttpsURLConnection(httpsUrl);
//            conn.setReadTimeout(10000);
//            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            if (cookieManager.getCookieStore().getCookies().size() > 0) {
                // While joining the Cookies, use ',' or ';' as needed. Most of the servers are using ';'
                conn.setRequestProperty("Cookie",
                        TextUtils.join(";", cookieManager.getCookieStore().getCookies()));
            }

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            writer.write(getQuery(nameValuePair));
            writer.flush();
            writer.close();
            os.close();
            conn.connect();


            if (200 <= conn.getResponseCode() && conn.getResponseCode() <= 299) {
                response = IOUtils.toByteArray(conn.getInputStream());
            } else {
                response = IOUtils.toByteArray(conn.getInputStream());
            }
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return response;
    }

    private static String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }

        return result.toString();
    }
}
