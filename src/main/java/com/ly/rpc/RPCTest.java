package com.ly.rpc;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.ly.rpc.client.RPCClient;
import com.ly.rpc.server.Server;
import com.ly.rpc.server.ServiceCenter;
import com.ly.rpc.service.HelloService;
import com.ly.rpc.service.HelloServiceImpl;

public class RPCTest {
 
    public static void main(String[] args) throws IOException {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Server serviceServer = new ServiceCenter(8088);
                    serviceServer.register(HelloService.class, HelloServiceImpl.class);
                    serviceServer.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        //实际上是通过
        HelloService service = RPCClient.getRemoteProxyObj(HelloService.class, new InetSocketAddress("localhost", 8088));
        System.out.println(service.sayHi("test"));
        System.out.println(service.sayHi2("test"));
    }
}