package org.purejava.connection;

import com.iwebpp.crypto.TweetNaclFast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.purejava.KeepassProxyAccessException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Establishes a connection to KeePassXC via its build-in proxy.
 * .connect() and .associate() need to ba called to create the connection.
 */
public abstract class Connection implements AutoCloseable {

    private TweetNaclFast.Box box;
    private TweetNaclFast.Box.KeyPair keyPair;
    private TweetNaclFast.Box.KeyPair idKeyPair;
    private String clientID;
    private Map<String, Object> map;
    private byte[] nonce;
    private String associate_id;
    protected final String PROXY_NAME = "org.keepassxc.KeePassXC.BrowserServer";

    public Connection() {
        keyPair = TweetNaclFast.Box.keyPair();
        byte[] array = new byte[24];
        new Random().nextBytes(array);
        clientID = b64encode(array);
        nonce = TweetNaclFast.randombytes(24);
    }

    public abstract void connect() throws IOException, KeepassProxyAccessException;

    /**
     * Send an unencrypted message to the proxy.
     * The proxy accepts messages in the JSON data format.
     *
     * @param msg The message to be sent. The key "action" describes the request to the proxy.
     * @throws IOException Sending failed due to technical reasons.
     */
    protected abstract void sendCleartextMessage(String msg) throws IOException;

    /**
     * Receive an unencrypted message from the proxy.
     * The proxy sends messages in the JSON data format.
     *
     * @return The received message.
     * @throws IOException Retrieving failed due to technical reasons.
     */
    protected abstract JSONObject getCleartextResponse() throws IOException;

    /**
     * Send an encrypted message to the proxy.
     * The proxy accepts messages in the JSON data format.
     *
     * @param msg The message to be sent. The key "action" describes the request to the proxy.
     * @throws IOException Sending failed due to technical reasons.
     */
    private void sendEncryptedMessage(Map<String, Object> msg) throws IOException {
        String strMsg = jsonTxt(msg);
        String encrypted = b64encode(box.box(strMsg.getBytes(), nonce));

        map = new HashMap<>();
        map.put("action", msg.get("action").toString());
        map.put("message", encrypted);
        map.put("nonce", b64encode(nonce));
        map.put("clientID", clientID);

        sendCleartextMessage(jsonTxt(map));
        incrementNonce();

    }

    /**
     * Receive an encrypted message from the proxy.
     * The proxy sends messages in the JSON data format.
     *
     * @return The received message.
     * @throws IOException Retrieving failed due to technical reasons.
     * @throws KeepassProxyAccessException It was impossible to process the requested action.
     */
    private JSONObject getEncryptedResponse() throws IOException, KeepassProxyAccessException {
        JSONObject response = getCleartextResponse();

        if (response.has("error")) {
            throw new KeepassProxyAccessException(response.getString("error"));
        }

        byte[] serverNonce = b64decode(response.getString("nonce").getBytes());
        byte[] bMessage = box.open(b64decode(response.getString("message").getBytes()), serverNonce);

        String decrypted = new String(bMessage, StandardCharsets.UTF_8);
        JSONObject decryptedResponse = new JSONObject(decrypted);

        if (!decryptedResponse.has("success")) {
            throw new KeepassProxyAccessException(response.getString("error"));
        }

        return decryptedResponse;
    }

    /**
     * This initially exchanges public keys between KeepassXC and this application.
     *
     * @throws IOException Connection to the proxy failed due to technical reasons.
     * @throws KeepassProxyAccessException It was impossible to exchange new public keys with the proxy.
     */
    protected void changePublibKeys() throws IOException, KeepassProxyAccessException {
        // Send change-public-keys request
        map = new HashMap<>();
        map.put("action", "change-public-keys");
        map.put("publicKey", b64encode(keyPair.getPublicKey()));
        map.put("nonce", b64encode(nonce));
        map.put("clientID", clientID);

        sendCleartextMessage(jsonTxt(map));
        JSONObject response = getCleartextResponse();

        if (!response.has("success")) {
            throw new KeepassProxyAccessException(response.getString("error"));
        }

        // Store box for further communication
        box = new TweetNaclFast.Box(b64decode(response.getString("publicKey").getBytes()), keyPair.getSecretKey());
        incrementNonce();

    }

    /**
     * Connects KeePassXC with a new client.
     *
     * @throws IOException Connecting KeePassXC with a new client failed due to technical reasons.
     * @throws KeepassProxyAccessException It was impossible to associate KeePassXC with a new client.
     */
    public void associate() throws IOException, KeepassProxyAccessException {
        idKeyPair = TweetNaclFast.Box.keyPair();

        // Send associate request
        map = new HashMap<>();
        map.put("action", "associate");
        map.put("key", b64encode(keyPair.getPublicKey()));
        map.put("idKey", b64encode(idKeyPair.getPublicKey()));

        sendEncryptedMessage(map);
        JSONObject response = getEncryptedResponse();

        associate_id = response.getString("id");
    }

    /**
     * Request for receiving the database hash (SHA256) of the current active KeePassXC database.
     *
     * @return The database hash of the current active KeePassXC database.
     * @throws IOException Retrieving the hash failed due to technical reasons.
     * @throws KeepassProxyAccessException It was impossible to get the hash.
     */
    public String getDatabasehash() throws IOException, KeepassProxyAccessException {
        // Send get-databasehash request
        map = new HashMap<>();
        map.put("action", "get-databasehash");

        sendEncryptedMessage(map);
        JSONObject response = getEncryptedResponse();

        return response.getString("hash");
    }

    /**
     * Request for testing if this client has been associated with KeePassXC.
     * The test is positive when no exception is thrown.
     *
     * @throws IOException Testing failed due to technical reasons.
     * @throws KeepassProxyAccessException It was impossible to perform the test.
     */
    public void testAssociate() throws IOException, KeepassProxyAccessException {
        // Send test-associate request
        map = new HashMap<>();
        map.put("action", "test-associate");
        map.put("id", associate_id);
        map.put("key", b64encode(idKeyPair.getPublicKey()));

        sendEncryptedMessage(map);
        JSONObject response = getEncryptedResponse();

    }

    /**
     * Request credentials from KeePassXC databases for a given URL.
     *
     * @param url The URL credentials are looked up for.
     * @param submitUrl URL that can be passed along amd gets added to entry properties.
     * @param httpAuth Include database entries into search that are restricted to HTTP Basic Auth.
     * @param list Id / key combinations identifying and granting access to KeePassXC databases.
     * @return An object that contains all found credentials together with additional information.
     * @throws IOException Requesting credentials failed due to technical reasons.
     * @throws KeepassProxyAccessException No credentials found for the given URL.
     */
    public JSONObject getLogins(String url, String submitUrl, boolean httpAuth, List<Map<String, String>> list) throws IOException, KeepassProxyAccessException {
        JSONArray array = new JSONArray();
        // Syntax check for list
        for (Map<String, String> m : list) {
            JSONObject o = new JSONObject(m);
            if (!(o.has("id") && o.has("key") && o.length() == 2)) {
                throw new KeepassProxyAccessException("JSON object key is malformed");
            }
            array.put(m);
        }

        // Send get-logins
        map = new HashMap<>();
        map.put("action", "get-logins");
        map.put("url", url);
        map.put("submitUrl", submitUrl);
        map.put("httpAuth", httpAuth);
        map.put("keys", array);

        sendEncryptedMessage(map);
        return getEncryptedResponse();

    }

    /**
     * Get a String representation of the JSON object.
     *
     * @param keysValues The keys/values defining the JSON object.
     * @return String representation of the JSON object.
     */
    private String jsonTxt(Map<String, Object> keysValues) {
        return new JSONObject(keysValues).toString();
    }

    /**
     * Increment nonce by 1
     */
    private void incrementNonce() {
        int newNonce = ByteBuffer.wrap(nonce).getInt() + 1;
        ByteBuffer dbuf = ByteBuffer.allocate(24).putInt(newNonce);
        nonce = dbuf.array();
    }

    /**
     * Base64 encode array of bytes and wrap as a String.
     *
     * @param bytes The data to be ecoded.
     * @return Base64 encoded String.
     */
    private String b64encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Base64 decode array of bytes.
     *
     * @param bytes The data to be decoded.
     * @return Base64 decoded data.
     */
    private byte[] b64decode(byte[] bytes) {
        return Base64.getDecoder().decode(bytes);
    }

    // Getters
    public String getIdKeyPairPublicKey() {
        return b64encode(idKeyPair.getPublicKey());
    }

    public String getAssociate_id() {
        return associate_id;
    }

    @Override
    public abstract void close() throws Exception;
}
