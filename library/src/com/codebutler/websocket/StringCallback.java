package com.codebutler.websocket;

/**
 * User: chenyoca (桥下一粒砂)
 * Time: 2013-09-18
 * 屏蔽 onMessage(byte[] data) 方法，只处理文本数据结果。
 */
public abstract class StringCallback implements WSCallback {

    @Override
    final public void onMessage (byte[] data) { }

}
