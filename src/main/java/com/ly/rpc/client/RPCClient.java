package com.ly.rpc.client;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

public class RPCClient<T> {
    public static <T> T getRemoteProxyObj(final Class<?> serviceInterface, final InetSocketAddress addr) {
        // 1.将本地的接口调用转换成JDK的动态代理，在动态代理中实现接口的远程调�?
//    	RPCClientJDKProxy clientProxy = new RPCClientJDKProxy(serviceInterface,addr);
//    	Object object = Proxy.newProxyInstance(serviceInterface.getClassLoader(),new Class<?>[]{serviceInterface},clientProxy);
    	
    	RPCClientCGLIBProxy clientProxy = new RPCClientCGLIBProxy(serviceInterface,addr);
    	Object object = clientProxy.getProxy(serviceInterface);
        return (T) object;
    }
}