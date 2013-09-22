package com.codebutler.websocket;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import org.apache.http.*;
import org.apache.http.client.HttpResponseException;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.BasicNameValuePair;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 单线程WebSocket客户端
 */
public class WebSocket {
    private static final String TAG = "WebSocket";
    private URI uri;
    private WSCallback wsCallback;
    private Socket socket;
    private Thread workThread;
    private HandlerThread handlerThread;
    private Handler handler;
    private List<BasicNameValuePair> extraHeaders;
    private HybiParser parser;
    private final Object sendLock = new Object();

    private static TrustManager[] trustManagers;

    /**
     * 这是用于 JSSE 信任管理器的基接口。
     * TrustManager 负责管理做出信任决定时使用的的信任材料，也负责决定是否接受同位体提供的证书。
     * 通过使用 TrustManagerFactory，或实现 TrustManager 子类之一创建 TrustManager。
     * @param tm 信任管理器的接口
     */
    public static void setTrustManagers(TrustManager[] tm) {
        trustManagers = tm;
    }

    public WebSocket(String wsUrl,WSCallback wsCallback){
        this(URI.create(wsUrl),wsCallback,null);
    }

    public WebSocket(String wsUrl,WSCallback wsCallback,List<BasicNameValuePair> extraHeaders){
        this(URI.create(wsUrl),wsCallback,extraHeaders);
    }

    public WebSocket(URI uri, WSCallback WSCallback, List<BasicNameValuePair> extraHeaders) {
        this.uri = uri;
        wsCallback = WSCallback;
        this.extraHeaders = extraHeaders;
        parser = new HybiParser(this);
        handlerThread = new HandlerThread("websocket-thread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    /**
     * 设置WebSocket Header数据
     * @param extraHeaders  Header数据
     */
    public void setExtraHeaders (List<BasicNameValuePair> extraHeaders) {
        this.extraHeaders = extraHeaders;
    }

    /**
     * 返回WebSocket的连接状态
     * @return 当WebSocket为连接状态为true，否则为false。
     */
    public boolean isConnected(){
        return workThread != null && workThread.isAlive();
    }

    /**
     * 连接，如果WebSocket已经连接，则不做操作。
     */
    public void connect() {
        if (isConnected()) {
            return;
        }
        workThread = new Thread(buildConnection());
        workThread.start();
    }

    Runnable buildConnection(){
        return
        new Runnable() {
            @Override
            public void run() {
                try {
                    String scheme = uri.getScheme();
                    String path = uri.getPath();
                    final boolean isSSL = scheme.equalsIgnoreCase("wss");
                    int port = (uri.getPort() != -1) ? uri.getPort() : (isSSL ? 443 : 80);

                    path = TextUtils.isEmpty(path) ? "/" : path;
                    if (!TextUtils.isEmpty(uri.getQuery())) {
                        path += "?" + uri.getQuery();
                    }

                    String originScheme = isSSL ? "https" : "http";
                    URI origin = new URI(originScheme, "//" + uri.getHost(), null);

                    SocketFactory factory = isSSL ?
                                                    getSSLSocketFactory() :  SocketFactory.getDefault();
                    socket = factory.createSocket(uri.getHost(), port);
                    String secret = createSecret();

                    PrintWriter out = new PrintWriter(socket.getOutputStream());
                    out.print("GET " + path + " HTTP/1.1\r\n");
                    out.print("Upgrade: websocket\r\n");
                    out.print("Connection: Upgrade\r\n");
                    out.print("Host: " + uri.getHost() + "\r\n");
                    out.print("Origin: " + origin.toString() + "\r\n");
                    out.print("Sec-WebSocket-Key: " + secret + "\r\n");
                    out.print("Sec-WebSocket-Version: 13\r\n");
                    if (extraHeaders != null) {
                        for (NameValuePair pair : extraHeaders) {
                            out.print(String.format("%s: %s\r\n", pair.getName(), pair.getValue()));
                        }
                    }
                    out.print("\r\n");
                    out.flush();

                    HybiParser.HappyDataInputStream stream =
                            new HybiParser.HappyDataInputStream(socket.getInputStream());

                    // Read HTTP response status line.
                    StatusLine statusLine = parseStatusLine(readLine(stream));
                    if (statusLine == null) {
                        throw new HttpException("Received no reply from server.");
                    } else if (statusLine.getStatusCode() != HttpStatus.SC_SWITCHING_PROTOCOLS) {
                        throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
                    }

                    // Read HTTP response headers.
                    String line;
                    boolean validated = false;

                    while (!TextUtils.isEmpty(line = readLine(stream))) {
                        Header header = parseHeader(line);
                        if (header.getName().equals("Sec-WebSocket-Accept")) {
                            String expected = createSecretValidation(secret);
                            String actual = header.getValue().trim();

                            if (!expected.equals(actual)) {
                                throw new HttpException("Bad Sec-WebSocket-Accept header value.");
                            }

                            validated = true;
                        }
                    }

                    if (!validated) {
                        throw new HttpException("No Sec-WebSocket-Accept header.");
                    }

                    wsCallback.onConnect();

                    // Now decode websocket frames.
                    parser.start(stream);

                } catch (EOFException ex) {
                    Log.d(TAG, "WebSocket EOF!", ex);
                    wsCallback.onDisconnect(0, "EOF");

                } catch (SSLException ex) {
                    // Connection reset by peer
                    Log.d(TAG, "Websocket SSL error!", ex);
                    wsCallback.onDisconnect(0, "SSL");

                } catch (Exception ex) {
                    wsCallback.onError(ex);
                }
            }
        };
    }

    /**
     * 主动断开WebSocket连接
     */
    public void disconnect() {
        if (socket == null) return;
        handler.post(new Runnable() {
            @Override
            public void run () {
                try {
                    socket.close();
                    socket = null;
                } catch (IOException ex) {
                    Log.d(TAG, "Error while disconnecting", ex);
                    wsCallback.onError(ex);
                }
            }
        });
    }

    /**
     * 发送文本数据
     * @param data 文本数据
     */
    public void send(String data) {
        sendFrame(parser.frame(data));
    }

    /**
     * 发送字节数据
     * @param data 字节数据
     */
    public void send(byte[] data) {
        sendFrame(parser.frame(data));
    }

    WSCallback getCallback () {
        return wsCallback;
    }

    private StatusLine parseStatusLine(String line) {
        if (TextUtils.isEmpty(line)) {
            return null;
        }
        return BasicLineParser.parseStatusLine(line, new BasicLineParser());
    }

    private Header parseHeader(String line) {
        return BasicLineParser.parseHeader(line, new BasicLineParser());
    }

    // Can't use BufferedReader because it buffers past the HTTP data.
    private String readLine(HybiParser.HappyDataInputStream reader) throws IOException {
        int readChar = reader.read();
        if (readChar == -1) {
            return null;
        }
        StringBuilder string = new StringBuilder("");
        while (readChar != '\n') {
            if (readChar != '\r') {
                string.append((char) readChar);
            }
            readChar = reader.read();
            if (readChar == -1) {
                return null;
            }
        }
        return string.toString();
    }

    private String createSecret() {
        byte[] nonce = new byte[16];
        for (int i = 0; i < 16; i++) {
            nonce[i] = (byte) (Math.random() * 256);
        }
        return Base64.encodeToString(nonce, Base64.DEFAULT).trim();
    }

    private String createSecretValidation(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((secret + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes());
            return Base64.encodeToString(md.digest(), Base64.DEFAULT).trim();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean verifyScheme(String scheme){
        if( scheme == null ) return false;
        return scheme.equalsIgnoreCase("ws") || scheme.equalsIgnoreCase("wss");
    }

    void sendFrame(final byte[] frame) {
        handler.post(new Runnable() {
            @Override
            public void run () {
                try {
                    synchronized (sendLock) {
                        if (socket == null) {
                            throw new IllegalStateException("Socket not connected");
                        }
                        OutputStream outputStream = socket.getOutputStream();
                        outputStream.write(frame);
                        outputStream.flush();
                    }
                } catch (Exception e) {
                    wsCallback.onError(e);
                }
            }
        });
    }

    private SSLSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, null);
        return context.getSocketFactory();
    }
}
