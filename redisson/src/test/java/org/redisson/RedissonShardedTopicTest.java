package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.NatMapper;
import org.redisson.api.RShardedTopic;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class RedissonShardedTopicTest {

    @Container
    private final GenericContainer<?> redisClusterContainer =
            new GenericContainer<>("vishnunair/docker-redis-cluster")
                    .withExposedPorts(6379, 6380, 6381, 6382, 6383, 6384)
                    .withStartupCheckStrategy(
                            new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(6))
                    );

    @Test
    public void testInvalidCommand() throws IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner.RedisProcess p = master1.run();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RShardedTopic t = redisson.getShardedTopic("ttt");
        RedisException e = Assertions.assertThrows(RedisException.class, () -> {
            t.addListener(String.class, (channel, msg) -> {
            });
        });
        assertThat(e.getMessage()).contains("ERR unknown command `SSUBSCRIBE`");

        redisson.shutdown();
        p.stop();
    }

    @Test
    public void testClusterSharding() {
        Config config = new Config();

        config.useClusterServers()
                .setPingConnectionInterval(0)
                .setNatMapper(new NatMapper() {
                    @Override
                    public RedisURI map(RedisURI uri) {
                        if (redisClusterContainer.getMappedPort(uri.getPort()) == null) {
                            return uri;
                        }
                        return new RedisURI(uri.getScheme(), redisClusterContainer.getHost(), redisClusterContainer.getMappedPort(uri.getPort()));
                    }
                })
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress("redis://127.0.0.1:" + redisClusterContainer.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            int j = i;
            RTopic topic = redisson.getShardedTopic("test" + i);
            topic.addListener(Integer.class, (c, v) -> {
                assertThat(v).isEqualTo(j);
                counter.incrementAndGet();
            });
        }

        for (int i = 0; i < 10; i++) {
            RTopic topic = redisson.getShardedTopic("test" + i);
            long s = topic.publish(i);
            assertThat(s).isEqualTo(1);
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> counter.get() == 10);

        for (int i = 0; i < 10; i++) {
            RTopic topic = redisson.getShardedTopic("test" + i);
            topic.removeAllListeners();
        }

        redisson.shutdown();

    }


}
