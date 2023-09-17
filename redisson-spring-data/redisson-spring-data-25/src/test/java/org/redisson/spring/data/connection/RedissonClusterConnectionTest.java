package org.redisson.spring.data.connection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.ClusterRunner;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.springframework.data.redis.connection.ClusterInfo;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode.NodeType;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonClusterConnectionTest {

    static RedissonClient redisson;
    static RedissonClusterConnection connection;
    static ClusterProcesses process;

    @BeforeClass
    public static void before() throws FailedToStartRedisException, IOException, InterruptedException {
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
        process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
        .setSubscriptionMode(SubscriptionMode.SLAVE)
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());

        redisson = Redisson.create(config);
        connection = new RedissonClusterConnection(redisson);
    }

    @AfterClass
    public static void after() {
        process.shutdown();
        redisson.shutdown();
    }

    @Test
    public void testDel() {
        List<byte[]> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] key = ("test" + i).getBytes();
            keys.add(key);
            connection.set(key, ("test" + i).getBytes());
        }
        assertThat(connection.del(keys.toArray(new byte[0][]))).isEqualTo(10);
    }

    @Test
    public void testMSet() {
        Map<byte[], byte[]> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(("test" + i).getBytes(), ("test" + i*100).getBytes());
        }
        connection.mSet(map);
        for (Map.Entry<byte[], byte[]> entry : map.entrySet()) {
            assertThat(connection.get(entry.getKey())).isEqualTo(entry.getValue());
        }
    }

    @Test
    public void testMGet() {
        Map<byte[], byte[]> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(("test" + i).getBytes(), ("test" + i*100).getBytes());
        }
        connection.mSet(map);
        List<byte[]> r = connection.mGet(map.keySet().toArray(new byte[0][]));
        assertThat(r).containsExactly(map.values().toArray(new byte[0][]));
    }

    @Test
    public void testClusterGetNodes() {
        Iterable<RedisClusterNode> nodes = connection.clusterGetNodes();
        assertThat(nodes).hasSize(6);
        for (RedisClusterNode redisClusterNode : nodes) {
            assertThat(redisClusterNode.getLinkState()).isNotNull();
            assertThat(redisClusterNode.getFlags()).isNotEmpty();
            assertThat(redisClusterNode.getHost()).isNotNull();
            assertThat(redisClusterNode.getPort()).isNotNull();
            assertThat(redisClusterNode.getId()).isNotNull();
            assertThat(redisClusterNode.getType()).isNotNull();
            if (redisClusterNode.getType() == NodeType.MASTER) {
                assertThat(redisClusterNode.getSlotRange().getSlots()).isNotEmpty();
            } else {
                assertThat(redisClusterNode.getMasterId()).isNotNull();
            }
        }
    }

    @Test
    public void testClusterGetNodesMaster() {
        Iterable<RedisClusterNode> nodes = connection.clusterGetNodes();
        for (RedisClusterNode redisClusterNode : nodes) {
            if (redisClusterNode.getType() == NodeType.MASTER) {
                Collection<RedisClusterNode> slaves = connection.clusterGetSlaves(redisClusterNode);
                assertThat(slaves).hasSize(1);
            }
        }
    }

    @Test
    public void testClusterGetMasterSlaveMap() {
        Map<RedisClusterNode, Collection<RedisClusterNode>> map = connection.clusterGetMasterSlaveMap();
        assertThat(map).hasSize(3);
        for (Collection<RedisClusterNode> slaves : map.values()) {
            assertThat(slaves).hasSize(1);
        }
    }

    @Test
    public void testClusterGetSlotForKey() {
        Integer slot = connection.clusterGetSlotForKey("123".getBytes());
        assertThat(slot).isNotNull();
    }

    @Test
    public void testClusterGetNodeForSlot() {
        RedisClusterNode node1 = connection.clusterGetNodeForSlot(1);
        RedisClusterNode node2 = connection.clusterGetNodeForSlot(16000);
        assertThat(node1.getId()).isNotEqualTo(node2.getId());
    }

    @Test
    public void testClusterGetNodeForKey() {
        RedisClusterNode node = connection.clusterGetNodeForKey("123".getBytes());
        assertThat(node).isNotNull();
    }

    @Test
    public void testClusterGetClusterInfo() {
        ClusterInfo info = connection.clusterGetClusterInfo();
        assertThat(info.getSlotsFail()).isEqualTo(0);
        assertThat(info.getSlotsOk()).isEqualTo(MasterSlaveConnectionManager.MAX_SLOT);
        assertThat(info.getSlotsAssigned()).isEqualTo(MasterSlaveConnectionManager.MAX_SLOT);
    }

    @Test
    public void testClusterAddRemoveSlots() {
        RedisClusterNode master = getFirstMaster();
        Integer slot = master.getSlotRange().getSlots().iterator().next();
        connection.clusterDeleteSlots(master, slot);
        connection.clusterAddSlots(master, slot);
    }

    @Test
    public void testClusterCountKeysInSlot() {
        Long t = connection.clusterCountKeysInSlot(1);
        assertThat(t).isZero();
    }

    @Test
    public void testClusterMeetForget() {
        RedisClusterNode master = getFirstMaster();
        connection.clusterForget(master);
        connection.clusterMeet(master);
    }

    @Test
    public void testClusterGetKeysInSlot() {
        List<byte[]> keys = connection.clusterGetKeysInSlot(12, 10);
        assertThat(keys).isEmpty();
    }

    @Test
    public void testClusterPing() {
        RedisClusterNode master = getFirstMaster();
        String res = connection.ping(master);
        assertThat(res).isEqualTo("PONG");
    }

    @Test
    public void testDbSize() {
        RedisClusterNode master = getFirstMaster();
        Long size = connection.dbSize(master);
        assertThat(size).isZero();
    }

    @Test
    public void testInfo() {
        RedisClusterNode master = getFirstMaster();
        Properties info = connection.info(master);
        assertThat(info.size()).isGreaterThan(10);
    }

    @Test
    public void testDelPipeline() {
        byte[] k = "key".getBytes();
        byte[] v = "val".getBytes();
        connection.set(k, v);

        connection.openPipeline();
        connection.get(k);
        connection.del(k);
        List<Object> results = connection.closePipeline();
        byte[] val = (byte[])results.get(0);
        assertThat(val).isEqualTo(v);
        Long res = (Long) results.get(1);
        assertThat(res).isEqualTo(1);
    }

    @Test
    public void testResetConfigStats() {
        RedisClusterNode master = getFirstMaster();
        connection.resetConfigStats(master);
    }

    @Test
    public void testTime() {
        RedisClusterNode master = getFirstMaster();
        Long time = connection.time(master);
        assertThat(time).isGreaterThan(1000);
    }

    @Test
    public void testGetClientList() {
        RedisClusterNode master = getFirstMaster();
        List<RedisClientInfo> list = connection.getClientList(master);
        assertThat(list.size()).isGreaterThan(10);
    }

    @Test
    public void testSetConfig() {
        RedisClusterNode master = getFirstMaster();
        connection.setConfig(master, "timeout", "10");
    }

    @Test
    public void testGetConfig() {
        RedisClusterNode master = getFirstMaster();
        Properties config = connection.getConfig(master, "*");
        assertThat(config.size()).isGreaterThan(20);
    }
    
    protected RedisClusterNode getFirstMaster() {
        Map<RedisClusterNode, Collection<RedisClusterNode>> map = connection.clusterGetMasterSlaveMap();
        RedisClusterNode master = map.keySet().iterator().next();
        return master;
    }

    @Test
    public void testConnectionFactoryReturnsClusterConnection() {
        RedisConnectionFactory connectionFactory = new RedissonConnectionFactory(redisson);

        assertThat(connectionFactory.getConnection()).isInstanceOf(RedissonClusterConnection.class);
    }

}
