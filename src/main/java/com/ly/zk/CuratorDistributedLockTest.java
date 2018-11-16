package com.ly.zk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryNTimes;

/**
 * ZooKeeper�ڵ����ͣ� <br>
 * ZooKeeper �ڵ������������ڵģ���ȡ���ڽڵ�����͡�<br>
 * �� ZooKeeper �У��ڵ����Ϳ��Է�Ϊ�־ýڵ�(PERSISTENT)����ʱ�ڵ�(EPHEMERAL)���Լ�ʱ��ڵ�(SEQUENTIAL)<br>
 * �����ڽڵ㴴�������У�һ�������ʹ�ã������������� 4�ֽڵ�����<br>
 *
 * �־ýڵ㣨PERSISTENT�� <br>
 * ��ν�־ýڵ㣬��ָ�ڽڵ㴴���󣬾�һֱ���ڣ�ֱ����ɾ�������������������ڵ㡣 <br>
 * (�ýڵ㲻����Ϊ�����ýڵ�Ŀͻ��˻ỰʧЧ����ʧ)
 *
 * ��ʱ�ڵ㣨EPHEMERAL�� <br>
 * �ͳ־ýڵ㲻ͬ���ǣ���ʱ�ڵ���������ںͿͻ��˻Ự�󶨡�<br>
 * ����ͻ��˻ỰʧЧ����ô����ڵ�ͻ��Զ����������<br>
 * (�����ᵽ���ǻỰʧЧ���������ӶϿ�)<br>
 * ���⣬����ʱ�ڵ����治�ܴ����ӽڵ㡣<br>
 *
 * �־�˳��ڵ㣨PERSISTENT_SEQUENTIAL�� <br>
 * ����ڵ�Ļ������Ժ�����ĳ־ýڵ�������һ�µġ�<br>
 * ����������ǣ���ZK�У�ÿ�����ڵ��Ϊ���ĵ�һ���ӽڵ�ά��һ��ʱ�򣬻��¼ÿ���ӽڵ㴴�����Ⱥ�˳�� <br>
 * ����������ԣ��ڴ����ӽڵ��ʱ�򣬿�������������ԣ���ô�ڴ����ڵ�����У�ZK���Զ�Ϊ�����ڵ�������һ�����ֺ�׺����Ϊ�µĽڵ�����������ֺ�׺�ķ�Χ�����͵����ֵ��<br>
 * ����: ������/lock/Ŀ¼�´�����3���㣬��Ⱥ�ᰴ�����𴴽���˳���������ڵ㣬�ڵ�ֱ�Ϊ/lock/0000000001��/lock/0000000002��/lock/0000000003��
 *
 * ��ʱ˳��ڵ㣨EPHEMERAL_SEQUENTIAL�� <br>
 * ������ʱ�ڵ��˳��ڵ�����ԡ����ǿ����������������ʵ�ֲַ�ʽ���� <br>
 * ����zookeeper˲ʱ����ڵ�ʵ�ֵķֲ�ʽ��������Ҫ�߼����£� <br>
 * �ͻ��˶�ĳ�����ܼ���ʱ����zookeeper�ϵ���ù��ܶ�Ӧ��ָ���ڵ��Ŀ¼�£�����һ��Ψһ��˲ʱ����ڵ㡣 <br>
 * �ж��Ƿ��ȡ���ķ�ʽ��ֻ��Ҫ�ж�����ڵ��������С��һ���������С�Ľڵ��뵱�ͻ��˼�¼�ڵ����ͬ�����<br>
 * ���ͷ�����ʱ��ֻ�轫���˲ʱ�ڵ�ɾ�����ɡ� <br>
 *
 * Curator��Netflix��˾��Դ��һ��Zookeeper�ͻ��ˣ��ṩ��һЩ����Zookeeper�ķ��������а��������ֲ�ʽ�� <br><br> *<br> * @author _yyl
 */
public class CuratorDistributedLockTest {
 
    private static final String ZK_ADDRESS = "127.0.0.1:2181";
    private static final String ZK_LOCK_PATH = "/zktest/lock0";
 
    /**
     * ����ĳ�������������߳�ȥ���������õ������̻߳�ռ��5��
     */
    public static void main(String[] args) throws InterruptedException {
        //����zookeeper�ͻ��� ,���Դ���10��  ����ʱ��Ϊ5s
        CuratorFramework client = CuratorFrameworkFactory.newClient(ZK_ADDRESS, new RetryNTimes(10, 5000));
        client.start();
        System.out.println(client.getState());
 
        System.out.println("zk client start successfully!");
        //ָ������·������������Ϊ�������������ǻ����󣬻������ٴλ�ȡ�������Դ�Ϊ��
        final  InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        //��������Ϊ���������������ǻ����󣬲������ٴλ�ȡ�����ﲻ�����ӣ�ʹ�ú�����������
//      InterProcessSemaphoreMutex lock = new InterProcessSemaphoreMutex(client, lockPath);
        int i = 0;
    	for ( ; ; ) {
    		new Thread(() -> { 
    			doWithLock(client, lock); 
    		}, "Thread-" + i++).start();
    		
    		Thread.sleep(1000L);
    		System.out.println("i =" + i);
    	}
        
    }
 
    private static void doWithLock(CuratorFramework client, InterProcessMutex lock) {
    	String name = Thread.currentThread().getName();
        try {
            if (lock.acquire(3, TimeUnit.SECONDS)) {
 
                System.out.println(name + ": ������");
 
                System.out.println(name+": " +client.getChildren().forPath(ZK_LOCK_PATH));
 
                Thread.sleep(5000L);
                
            }else {
            	System.out.println(name + ": δ������");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                lock.release();
                System.out.println(name + ": �ͷ��� lock");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
 
}