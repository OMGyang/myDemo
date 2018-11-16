package com.ly.rpc.service;

public class HelloServiceImpl implements HelloService {

	public String sayHi(String name) {
		return "Hi, " + name;
	}
	
	public String sayHi2(String name) {
		return "Hi2, " + name;
	}

}