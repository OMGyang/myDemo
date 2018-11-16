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
    private static final String SET_WITH_EXPIRE_TIME = "EX";//EX���룬PX�Ǻ���

    /**
     * ���Ի�ȡ�ֲ�ʽ��
     * @param jedis Redis�ͻ���
     * @param lockKey ��
     * @param requestId �����ʶNX
     * @param expireTime ����ʱ��
     * @return �Ƿ��ȡ�ɹ�
     * ��һ��Ϊkey������ʹ��key����������Ϊkey��Ψһ�ġ�
		�ڶ���Ϊvalue�����Ǵ�����requestId���ܶ�ͯЬ���ܲ����ף���key��Ϊ�����͹�����Ϊʲô��Ҫ�õ�value��ԭ��������������潲���ɿ���ʱ���ֲ�ʽ��Ҫ������ĸ��������廹��ϵ���ˣ�ͨ����value��ֵΪrequestId�����Ǿ�֪����������ĸ�����ӵ��ˣ��ڽ�����ʱ��Ϳ��������ݡ�requestId����ʹ��UUID.randomUUID().toString()�������ɡ�
		������Ϊnxxx������������������NX����˼��SET IF NOT EXIST������key������ʱ�����ǽ���set��������key�Ѿ����ڣ������κβ�����
		���ĸ�Ϊexpx������������Ǵ�����PX����˼������Ҫ�����key��һ�����ڵ����ã�����ʱ���ɵ��������������
		�����Ϊtime������ĸ��������Ӧ������key�Ĺ���ʱ�䡣
		�ܵ���˵��ִ�������set()������ֻ�ᵼ�����ֽ����1. ��ǰû������key�����ڣ�����ô�ͽ��м������������������ø���Ч�ڣ�ͬʱvalue��ʾ�����Ŀͻ��ˡ�2. ���������ڣ������κβ�����
     */
    public synchronized static boolean tryGetDistributedLock(Jedis jedis, String lockKey, String requestId, int expireTime) {
    	//���ȣ�set()������NX���������Ա�֤�������key���ڣ�����������óɹ���Ҳ����ֻ��һ���ͻ����ܳ����������㻥���ԡ�
    	//��Σ��������Ƕ��������˹���ʱ�䣬��ʹ���ĳ����ߺ�������������û�н�������Ҳ����Ϊ���˹���ʱ����Զ���������key��ɾ���������ᷢ��������
    	//�����Ϊ���ǽ�value��ֵΪrequestId����������Ŀͻ��������ʶ����ô�ڿͻ����ڽ�����ʱ��Ϳ��Խ���У���Ƿ���ͬһ���ͻ��ˡ�
    	//��������ֻ����Redis��������ĳ����������ݴ��������ݲ����ǡ�
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
     * �ͷŷֲ�ʽ��
     * @param jedis Redis�ͻ���
     * @param lockKey ��
     * @param requestId �����ʶ
     * @return �Ƿ��ͷųɹ�
     */
    public static boolean releaseDistributedLock(Jedis jedis, String lockKey, String requestId) {
    	//����д��һ���򵥵�Lua�ű�����
    	//Lua:���Ȼ�ȡ����Ӧ��valueֵ������Ƿ���requestId��ȣ���������ɾ��������������
    	//��ôΪʲôҪʹ��Lua������ʵ���أ���ΪҪȷ������������ԭ���Եġ�
    	//����˵��������eval����ִ��Lua�����ʱ��Lua���뽫������һ������ȥִ�У�����ֱ��eval����ִ����ɣ�Redis�Ż�ִ����������
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        //��Lua���봫��jedis.eval()�������ʹ����KEYS[1]��ֵΪlockKey��ARGV[1]��ֵΪrequestId��eval()�����ǽ�Lua���뽻��Redis�����ִ�С�
        Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));
        if (RELEASE_SUCCESS.equals(result)) {
            return true;
        }
        System.out.println("�ͷ���ʧ��:"+result);
        return false;

    }
    
    //����
    public static void main(String[] args) throws InterruptedException {
    	//���ӱ��ص� Redis ����
        Jedis jedis = new Jedis("127.0.0.1");
        System.out.println("���ӳɹ�");
        //�鿴�����Ƿ�����
        System.out.println("������������: "+jedis.ping());
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
        			System.out.println(name + ": ������");
        			Thread.sleep(5000L);
        			break;
        		} else {
        			System.out.println(name + ": δ������,�ȴ����ٳ��Ի�ȡ");
        			Thread.sleep(1000);
        		}
        	}
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(releaseDistributedLock(jedis2,lockKey,requestId)) {
                	System.out.println(name + ": �ͷ��� lock");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
}