package org.redisson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.MapOptions;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.RMap;
import org.redisson.client.codec.Codec;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMapTest extends BaseMapTest {

        @Override
    protected <K, V> RMap<K, V> getMap(String name) {
        return redisson.getMap(name);
    }

        @Override
    protected <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return redisson.getMap(name, codec);
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().loader(createMapLoader(map));
        return redisson.getMap("test", options);        
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderAsyncTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().loaderAsync(createMapLoaderAsync(map));
        return redisson.getMap("test", options);
    }

    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().writer(createMapWriter(map));
        return redisson.getMap("test", options);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults()
                                    .writer(createMapWriter(map))
                                    .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMap("test", options);        
    }

    @Override
    protected <K, V> RMap<K, V> getWriteBehindAsyncTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults()
                .writerAsync(createMapWriterAsync(map))
                .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMap("test", options);
    }

    @Test
    public void testEntrySet() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        assertThat(map.entrySet().size()).isEqualTo(3);
        Map<Integer, String> testMap = new HashMap<Integer, String>(map);
        assertThat(map.entrySet()).containsExactlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testReadAllEntrySet() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        assertThat(map.readAllEntrySet().size()).isEqualTo(3);
        Map<Integer, String> testMap = new HashMap<Integer, String>(map);
        assertThat(map.readAllEntrySet()).containsExactlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testSimpleTypes() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        String val = map.get(2);
        assertThat(val).isEqualTo("33");
    }

    @Test
    public void testKeySet() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.keySet()).containsOnly(new SimpleKey("33"), new SimpleKey("1"), new SimpleKey("5"));
    }
    
    @Test
    public void testKeyIterator() {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 0);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Collection<Integer> keys = map.keySet();
        assertThat(keys).containsOnly(1, 3, 4, 7);
        for (Iterator<Integer> iterator = map.keySet().iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!keys.remove(value)) {
                Assertions.fail("value can't be removed");
            }
        }

        assertThat(keys.size()).isEqualTo(0);
    }

            }
