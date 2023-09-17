package org.redisson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.LocalCachedMapOptions.ReconnectionStrategy;
import org.redisson.api.LocalCachedMapOptions.SyncStrategy;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.listener.LocalCacheInvalidateListener;
import org.redisson.api.map.MapLoader;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.redisson.config.Config;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLocalCachedMapTest extends BaseMapTest {

    public abstract class UpdateTest {

        RLocalCachedMap<String, Integer> map1;
        RLocalCachedMap<String, Integer> map2;
        Map<String, Integer> cache1;
        Map<String, Integer> cache2;
        
        public void execute() throws InterruptedException {
            LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LFU)
                    .syncStrategy(SyncStrategy.UPDATE)
                    .reconnectionStrategy(ReconnectionStrategy.CLEAR)
                    .cacheSize(5);
            map1 = redisson.getLocalCachedMap("test2", options);
            cache1 = map1.getCachedMap();
            
            map2 = redisson.getLocalCachedMap("test2", options);
            cache2 = map2.getCachedMap();
            
            map1.put("1", 1);
            map1.put("2", 2);
            
            Thread.sleep(50);
            
            assertThat(cache1.size()).isEqualTo(2);
            assertThat(cache2.size()).isEqualTo(2);
            
            test();
        }
        
        public abstract void test() throws InterruptedException;
        
    }
    
    public abstract class InvalidationTest {

        RLocalCachedMap<String, Integer> map1;
        RLocalCachedMap<String, Integer> map2;
        Map<String, Integer> cache1;
        Map<String, Integer> cache2;
        
        public void execute() throws InterruptedException {
            LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5);
            map1 = redisson.getLocalCachedMap("test", options);
            cache1 = map1.getCachedMap();
            
            map2 = redisson.getLocalCachedMap("test", options);
            cache2 = map2.getCachedMap();
            
            map1.put("1", 1);
            map1.put("2", 2);
            
            assertThat(map2.get("1")).isEqualTo(1);
            assertThat(map2.get("2")).isEqualTo(2);
            
            assertThat(cache1.size()).isEqualTo(2);
            assertThat(cache2.size()).isEqualTo(2);
            
            test();
        }
        
        public abstract void test() throws InterruptedException;
        
    }

    @Test
    public void testUpdateStrategy() {
        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>defaults()
                .syncStrategy(LocalCachedMapOptions.SyncStrategy.UPDATE);

        RLocalCachedMap<String, String> cachedMap = redisson.getLocalCachedMap("myMap11", options);
        cachedMap.put("a", "b");
        cachedMap.remove("a");
        String value = cachedMap.get("a");
        assertThat(value).isNull();
        assertThat(cachedMap.containsKey("a")).isFalse();
    }

    @Test
    public void testPutAfterDelete() {
        RMap<String, String> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());

        for (int i = 0; i < 1_000; i++) {
            map.delete();

            map.put("key", "val1");
            map.get("key");
            map.put("key", "val2");
            String val = map.get("key");
            assertThat(val).isEqualTo("val2");
        }
    }

    @Test
    public void testListeners() throws InterruptedException {
        RLocalCachedMap<String, String> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, String> entries = new HashMap();
        map.addListener((LocalCacheInvalidateListener<String, String>) (key, value) -> {
            entries.put(key, value);
        });
        map.put("v1", "v2");
        map.put("v3", "v4");

        Thread.sleep(100);

        assertThat(entries.keySet()).containsOnly("v1", "v3");
        assertThat(entries.values()).containsOnly(null, null);
    }

    @Test
    public void testExpiration() throws IOException, InterruptedException {
        RedisRunner.RedisProcess instance = new RedisRunner()
                .nosave()
                .randomPort()
                .randomDir()
                .notifyKeyspaceEvents(
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.E,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.K,
                        RedisRunner.KEYSPACE_EVENTS_OPTIONS.x)
                .run();

        Config config = new Config();
        config.useSingleServer().setAddress(instance.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RLocalCachedMap<String, String> m = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        m.put("12", "32");
        assertThat(m.cachedEntrySet()).hasSize(1);
        m.expire(Duration.ofSeconds(1));
        Thread.sleep(1500);
        assertThat(m.cachedEntrySet()).hasSize(0);
        assertThat(m.get("12")).isNull();

        redisson.shutdown();
        instance.stop();
    }

    @Test
    public void testMapLoaderGet() {
        Map<String, String> cache = new HashMap<>();
        cache.put("1", "11");
        cache.put("2", "22");
        cache.put("3", "33");

        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>defaults()
                                                        .storeMode(LocalCachedMapOptions.StoreMode.LOCALCACHE).loader(createMapLoader(cache));
        RMap<String, String> map =  redisson.getLocalCachedMap("test", options);

        assertThat(map.size()).isEqualTo(0);
        assertThat(map.get("1")).isEqualTo("11");
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get("0")).isNull();
        map.put("0", "00");
        assertThat(map.get("0")).isEqualTo("00");
        assertThat(map.size()).isEqualTo(2);

        assertThat(map.containsKey("2")).isTrue();
        assertThat(map.size()).isEqualTo(3);

        Map<String, String> s = map.getAll(new HashSet<>(Arrays.asList("1", "2", "9", "3")));
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("1", "11");
        expectedMap.put("2", "22");
        expectedMap.put("3", "33");
        assertThat(s).isEqualTo(expectedMap);
        assertThat(map.size()).isEqualTo(4);
        destroy(map);
    }

    @Override
    protected <K, V> RMap<K, V> getMap(String name) {
        return redisson.getLocalCachedMap(name, LocalCachedMapOptions.defaults());
    }
    
    @Override
    protected <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return redisson.getLocalCachedMap(name, codec, LocalCachedMapOptions.defaults());
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults().writer(createMapWriter(map));
        return redisson.getLocalCachedMap(name, options);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults()
                                    .writer(createMapWriter(map))
                                    .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getLocalCachedMap("test", options);        
    }

    @Override
    protected <K, V> RMap<K, V> getWriteBehindAsyncTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults()
                .writerAsync(createMapWriterAsync(map))
                .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getLocalCachedMap("test", options);
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults().loader(createMapLoader(map));
        return redisson.getLocalCachedMap(name, options);        
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderAsyncTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults().loaderAsync(createMapLoaderAsync(map));
        return redisson.getLocalCachedMap(name, options);
    }

    @Test
    public void testBigPutAll() throws InterruptedException {
        RLocalCachedMap<Object, Object> m = redisson.getLocalCachedMap("testValuesWithNearCache2",
                LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).syncStrategy(SyncStrategy.INVALIDATE));
        
        Map<Object, Object> map = new HashMap<>();
        for (int k = 0; k < 10000; k++) {
            map.put("" + k, "" + k);
        }
        m.putAll(map);
        
        assertThat(m.size()).isEqualTo(10000);
    }

    @Test
    public void testReadAllValues2() {
        ObjectMapper objectMapper = new JsonJacksonCodec().getObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Codec codec = new TypedJsonJacksonCodec(String.class, SimpleValue.class, objectMapper);

        RLocalCachedMap<String, SimpleValue> map1 = redisson.getLocalCachedMap("test", codec, LocalCachedMapOptions.defaults());
        RLocalCachedMap<String, SimpleValue> map2 = redisson.getLocalCachedMap("test", codec, LocalCachedMapOptions.defaults());
        map1.put("key", new SimpleValue("3"));
        Collection<SimpleValue> s = map1.readAllValues();
        assertThat(s).hasSize(1);
        Collection<SimpleValue> s2 = map2.readAllValues();
        assertThat(s2).hasSize(1);
    }


    @Test
    public void testReadValuesAndEntries() {
        RLocalCachedMap<Object, Object> m = redisson.getLocalCachedMap("testValuesWithNearCache2",
                LocalCachedMapOptions.defaults());
        m.clear();
        m.put("a", 1);
        m.put("b", 2);
        m.put("c", 3);

        Set<Integer> expectedValuesSet = new HashSet<>();
        expectedValuesSet.add(1);
        expectedValuesSet.add(2);
        expectedValuesSet.add(3);
        Set<Object> actualValuesSet = new HashSet<>(m.readAllValues());
        Assertions.assertEquals(expectedValuesSet, actualValuesSet);
        Map<String, Integer> expectedMap = new HashMap<>();
        expectedMap.put("a", 1);
        expectedMap.put("b", 2);
        expectedMap.put("c", 3);
        Assertions.assertEquals(expectedMap.entrySet(), m.readAllEntrySet());
    }
    
    @Test
    public void testClearEmpty() {
        RLocalCachedMap<Object, Object> localCachedMap = redisson.getLocalCachedMap("udi-test",
                        LocalCachedMapOptions.defaults());

        localCachedMap.clear();
    }
    
    @Test
    public void testDelete() {
        RLocalCachedMap<String, String> localCachedMap = redisson.getLocalCachedMap("udi-test",
                        LocalCachedMapOptions.defaults());

        assertThat(localCachedMap.delete()).isFalse();
        localCachedMap.put("1", "2");
        assertThat(localCachedMap.delete()).isTrue();
    }

    @Test
    public void testInvalidationOnClear() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.clear();

                Thread.sleep(50);
                assertThat(cache1.size()).isZero();
                assertThat(cache2.size()).isZero();
                
                assertThat(map1.size()).isZero();
                assertThat(map2.size()).isZero();
            }
        }.execute();
    }

    @Test
    public void testInvalidationOnUpdateNonBinaryCodec() throws InterruptedException {
        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5);
        RLocalCachedMap<String, String> map1 = redisson.getLocalCachedMap("test", new StringCodec(), options);
        Map<String, String> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, String> map2 = redisson.getLocalCachedMap("test", new StringCodec(), options);
        Map<String, String> cache2 = map2.getCachedMap();
        
        map1.put("1", "1");
        map1.put("2", "2");
        
        assertThat(map2.get("1")).isEqualTo("1");
        assertThat(map2.get("2")).isEqualTo("2");
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", "3");
        map2.put("2", "4");
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(1);
        assertThat(cache2.size()).isEqualTo(1);
    }
    
    @Test
    public void testSyncOnUpdate() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.put("1", 3);
                map2.put("2", 4);
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(1);
                assertThat(cache2.size()).isEqualTo(1);
            }
        }.execute();
        
        new UpdateTest() {
            @Override
            public void test() throws InterruptedException {
                map1.put("1", 3);
                map2.put("2", 4);
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(2);
                assertThat(cache2.size()).isEqualTo(2);
            }
        }.execute();
    }

    @Test
    public void testNameMapper() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return name + ":suffix:";
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace(":suffix:", "");
                    }
                })
              .setConnectionMinimumIdleSize(3)
              .setConnectionPoolSize(3)
              .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient redisson = Redisson.create(config);

        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5);

        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();

        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();

        assertThat(map1.getName()).isEqualTo("test");
        assertThat(map2.getName()).isEqualTo("test");

        map1.put("1", 1);
        map1.put("2", 2);

        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);

        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", 3);
        map2.put("2", 4);
        Thread.sleep(50);

        assertThat(redisson.getKeys().getKeys()).containsOnly("test");

        RedisClientConfig destinationCfg = new RedisClientConfig();
        destinationCfg.setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        RedisClient client = RedisClient.create(destinationCfg);
        RedisConnection destinationConnection = client.connect();

        List<String> channels = destinationConnection.sync(RedisCommands.PUBSUB_CHANNELS);
        assertThat(channels).contains("{test:suffix:}:topic");
        client.shutdown();

        assertThat(cache1.size()).isEqualTo(1);
        assertThat(cache2.size()).isEqualTo(1);

        redisson.shutdown();
    }

    @Test
    public void testPutAllSyncUpdate() {
        RLocalCachedMap<Object, Object> rLocalCachedMap1 = redisson.getLocalCachedMap("znMapTest1", LocalCachedMapOptions.defaults()
                .evictionPolicy(EvictionPolicy.NONE)
                .cacheSize(0)
                .syncStrategy(SyncStrategy.UPDATE)
                .reconnectionStrategy(ReconnectionStrategy.NONE)
                .writeMode(WriteMode.WRITE_BEHIND));
        Map<String, String> map = new HashMap<>();
        map.put("test", "123");
        rLocalCachedMap1.putAll(map);
    }

    @Test
    public void testNoInvalidationOnUpdate() throws InterruptedException {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.NONE);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);

        Thread.sleep(50);

        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", 3);
        map2.put("2", 4);
        Thread.sleep(50);

        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);
    }

    @Test
    public void testLocalCacheState() throws InterruptedException {
        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.INVALIDATE);
        
        RLocalCachedMap<String, String> map = redisson.getLocalCachedMap("test", options);
        map.put("1", "11");
        map.put("2", "22");
        assertThat(map.cachedKeySet()).containsExactlyInAnyOrder("1", "2");
        assertThat(map.cachedValues()).containsExactlyInAnyOrder("11", "22");
        assertThat(map.getCachedMap().keySet()).containsExactlyInAnyOrder("1", "2");
        assertThat(map.getCachedMap().values()).containsExactlyInAnyOrder("11", "22");
    }

    
    @Test
    public void testLocalCacheClear() throws InterruptedException {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.INVALIDATE);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        
        map1.clearLocalCache();
        
        assertThat(redisson.getKeys().count()).isEqualTo(1);

        assertThat(cache1.size()).isZero();
        assertThat(cache2.size()).isZero();
    }

    
    @Test
    public void testNoInvalidationOnRemove() throws InterruptedException {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.NONE);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.remove("1");
        map2.remove("2");
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(1);
        assertThat(cache2.size()).isEqualTo(1);
    }
    
    @Test
    public void testSyncOnRemove() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.remove("1");
                map2.remove("2");
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(0);
                assertThat(cache2.size()).isEqualTo(0);
            }
        }.execute();
        
        new UpdateTest() {
            @Override
            public void test() throws InterruptedException {
                map1.remove("1");
                map2.remove("2");
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(0);
                assertThat(cache2.size()).isEqualTo(0);
            }
        }.execute();
    }
    
    @Test
    public void testLFU() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5));
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        map.put("16", 4);
        map.put("17", 5);
        map.put("18", 6);
        
        assertThat(cache.size()).isEqualTo(5);
        assertThat(map.size()).isEqualTo(6);
        assertThat(map.keySet()).containsOnly("12", "14", "15", "16", "17", "18");
        assertThat(map.values()).containsOnly(1, 2, 3, 4, 5, 6);
    }
    
    @Test
    public void testLRU() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LRU).cacheSize(5));
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        map.put("16", 4);
        map.put("17", 5);
        map.put("18", 6);
        
        assertThat(cache.size()).isEqualTo(5);
        assertThat(map.size()).isEqualTo(6);
        assertThat(map.keySet()).containsOnly("12", "14", "15", "16", "17", "18");
        assertThat(map.values()).containsOnly(1, 2, 3, 4, 5, 6);
    }

    @Test
    public void testSizeCache() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        
        assertThat(cache.size()).isEqualTo(3);
        assertThat(map.size()).isEqualTo(3);
    }

    @Test
    public void testInvalidationOnPut() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.put("1", 10);
                map1.put("2", 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }
    
    @Test
    public void testPutGetCache() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);

        assertThat(cache).containsValues(1, 2, 3);
        assertThat(map.get("12")).isEqualTo(1);
        assertThat(map.get("14")).isEqualTo(2);
        assertThat(map.get("15")).isEqualTo(3);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        
        assertThat(map1.get("12")).isEqualTo(1);
        assertThat(map1.get("14")).isEqualTo(2);
        assertThat(map1.get("15")).isEqualTo(3);
    }

    @Test
    public void testGetStoringCacheMiss() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().storeCacheMiss(true));
        Map<String, Integer> cache = map.getCachedMap();

        assertThat(map.get("19")).isNull();

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .untilAsserted(() -> assertThat(cache.size()).isEqualTo(1));
    }

    @Test
    public void testGetStoringCacheMissGetAll() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().storeCacheMiss(true).loader(new MapLoader<String, Integer>() {
            @Override
            public Integer load(String key) {
                return null;
            }

            @Override
            public Iterable<String> loadAllKeys() {
                return new ArrayList<>();
            }
        }));
        Map<String, Integer> cache = map.getCachedMap();

        Map<String, Integer> s1 = map.getAll(new HashSet<>(Arrays.asList("1", "2", "3")));
        assertThat(s1).isEmpty();

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .untilAsserted(() -> assertThat(cache.size()).isEqualTo(3));

        Map<String, Integer> s2 = map.getAll(new HashSet<>(Arrays.asList("1", "2", "3")));
        assertThat(s2).isEmpty();

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .untilAsserted(() -> assertThat(cache.size()).isEqualTo(3));
    }

    @Test
    public void testGetNotStoringCacheMiss() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().storeCacheMiss(false));
        Map<String, Integer> cache = map.getCachedMap();

        assertThat(map.get("19")).isNull();

        Awaitility.await().atMost(Durations.ONE_SECOND)
                .untilAsserted(() -> assertThat(cache.size()).isEqualTo(0));
    }

    @Test
    public void testGetAllCache() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("getAll", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();
        map.put("1", 100);
        map.put("2", 200);
        map.put("3", 300);
        map.put("4", 400);

        assertThat(cache.size()).isEqualTo(4);
        Map<String, Integer> filtered = map.getAll(new HashSet<String>(Arrays.asList("2", "3", "5")));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("2", 200);
        expectedMap.put("3", 300);
        assertThat(filtered).isEqualTo(expectedMap);
        
        RMap<String, Integer> map1 = redisson.getLocalCachedMap("getAll", LocalCachedMapOptions.defaults());
        
        Map<String, Integer> filtered1 = map1.getAll(new HashSet<String>(Arrays.asList("2", "3", "5")));

        assertThat(filtered1).isEqualTo(expectedMap);
    }

    @Test
    public void testInvalidationOnPutAll() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                Map<String, Integer> entries = new HashMap<>();
                entries.put("1", 10);
                entries.put("2", 20);
                map1.putAll(entries);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    
    @Test
    public void testPutAllCache() throws InterruptedException {
        RLocalCachedMap<Integer, String> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        RLocalCachedMap<Integer, String> map1 = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<Integer, String> cache = map.getCachedMap();
        Map<Integer, String> cache1 = map1.getCachedMap();
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        map.putAll(joinMap);

        assertThat(cache.size()).isEqualTo(6);
        assertThat(cache1.size()).isEqualTo(0);
        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6);
        
        map1.putAll(joinMap);
        
        // waiting for cache cleanup listeners triggering 
        Thread.sleep(500);
        
        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache1.size()).isEqualTo(3);
    }

    @Test
    public void testGetBeforePut() {
        RLocalCachedMap<String, String> map1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        for (int i = 0; i < 1_000; i++) {
            map1.put("key" + i, "val");
        }

        RMap<String, String> map2 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        for (int i = 0; i < 1_000; i++) {
            map2.get("key" + i);
            map2.put("key" + i, "value" + i);
            String cachedValue = map2.get("key" + i);
            assertThat(cachedValue).isEqualTo("value" + i);
        }
    }

    @Test
    public void testAddAndGet() throws InterruptedException {
        RLocalCachedMap<Integer, Integer> map = redisson.getLocalCachedMap("getAll", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE), LocalCachedMapOptions.defaults());
        Map<Integer, Integer> cache = map.getCachedMap();
        map.put(1, 100);

        Integer res = map.addAndGet(1, 12);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(res).isEqualTo(112);
        res = map.get(1);
        assertThat(res).isEqualTo(112);

        RMap<Integer, Double> map2 = redisson.getLocalCachedMap("getAll2", new CompositeCodec(redisson.getConfig().getCodec(), DoubleCodec.INSTANCE), LocalCachedMapOptions.defaults());
        map2.put(1, new Double(100.2));

        Double res2 = map2.addAndGet(1, new Double(12.1));
        assertThat(res2).isEqualTo(112.3);
        res2 = map2.get(1);
        assertThat(res2).isEqualTo(112.3);

        RMap<String, Integer> mapStr = redisson.getLocalCachedMap("mapStr", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE), LocalCachedMapOptions.defaults());
        assertThat(mapStr.put("1", 100)).isNull();

        assertThat(mapStr.addAndGet("1", 12)).isEqualTo(112);
        assertThat(mapStr.get("1")).isEqualTo(112);
        assertThat(cache.size()).isEqualTo(1);
    }
    
    @Test
    public void testFastPutIfAbsent() throws Exception {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"))).isFalse();
        assertThat(cache.size()).isEqualTo(1);
        assertThat(map.get(key)).isEqualTo(value);

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        assertThat(map.fastPutIfAbsent(key1, value1)).isTrue();
        Thread.sleep(50);
        assertThat(cache.size()).isEqualTo(2);
        assertThat(map.get(key1)).isEqualTo(value1);
    }
    
    @Test
    public void testReadAllEntrySet() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.readAllEntrySet().size()).isEqualTo(3);
        assertThat(cache.size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllEntrySet()).containsOnlyElementsOf(testMap.entrySet());
        
        RMap<SimpleKey, SimpleValue> map2 = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        assertThat(map2.readAllEntrySet()).containsOnlyElementsOf(testMap.entrySet());
    }
    
    @Test
    public void testPutIfAbsent() throws Exception {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();

        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        Assertions.assertEquals(value, map.putIfAbsent(key, new SimpleValue("3")));
        Assertions.assertEquals(value, map.get(key));

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assertions.assertNull(map.putIfAbsent(key1, value1));
        Assertions.assertEquals(value1, map.get(key1));
        assertThat(cache.size()).isEqualTo(2);
    }
    
    @Test
    public void testInvalidationOnRemoveValue() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.remove("1", 1);
                map1.remove("2", 2);

                Thread.sleep(50);
                
                assertThat(cache1).isEmpty();
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    
    @Test
    public void testRemoveValue() throws InterruptedException {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assertions.assertTrue(res);

        Thread.sleep(50);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertNull(val1);

        Assertions.assertEquals(0, map.size());
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    public void testRemoveValueFail() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("2"), new SimpleValue("1"));
        Assertions.assertFalse(res);

        boolean res1 = map.remove(new SimpleKey("1"), new SimpleValue("3"));
        Assertions.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("2", val1.getValue());
        assertThat(cache.size()).isEqualTo(1);
    }
    
    @Test
    public void testInvalidationOnReplaceOldValue() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.replace("1", 1, 10);
                map1.replace("2", 2, 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    
    @Test
    public void testReplaceOldValueFail() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assertions.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("2", val1.getValue());
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    public void testReplaceOldValueSuccess() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assertions.assertTrue(res);

        boolean res1 = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assertions.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("3", val1.getValue());
        assertThat(cache.size()).isEqualTo(1);
    }
    
    @Test
    public void testInvalidationOnReplaceValue() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.replace("1", 10);
                map1.replace("2", 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }
    
    @Test
    public void testReplaceValue() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        Assertions.assertEquals("2", res.getValue());
        assertThat(cache.size()).isEqualTo(1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("3", val1.getValue());
    }
    
    @Test
    public void testReadAllValues() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));
        assertThat(cache.size()).isEqualTo(3);

        assertThat(map.readAllValues().size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllValues()).containsAll(testMap.values());
        
        RMap<SimpleKey, SimpleValue> map2 = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        assertThat(map2.readAllValues()).containsAll(testMap.values());
    }

    @Test
    public void testInvalidationOnFastRemove() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.fastRemove("1", "2", "3");

                Thread.sleep(50);
                
                assertThat(cache1).isEmpty();
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    @Test
    public void testRemove() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();
        map.put("12", 1);

        assertThat(cache.size()).isEqualTo(1);
        
        assertThat(map.remove("12")).isEqualTo(1);
        
        assertThat(cache.size()).isEqualTo(0);
        
        assertThat(map.remove("14")).isNull();
    }

    @Test
    public void testFastRemove() throws InterruptedException, ExecutionException {
        RLocalCachedMap<Integer, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        map.put(1, 3);
        map.put(2, 4);
        map.put(7, 8);

        assertThat(map.fastRemove(1, 2)).isEqualTo(2);
        assertThat(map.fastRemove(2)).isEqualTo(0);
        assertThat(map.size()).isEqualTo(1);
    }
    
    @Test
    public void testFastRemoveEmpty()  {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults()
                .evictionPolicy(EvictionPolicy.NONE)
                .cacheSize(3)
                .syncStrategy(SyncStrategy.NONE);
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", options);
        assertThat(map.fastRemove("test")).isZero();
    }

    @Test
    public void testInvalidationOnFastPut() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.fastPut("1", 10);
                map1.fastPut("2", 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }


    @Test
    public void testFastPut() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Assertions.assertTrue(map.fastPut("1", 2));
        assertThat(map.get("1")).isEqualTo(2);
        Assertions.assertFalse(map.fastPut("1", 3));
        assertThat(map.get("1")).isEqualTo(3);
        Assertions.assertEquals(1, map.size());
    }

    @Test
    public void testMerge() {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.defaults();
        options.loader(new MapLoader() {
            @Override
            public Object load(Object o) {
                return null;
            }

            @Override
            public Iterable loadAllKeys() {
                return null;
            }
        });

        RLocalCachedMap<String, Integer> localCachedMap = redisson.getLocalCachedMap("map", options);
        localCachedMap.merge("c", 2, (x, y) -> x * y);

        Integer counter = localCachedMap.get("c");
        assertThat(counter).isEqualTo(2);
    }
    
}
