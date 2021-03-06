package net.yoojia.aws;

import android.util.Log;
import com.codebutler.websocket.WSCallback;
import com.codebutler.websocket.WebSocket;
import junit.framework.TestCase;
import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class Test extends TestCase{

    public static final String TAG = "TEST";

    public void testMain() throws InterruptedException {
        List<BasicNameValuePair> extraHeaders = Arrays.asList(
                new BasicNameValuePair("Cookie", "session=abcd")
        );
        WebSocket client = new WebSocket(
                URI.create("ws://192.168.1.105:8887"), new WSCallback() {
            @Override
            public void onConnect() {
                Log.d(TAG, "连接成功!");
            }

            @Override
            public void onMessage(String message) {
                Log.d(TAG, String.format("返回数据（字符）:\n %s", message));
            }

            @Override
            public void onMessage(byte[] data) {
                Log.d(TAG, String.format("返回数据（字节）:\n %s", String.valueOf(data)));
            }

            @Override
            public void onDisconnect(int code, String reason) {
                Log.d(TAG, String.format("连接被断开! Code: %d 原因: %s", code, reason));
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "发生错误：", error);
            }
        }, null);

        client.connect();

        client.send("hello!");

        Thread.sleep(3*60*1000);

        client.disconnect();
    }
}
