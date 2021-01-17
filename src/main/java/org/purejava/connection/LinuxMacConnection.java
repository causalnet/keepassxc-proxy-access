package org.purejava.connection;

import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.purejava.KeepassProxyAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class LinuxMacConnection extends Connection {

    static final Logger log = LoggerFactory.getLogger(LinuxMacConnection.class);

    private AFUNIXSocket socket;
    private OutputStream os;
    private InputStream is;
    private String socketPath;
    private final File socketFile;

    public LinuxMacConnection() {
        this.socketPath = getSocketPath();
        this.socketFile = new File(new File(socketPath), "/" + PROXY_NAME);
    }

    /**
     * Connect to the KeePassXC proxy via a Unix Domain Sockets (AF_UNIX)
     * the proxy has opened.
     *
     * @throws IOException Connecting to the proxy failed due to technical reasons or the proxy wasn't started.
     * @throws KeepassProxyAccessException It was impossible to exchange new public keys with the proxy.
     */
    @Override
    public void connect() throws IOException, KeepassProxyAccessException {
        try {
            socket = AFUNIXSocket.newInstance();
            socket.connect(new AFUNIXSocketAddress(socketFile));
            os = socket.getOutputStream();
            is = socket.getInputStream();
        } catch (SocketException e) {
            log.error("Cannot connect to proxy. Is KeepassXC started?");
            throw e;
        }

        changePublibKeys();
    }

    @Override
    protected void sendCleartextMessage(String msg) throws IOException {
        os.write(msg.getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    @Override
    protected JSONObject getCleartextResponse() throws IOException {
        byte[] buf = new byte[4096];
        int read = is.read(buf);
        return new JSONObject(new String(buf, 0, read, StandardCharsets.UTF_8));
    }

    /**
     * Get the os-specific directory, where runtime files and sockets are kept.
     *
     * @return The socket path.
     */
    private String getSocketPath() {
        if (SystemUtils.IS_OS_LINUX) {
            String path = System.getenv("XDG_RUNTIME_DIR");
            return (path == null) ? System.getenv("TMPDIR") : System.getenv("XDG_RUNTIME_DIR");
        }
        if (SystemUtils.IS_OS_MAC_OSX) {
            return System.getenv("TMPDIR");
        } else {
            // unknown OS
            return "-";
        }
    }

    @Override
    public void close() throws Exception {
        is.close();
        os.close();
        socket.close();
    }
}