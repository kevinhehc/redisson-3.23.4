package org.redisson;

import org.junit.jupiter.api.*;
import org.redisson.client.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.pubsub.PubSubType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisClientTest {

    private RedisClient redisClient;
    
    @BeforeAll
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
    }

    @AfterAll
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }

    @BeforeEach
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
        }
        RedisClientConfig config = new RedisClientConfig();
        config.setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        redisClient = RedisClient.create(config);
    }

    @AfterEach
    public void after() throws InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
        redisClient.shutdown();
    }

    @Test
    public void testConnectAsync() throws InterruptedException {
        CompletionStage<RedisConnection> f = redisClient.connectAsync();
        CountDownLatch l = new CountDownLatch(2);
        f.whenComplete((conn, e) -> {
            assertThat(conn.sync(RedisCommands.PING)).isEqualTo("PONG");
            l.countDown();
        });
        f.handle((conn, ex) -> {
            assertThat(conn.sync(RedisCommands.PING)).isEqualTo("PONG");
            l.countDown();
            return null; 
        });
        assertThat(l.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testSubscribe() throws InterruptedException {
        RedisPubSubConnection pubSubConnection = redisClient.connectPubSub();
        final CountDownLatch latch = new CountDownLatch(2);
        pubSubConnection.addListener(new RedisPubSubListener<Object>() {

            @Override
            public void onStatus(PubSubType type, CharSequence channel) {
                assertThat(type).isEqualTo(PubSubType.SUBSCRIBE);
                assertThat(Arrays.asList("test1", "test2").contains(channel.toString())).isTrue();
                latch.countDown();
            }

            @Override
            public void onMessage(CharSequence channel, Object message) {
            }

            @Override
            public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
            }
        });
        pubSubConnection.subscribe(StringCodec.INSTANCE, new ChannelName("test1"), new ChannelName("test2"));

        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void test() throws InterruptedException {
        final RedisConnection conn = redisClient.connect();

        conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        for (int i = 0; i < 100000; i++) {
            pool.execute(() -> {
                conn.async(StringCodec.INSTANCE, RedisCommands.INCR, "test");
            });
        }

        pool.shutdown();

        assertThat(pool.awaitTermination(1, TimeUnit.HOURS)).isTrue();

        assertThat((Long) conn.sync(LongCodec.INSTANCE, RedisCommands.GET, "test")).isEqualTo(100000);

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testPipeline() throws InterruptedException, ExecutionException {
        RedisConnection conn = redisClient.connect();

        conn.sync(StringCodec.INSTANCE, RedisCommands.SET, "test", 0);

        List<CommandData<?, ?>> commands = new ArrayList<CommandData<?, ?>>();
        CommandData<String, String> cmd1 = conn.create(null, RedisCommands.PING);
        commands.add(cmd1);
        CommandData<Long, Long> cmd2 = conn.create(null, RedisCommands.INCR, "test");
        commands.add(cmd2);
        CommandData<Long, Long> cmd3 = conn.create(null, RedisCommands.INCR, "test");
        commands.add(cmd3);
        CommandData<String, String> cmd4 = conn.create(null, RedisCommands.PING);
        commands.add(cmd4);

        CompletableFuture<Void> p = new CompletableFuture<Void>();
        conn.send(new CommandsData(p, commands, false, false));

        assertThat(cmd1.getPromise().get()).isEqualTo("PONG");
        assertThat(cmd2.getPromise().get()).isEqualTo(1);
        assertThat(cmd3.getPromise().get()).isEqualTo(2);
        assertThat(cmd4.getPromise().get()).isEqualTo("PONG");

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testBigRequest() throws InterruptedException, ExecutionException {
        RedisConnection conn = redisClient.connect();

        for (int i = 0; i < 50; i++) {
            conn.sync(StringCodec.INSTANCE, RedisCommands.HSET, "testmap", i, "2");
        }

        Map<Object, Object> res = conn.sync(StringCodec.INSTANCE, RedisCommands.HGETALL, "testmap");
        assertThat(res.size()).isEqualTo(50);

        conn.sync(RedisCommands.FLUSHDB);
    }

    @Test
    public void testPipelineBigResponse() throws InterruptedException, ExecutionException {
        RedisConnection conn = redisClient.connect();

        List<CommandData<?, ?>> commands = new ArrayList<CommandData<?, ?>>();
        for (int i = 0; i < 1000; i++) {
            CommandData<String, String> cmd1 = conn.create(null, RedisCommands.PING);
            commands.add(cmd1);
        }

        CompletableFuture<Void> p = new CompletableFuture<Void>();
        conn.send(new CommandsData(p, commands, false, false));

        for (CommandData<?, ?> commandData : commands) {
            commandData.getPromise().get();
        }

        conn.sync(RedisCommands.FLUSHDB);
    }

}
