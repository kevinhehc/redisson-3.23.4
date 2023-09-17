package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.RBucket;
import org.redisson.api.RPatternTopic;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.BasePatternStatusListener;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.balancer.RandomLoadBalancer;

import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonTopicPatternTest extends BaseTest {

    public static class Message implements Serializable {

        private String name;

        public Message() {
        }

        public Message(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Message other = (Message) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Message{" + "name='" + name + '\'' + '}';
        }
    }

    @Test
    public void testCluster() throws IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave().notifyKeyspaceEvents(
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g);
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave().notifyKeyspaceEvents(
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g);
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave().notifyKeyspaceEvents(
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g);
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave().notifyKeyspaceEvents(
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g);
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave().notifyKeyspaceEvents(
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g);
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave().notifyKeyspaceEvents(
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                                    RedisRunner.KEYSPACE_EVENTS_OPTIONS.g);

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Thread.sleep(3000);

        Config config = new Config();
        config.useClusterServers()
                .setPingConnectionInterval(0)
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        AtomicInteger subscribeCounter = new AtomicInteger();
        RPatternTopic topic = redisson.getPatternTopic("__keyevent@*", StringCodec.INSTANCE);
        topic.addListener(new PatternStatusListener() {
            @Override
            public void onPSubscribe(String pattern) {
                subscribeCounter.incrementAndGet();
            }

            @Override
            public void onPUnsubscribe(String pattern) {
                System.out.println("onPUnsubscribe: " + pattern);
            }
        });

        AtomicInteger counter = new AtomicInteger();

        PatternMessageListener<String> listener = (pattern, channel, msg) -> {
            System.out.println("mes " + channel + " counter " + counter.get());
            counter.incrementAndGet();
        };
        topic.addListener(String.class, listener);

        for (int i = 0; i < 10; i++) {
            redisson.getBucket("" + i).set(i);
            redisson.getBucket("" + i).delete();
            Thread.sleep(7);
        }

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> counter.get() > 9);
        assertThat(subscribeCounter.get()).isEqualTo(1);

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testNonEventMessagesInCluster() throws IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        AtomicInteger subscribeCounter = new AtomicInteger();
        RPatternTopic topic = redisson.getPatternTopic("my*", StringCodec.INSTANCE);
        topic.addListener(new PatternStatusListener() {
            @Override
            public void onPSubscribe(String pattern) {
                subscribeCounter.incrementAndGet();
            }

            @Override
            public void onPUnsubscribe(String pattern) {
                System.out.println("onPUnsubscribe: " + pattern);
            }
        });

        AtomicInteger counter = new AtomicInteger();

        PatternMessageListener<String> listener = (pattern, channel, msg) -> {
            counter.incrementAndGet();
        };
        topic.addListener(String.class, listener);

        for (int i = 0; i < 100; i++) {
            redisson.getTopic("my" + i).publish(123);
        }

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> counter.get() == 100);
        assertThat(subscribeCounter.get()).isEqualTo(1);

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testMultiType() throws InterruptedException {
        RPatternTopic topic1 = redisson.getPatternTopic("topic1.*");
        AtomicInteger str = new AtomicInteger(); 
        topic1.addListener(String.class, (pattern, channel, msg) -> {
            str.incrementAndGet();
        });
        AtomicInteger i = new AtomicInteger();
        topic1.addListener(Integer.class, (pattern, channel, msg) -> {
            i.incrementAndGet();
        });
        
        redisson.getTopic("topic1.str").publish("123");
        redisson.getTopic("topic1.int").publish(123);
        
        Thread.sleep(500);
        
        Assertions.assertEquals(1, i.get());
        Assertions.assertEquals(1, str.get());
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RPatternTopic topic1 = redisson.getPatternTopic("topic1.*");
        int listenerId = topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.fail();
        });
        topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.assertTrue(pattern.equals("topic1.*"));
            Assertions.assertTrue(channel.equals("topic1.t3"));
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic1.removeListener(listenerId);

        redisson.getTopic("topic1.t3").publish(new Message("123"));

        Assertions.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testLazyUnsubscribe() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(1);

        RedissonClient redisson1 = BaseTest.createInstance();
        RPatternTopic topic1 = redisson1.getPatternTopic("topic.*");
        int listenerId = topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.fail();
        });

        Thread.sleep(1000);
        topic1.removeListener(listenerId);
        Thread.sleep(1000);

        RedissonClient redisson2 = BaseTest.createInstance();
        RPatternTopic topic2 = redisson2.getPatternTopic("topic.*");
        topic2.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.assertTrue(pattern.equals("topic.*"));
            Assertions.assertTrue(channel.equals("topic.t1"));
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RTopic topic3 = redisson2.getTopic("topic.t1");
        topic3.publish(new Message("123"));

        Assertions.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void test() throws InterruptedException {
        final CountDownLatch messageRecieved = new CountDownLatch(5);

        final CountDownLatch statusRecieved = new CountDownLatch(1);
        RedissonClient redisson1 = BaseTest.createInstance();
        RPatternTopic topic1 = redisson1.getPatternTopic("topic.*");
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPSubscribe(String pattern) {
                Assertions.assertEquals("topic.*", pattern);
                statusRecieved.countDown();
            }
        });
        topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic.t1");
        topic2.addListener(Message.class, (channel, msg) -> {
            Assertions.assertEquals(new Message("123"), msg);
            messageRecieved.countDown();
        });
        topic2.publish(new Message("123"));
        topic2.publish(new Message("123"));

        RTopic topicz = redisson2.getTopic("topicz.t1");
        topicz.publish(new Message("789")); // this message doesn't get
                                            // delivered, and would fail the
                                            // assertion

        RTopic topict2 = redisson2.getTopic("topic.t2");
        topict2.publish(new Message("123"));

        statusRecieved.await();
        Assertions.assertTrue(messageRecieved.await(5, TimeUnit.SECONDS));

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testListenerRemove() throws InterruptedException {
        RedissonClient redisson1 = BaseTest.createInstance();
        RPatternTopic topic1 = redisson1.getPatternTopic("topic.*");
        final CountDownLatch l = new CountDownLatch(1);
        topic1.addListener(new BasePatternStatusListener() {
            @Override
            public void onPUnsubscribe(String pattern) {
                Assertions.assertEquals("topic.*", pattern);
                l.countDown();
            }
        });
        int id = topic1.addListener(Message.class, (pattern, channel, msg) -> {
            Assertions.fail();
        });

        RedissonClient redisson2 = BaseTest.createInstance();
        RTopic topic2 = redisson2.getTopic("topic.t1");
        topic1.removeListener(id);
        topic2.publish(new Message("123"));

        redisson1.shutdown();
        redisson2.shutdown();
    }

    @Test
    public void testConcurrentTopic() throws Exception {
        int threads = 30;
        int loops = 50000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>(); 
        for (int i = 0; i < threads; i++) {

            Runnable worker = new Runnable() {

                @Override
                public void run() {
                    for (int j = 0; j < loops; j++) {
                        RPatternTopic t = redisson.getPatternTopic("PUBSUB*");
                        int listenerId = t.addListener(new PatternStatusListener() {
                            @Override
                            public void onPUnsubscribe(String channel) {
                            }
                            
                            @Override
                            public void onPSubscribe(String channel) {
                            }
                        });
                        redisson.getTopic("PUBSUB_" + j).publish("message");
                        t.removeListener(listenerId);
                    }
                }
            };
            Future<?> s = executor.submit(worker);
            futures.add(s);
        }
        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        for (Future<?> future : futures) {
            future.get();
        }
    }

    @Test
    public void testReattachInClusterSlave() throws Exception {
        testReattachInCluster(SubscriptionMode.SLAVE);
    }

    @Test
    public void testReattachInClusterMaster() throws Exception {
        testReattachInCluster(SubscriptionMode.MASTER);
    }

    private void testReattachInCluster(SubscriptionMode subscriptionMode) throws Exception {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.$);
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.$);
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$);
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$);
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                RedisRunner.KEYSPACE_EVENTS_OPTIONS.$);
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave()
                .notifyKeyspaceEvents(
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.g,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.$);


        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
                .setSubscriptionMode(subscriptionMode)
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        AtomicInteger executions = new AtomicInteger();

        RPatternTopic topic = redisson.getPatternTopic("__keyevent@*:del", StringCodec.INSTANCE);
        topic.addListener(String.class, new PatternMessageListener<String>() {
            @Override
            public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                executions.incrementAndGet();
            }
        });

        process.getNodes().stream().filter(x -> master1.getPort() == x.getRedisServerPort())
                .forEach(x -> {
                        x.stop();
                });

        Thread.sleep(40000);

        for (int i = 0; i < 100; i++) {
            RBucket<Object> b = redisson.getBucket("test" + i);
            b.set(i);
            b.delete();
        }
        Thread.sleep(100);
        assertThat(executions.get()).isEqualTo(100);

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testReattach() throws InterruptedException, IOException, ExecutionException, TimeoutException {
        RedisProcess runner = new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        final AtomicBoolean executed = new AtomicBoolean();
        
        RPatternTopic topic = redisson.getPatternTopic("topic*");
        topic.addListener(Integer.class, new PatternMessageListener<Integer>() {
            @Override
            public void onMessage(CharSequence pattern, CharSequence channel, Integer msg) {
                if (msg == 1) {
                    executed.set(true);
                }
            }
        });
        
        runner.stop();

        runner = new RedisRunner()
                .port(runner.getRedisServerPort())
                .nosave()
                .randomDir()
                .run();
        
        Thread.sleep(1000);

        redisson.getTopic("topic1").publish(1);
        
        await().atMost(5, TimeUnit.SECONDS).untilTrue(executed);
        
        redisson.shutdown();
        runner.stop();
    }
    
}
