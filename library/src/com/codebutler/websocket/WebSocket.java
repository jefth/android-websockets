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

    private URI                      mURI;
    private SocketCallback mSocketCallback;
    private Socket                  mSocket;
    private Thread                   mThread;
    private HandlerThread            mHandlerThread;
    private Handler                  mHandler;
    private List<BasicNameValuePair> mExtraHeaders;
    private HybiParser               mParser;

    private final Object mSendLock = new Object();

    private static TrustManager[] sTrustManagers;

    /**
     * 创建WebSocket对象
     * @param wsUrl 连接路径
     * @param socketCallback 响应回调接口
     */
    public WebSocket(String wsUrl,SocketCallback socketCallback){
        this(URI.create(wsUrl),socketCallback);
    }

    /**
     * 创建WebSocket对象
     * @param uri 连接路径对象
     * @param socketCallback 响应回调接口
     */
    public WebSocket(URI uri, SocketCallback socketCallback) {
        mURI          = uri;
        mSocketCallback = socketCallback;
        mParser       = new HybiParser(this);
        mHandlerThread = new HandlerThread("websocket-thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public static void setTrustManagers(TrustManager[] tm) {
        sTrustManagers = tm;
    }


    public void setExtraHeaders (List<BasicNameValuePair> mExtraHeaders) {
        this.mExtraHeaders = mExtraHeaders;
    }

    /**
     * 获取Socket回调响应接口
     * @return 回调响应接口
     */
    public SocketCallback getSocketCallback() {
        return mSocketCallback;
    }

    /**
     * 建立连接。如果此前已经创建连接并且该连接仍然有效，则不会执行连接操作。
     */
    public void connect() {
        if (mThread != null && mThread.isAlive()) {
            return;
        }
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    String scheme = mURI.getScheme();

                    if ( !verifyScheme(scheme) ) new IllegalArgumentException("Illegal scheme: "+scheme);

                    boolean isSSL = scheme.equalsIgnoreCase("wss");
                    String path = mURI.getPath();
                    int port = mURI.getPort();
                    port = (port != -1) ? port : (isSSL ? 443 : 80);

                    path = TextUtils.isEmpty(path) ? "/" : path;
                    if (!TextUtils.isEmpty(mURI.getQuery())) {
                        path += "?" + mURI.getQuery();
                    }

                    SocketFactory factory = isSSL ?
                            getSSLSocketFactory() : SocketFactory.getDefault();
                    mSocket = factory.createSocket(mURI.getHost(), port);

                    String originScheme = isSSL ? "https" : "http";
                    URI origin = new URI(originScheme, "//" + mURI.getHost(), null);
                    String secret = createSecret();

                    PrintWriter handShakeOutput = new PrintWriter(mSocket.getOutputStream());
                    handShakeOutput.print("GET " + path + " HTTP/1.1\r\n");
                    handShakeOutput.print("Upgrade: websocket\r\n");
                    handShakeOutput.print("Connection: Upgrade\r\n");
                    handShakeOutput.print("Host: " + mURI.getHost() + "\r\n");
                    handShakeOutput.print("Origin: " + origin.toString() + "\r\n");
                    handShakeOutput.print("Sec-WebSocket-Key: " + secret + "\r\n");
                    handShakeOutput.print("Sec-WebSocket-Version: 13\r\n");
                    if (mExtraHeaders != null) {
                        for (NameValuePair pair : mExtraHeaders) {
                            handShakeOutput.print(String.format("%s: %s\r\n", pair.getName(), pair.getValue()));
                        }
                    }
                    handShakeOutput.print("\r\n");
                    handShakeOutput.flush();

                    HybiParser.HappyDataInputStream stream =
                            new HybiParser.HappyDataInputStream(mSocket.getInputStream());

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
                            break;
                        }
                    }
                    if (!validated) {
                        throw new HttpException("No Sec-WebSocket-Accept header.");
                    }
                    mSocketCallback.onOpen();
                    // Now decode websocket frames.
                    mParser.start(stream);
                } catch (EOFException ex) {
                    Log.d(TAG, "WebSocket EOF!", ex);
                    mSocketCallback.onClose(0, "EOF");
                } catch (SSLException ex) {
                    // Connection reset by peer
                    Log.d(TAG, "Websocket SSL error!", ex);
                    mSocketCallback.onClose(0, "SSL");
                } catch (Exception ex) {
                    mSocketCallback.onError(ex);
                }
            }
        });
        mThread.start();
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (mSocket != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mSocket.close();
                        mSocket = null;
                    } catch (IOException ex) {
                        Log.d(TAG, "Error while disconnecting", ex);
                        mSocketCallback.onError(ex);
                    }
                }
            });
        }
    }

    /**
     * 发送文本消息到服务端
     * @param data
     */
    public void send(String data) {
        sendFrame(mParser.frame(data));
    }

    /**
     * 发送字节数据到服务端
     * @param data 字节数据
     */
    public void send(byte[] data) {
        sendFrame(mParser.frame(data));
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
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mSendLock) {
                        if (mSocket == null) {
                            throw new IllegalStateException("Socket not connected");
                        }
                        OutputStream outputStream = mSocket.getOutputStream();
                        outputStream.write(frame);
                        outputStream.flush();
                    }
                } catch (Exception e) {
                    mSocketCallback.onError(e);
                }
            }
        });
    }

    private SSLSocketFactory getSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, sTrustManagers, null);
        return context.getSocketFactory();
    }
}
