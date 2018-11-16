package com.ly.lock.redis;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import redis.clients.jedis.Jedis;

public class RedisTool {

    private static final String LOCK_SUCCESS = "OK";
    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "EX";//EX是秒，PX是毫秒

    /**
     * 尝试获取分布式锁
     * @param jedis Redis客户端
     * @param lockKey 锁
     * @param requestId 请求标识NX
     * @param expireTime 超期时间
     * @return 是否获取成功
     * 第一个为key，我们使用key来当锁，因为key是唯一的。
		第二个为value，我们传的是requestId，很多童鞋可能不明白，有key作为锁不就够了吗，为什么还要用到value？原因就是我们在上面讲到可靠性时，分布式锁要满足第四个条件解铃还须系铃人，通过给value赋值为requestId，我们就知道这把锁是哪个请求加的了，在解锁的时候就可以有依据。requestId可以使用UUID.randomUUID().toString()方法生成。
		第三个为nxxx，这个参数我们填的是NX，意思是SET IF NOT EXIST，即当key不存在时，我们进行set操作；若key已经存在，则不做任何操作；
		第四个为expx，这个参数我们传的是PX，意思是我们要给这个key加一个过期的设置，具体时间由第五个参数决定。
		第五个为time，与第四个参数相呼应，代表key的过期时间。
		总的来说，执行上面的set()方法就只会导致两种结果：1. 当前没有锁（key不存在），那么就进行加锁操作，并对锁设置个有效期，同时value表示加锁的客户端。2. 已有锁存在，不做任何操作。
     */
    public synchronized static boolean tryGetDistributedLock(Jedis jedis, String lockKey, String requestId, int expireTime) {
    	//首先，set()加入了NX参数，可以保证如果已有key存在，则函数不会调用成功，也就是只有一个客户端能持有锁，满足互斥性。
    	//其次，由于我们对锁设置了过期时间，即使锁的持有者后续发生崩溃而没有解锁，锁也会因为到了过期时间而自动解锁（即key被删除），不会发生死锁。
    	//最后，因为我们将value赋值为requestId，代表加锁的客户端请求标识，那么在客户端在解锁的时候就可以进行校验是否是同一个客户端。
    	//由于我们只考虑Redis单机部署的场景，所以容错性我们暂不考虑。
        try {
			String result = jedis.set(lockKey, requestId, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
			if (LOCK_SUCCESS.equals(result)) {
			    return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
        return false;

    }

    private static final Long RELEASE_SUCCESS = 1L;

    /**
     * 释放分布式锁
     * @param jedis Redis客户端
     * @param lockKey 锁
     * @param requestId 请求标识
     * @return 是否释放成功
     */
    public static boolean releaseDistributedLock(Jedis jedis, String lockKey, String requestId) {
    	//我们写了一个简单的Lua脚本代码
    	//Lua:首先获取锁对应的value值，检查是否与requestId相等，如果相等则删除锁（解锁）。
    	//那么为什么要使用Lua语言来实现呢？因为要确保上述操作是原子性的。
    	//简单来说，就是在eval命令执行Lua代码的时候，Lua代码将被当成一个命令去执行，并且直到eval命令执行完成，Redis才会执行其他命令
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        //将Lua代码传到jedis.eval()方法里，并使参数KEYS[1]赋值为lockKey，ARGV[1]赋值为requestId。eval()方法是将Lua代码交给Redis服务端执行。
        Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));
        if (RELEASE_SUCCESS.equals(result)) {
            return true;
        }
        System.out.println("释放锁失败:"+result);
        return false;

    }
    
    //测试
    public static void main(String[] args) throws InterruptedException {
    	//连接本地的 Redis 服务
        Jedis jedis = new Jedis("127.0.0.1");
        System.out.println("连接成功");
        //查看服务是否运行
        System.out.println("服务正在运行: "+jedis.ping());
        final String lockKey = "lock";
        for (int i = 0; i < 5; i++) {
			new Thread(() -> { 
    			doWithLock(jedis,lockKey); 
    		}, "Thread-" + i++).start();
		}
//        jedis.close();
    }
    
    private static void doWithLock(Jedis jedis,String lockKey) {
    	String name = Thread.currentThread().getName();
    	UUID uuid = UUID.randomUUID();
    	String requestId = uuid.toString();
    	Jedis jedis2 = new Jedis("127.0.0.1");
        try {
        	while(true) {
        		if (tryGetDistributedLock(jedis2,lockKey,requestId,15)) {
        			System.out.println(name + ": 持有锁");
        			Thread.sleep(5000L);
        			break;
        		} else {
        			System.out.println(name + ": 未持有锁,等待后再尝试获取");
        			Thread.sleep(1000);
        		}
        	}
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(releaseDistributedLock(jedis2,lockKey,requestId)) {
                	System.out.println(name + ": 释放锁 lock");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
}