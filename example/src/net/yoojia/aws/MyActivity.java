package net.yoojia.aws;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.codebutler.websocket.WSCallback;
import com.codebutler.websocket.WebSocket;
import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class MyActivity extends Activity {
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
