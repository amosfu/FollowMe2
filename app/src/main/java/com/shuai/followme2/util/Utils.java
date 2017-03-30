package com.shuai.followme2.util;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;

import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

import info.guardianproject.netcipher.client.StrongBuilder;
import info.guardianproject.netcipher.client.StrongConnectionBuilder;

/**
 * Created by Amos on 2017-03-09.
 */

public class Utils {
    public static final String APP_LABEL = "FollowMe2";
    //public static final String SERVER_DOMAIN = "https://whiteboard-afu.rhcloud.com";
    public static final String SERVER_DOMAIN = "http://10.0.2.2:8080";

    public static KeyPairGenerator KEY_PAIR_GENERATOR;
    public static KeyFactory KEY_FACTORY;

    public static boolean isTorEnabled = false;
    public static CountDownLatch torLock = new CountDownLatch(1);

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

    private synchronized static HttpURLConnection getHttpsURLConnection(final URL url, Activity activity) throws Exception {
        if (isTorEnabled) {
            final SynchronousQueue<Object> connectionQueue = new SynchronousQueue<>();
            final StrongConnectionBuilder builder = StrongConnectionBuilder.forMaxSecurity(activity).connectTo(url);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    builder.build(new StrongBuilder.Callback<HttpURLConnection>() {
                        @Override
                        public void onConnected(HttpURLConnection httpURLConnection) {
                            try {
                                connectionQueue.put(httpURLConnection);
                                Log.i(APP_LABEL, "Created HttpsUrlConnection through Tor network");
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConnectionException(Exception e) {
                            Log.i(APP_LABEL, "exception", e);
                            try {
                                connectionQueue.put(new Object());
                            } catch (Exception ex) {
                                Log.i(APP_LABEL, "exception", ex);
                            }
                        }

                        @Override
                        public void onTimeout() {
                            Log.i(APP_LABEL, "Establishing HttpsUrlConnection timed-out!");
                            try {
                                connectionQueue.put(new Object());
                            } catch (Exception e) {
                                Log.i(APP_LABEL, "exception", e);
                            }
                        }

                        @Override
                        public void onInvalid() {
                            Log.i(APP_LABEL, "StrongBuilder.Callback<HttpURLConnection>.onInvalid() called!");
                            try {
                                connectionQueue.put(new Object());
                            } catch (Exception e) {
                                Log.i(APP_LABEL, "exception", e);
                            }
                        }
                    });
                }
            }).start();

            return (HttpURLConnection) connectionQueue.take();
        } else {
            return (HttpURLConnection) url.openConnection();
        }
    }


    public static byte[] encryptJsonObject(Object input, byte[] key) {
        byte[] encrypted = null;
        byte[] json = encodeObjectToJson(input);
        Log.e(APP_LABEL, "Encrypting Json : " + json);
        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA-256");
            // generate 256bit AES key
            byte[] digestedKey = sha3.digest(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(shortenSecretKey(digestedKey, 128), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            encrypted = cipher.doFinal(json);
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

            byte[] json = cipher.doFinal(input);
            decryptedObject = decodeJsonToObject(json, outputClass);
        } catch (Exception e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return decryptedObject;
    }

    public static byte[] sendByteArrAsFileViaHTTP(byte[] payload, CookieManager cookieManager, String url, String attachmentName, Activity activity) {
        byte[] response = null;
        try {
            ContentBody contentPart = new ByteArrayBody(payload, attachmentName);
            MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            reqEntity.addPart(attachmentName, contentPart);

            URL httpsUrl = new URL(url);
            HttpURLConnection conn = getHttpsURLConnection(httpsUrl, activity);
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

    public static byte[] encodeObjectToJson(Object input) {
        byte[] json = new byte[]{};
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            json = ow.writeValueAsBytes(input);
        } catch (JsonProcessingException e) {
            Log.e(APP_LABEL, "exception", e);
            e.printStackTrace();
        }
        return json;
    }

    public static <T> T decodeJsonToObject(byte[] input, Class<T> outputClass) {
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

    public static byte[] sendHTTPSWithNameValuePair(String url, List<NameValuePair> nameValuePair, CookieManager cookieManager, Activity activity) {
        byte[] response = null;
        // Url Encoding the POST parameters
        try {
            URL httpsUrl = new URL(url);
            HttpURLConnection conn = getHttpsURLConnection(httpsUrl, activity);
 //           conn.setReadTimeout(10000);
 //           conn.setConnectTimeout(15000);
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

            if (conn.getResponseCode() == HttpsURLConnection.HTTP_OK) {
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

    //test
    public static void main(String[] args) {
        String name = "amos";
        byte[] arr1 = Base64.decode(name, Base64.DEFAULT);
       String name2 =  Base64.encodeToString(arr1,Base64.DEFAULT);
        System.out.println(name2);
    }
}
