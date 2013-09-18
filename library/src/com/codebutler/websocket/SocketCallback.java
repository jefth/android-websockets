package com.codebutler.websocket;

public interface SocketCallback {
    /**
     * 建立连接后回调
     */
    public void onOpen ();

    /**
     * 接收到服务端返回文本消息时回调
     * @param message 服务端返回的文本消息
     */
    public void onMessage (String message);

    /**
     * 接收到服务端返回二进制数据时回调
     * @param data 服务端返回的二进制数据
     */
    public void onMessage (byte[] data);

    /**
     * 连接关闭时回调
     * @param code
     * @param reason
     */
    public void onClose (int code, String reason);

    /**
     * 连接出错时回调
     * @param error 异常对象
     */
    public void onError(Exception error);
}