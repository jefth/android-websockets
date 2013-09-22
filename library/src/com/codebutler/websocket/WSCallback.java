package com.codebutler.websocket;

public interface WSCallback {
    /**
     * WebSocket已经连接
     */
    public void onConnect();

    /**
     * 服务端返回文本消息
     * @param message 文本消息
     */
    public void onMessage (String message);

    /**
     * 服务端返回字节消息
     * @param data 字节消息
     */
    public void onMessage (byte[] data);

    /**
     * WebSocket已经断开
     * @param code 断开码
     * @param reason 断开说明
     */
    public void onDisconnect(int code, String reason);

    /**
     * WebSocket连接发生异常
     * @param error 异常对象
     */
    public void onError(Exception error);
}