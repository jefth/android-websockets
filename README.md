# WebSocket client for Android

在Android平台上实现的，最轻量，最简单的Web Socket客户端。

## Credits

The hybi parser is based on code from the [faye project](https://github.com/faye/faye-websocket-node). Faye is Copyright (c) 2009-2012 James Coglan. Many thanks for the great open-source library!

Ported from JavaScript to Java by [Eric Butler](https://twitter.com/codebutler) <eric@codebutler.com>.

## Usage

Here's the entire API:

```java
public void testMain() throws InterruptedException {
        List<BasicNameValuePair> extraHeaders = Arrays.asList(
                new BasicNameValuePair("Cookie", "session=abcd")
        );
        WebSocket client = new WebSocket(
                URI.create("ws://192.168.1.248:8889/websocket"), new WSCallback() {
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
        }, extraHeaders);

        client.connect();

        client.send("hello!");

        Thread.sleep(3*60*1000);

        client.disconnect();
    }
```

## TODO

* Run [autobahn tests](http://autobahn.ws/testsuite)
* Investigate using [naga](http://code.google.com/p/naga/) instead of threads.

## License

(The MIT License)
	
	Copyright (c) 2009-2012 James Coglan
	Copyright (c) 2012 Eric Butler 
	
	Permission is hereby granted, free of charge, to any person obtaining a copy of
	this software and associated documentation files (the 'Software'), to deal in
	the Software without restriction, including without limitation the rights to use,
	copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
	Software, and to permit persons to whom the Software is furnished to do so,
	subject to the following conditions:
	
	The above copyright notice and this permission notice shall be included in all
	copies or substantial portions of the Software.
	
	THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
	FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
	COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
	IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
	CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
	 
