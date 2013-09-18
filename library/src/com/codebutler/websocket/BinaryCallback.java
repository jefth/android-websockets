package com.codebutler.websocket;

/**
 * User: chenyoca (桥下一粒砂)
 * Time: 2013-09-18
 * 屏蔽 onMessage(String data) 方法，只处理二进制数据结果
 */
public abstract class BinaryCallback implements SocketCallback {

    @Override
    final public void onMessage (String data) { }

}
