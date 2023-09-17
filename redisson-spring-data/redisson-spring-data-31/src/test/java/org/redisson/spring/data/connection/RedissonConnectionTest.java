package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.types.Expiration;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedissonConnectionTest extends BaseConnectionTest {

    @Test
    public void testZLexCount() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        Long size = redisTemplate.boundZSetOps("test").lexCount(Range.closed("1", "2"));
        assertThat(size).isEqualTo(2);
    }

    @Test
    public void testZRemLexByRange() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);
        redisTemplate.boundZSetOps("test").add("3", 30);

        Long size = redisTemplate.boundZSetOps("test")
                .removeRangeByLex(Range.closed("1", "2"));
        assertThat(size).isEqualTo(2);
    }

    @Test
    public void testReverseRangeByLex() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        redisTemplate.boundZSetOps("test").add("1", 10);
        redisTemplate.boundZSetOps("test").add("2", 20);

        Set<String> ops = redisTemplate.boundZSetOps("test")
                .reverseRangeByLex(Range.closed("1", "2")
                        , Limit.limit().count(10));
        assertThat(ops.size()).isEqualTo(2);
    }

    @Test
    public void testExecute() {
        Long s = (Long) connection.execute("ttl", "key".getBytes());
        assertThat(s).isEqualTo(-2);
        connection.execute("flushDb");
    }

    @Test
    public void testRandomMembers() {
        RedisTemplate<String, Integer> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();


        SetOperations<String, Integer> ops = redisTemplate.opsForSet();
        ops.add("val", 1, 2, 3, 4);
        Set<Integer> values = redisTemplate.opsForSet().distinctRandomMembers("val", 1L);
        assertThat(values).containsAnyOf(1, 2, 3, 4);

        Integer v = redisTemplate.opsForSet().randomMember("val");
        assertThat(v).isNotNull();
    }

    @Test
    public void testRangeByLex() {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        RedisZSetCommands.Range range = new RedisZSetCommands.Range();
        range.lt("c");
        Set<String> zSetValue = redisTemplate.opsForZSet().rangeByLex("val", range);
        assertThat(zSetValue).isEmpty();
    }

    @Test
    public void testGeo() {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
        redisTemplate.afterPropertiesSet();

        String key = "test_geo_key";
        Point point = new Point(116.401001, 40.119499);
        redisTemplate.opsForGeo().add(key, point, "a");

        point = new Point(111.545998, 36.133499);
        redisTemplate.opsForGeo().add(key, point, "b");

        point = new Point(111.483002, 36.030998);
        redisTemplate.opsForGeo().add(key, point, "c");
        Circle within = new Circle(116.401001, 40.119499, 80000);
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeCoordinates();
        GeoResults<RedisGeoCommands.GeoLocation<String>> res = redisTemplate.opsForGeo().radius(key, within, args);
        assertThat(res.getContent().get(0).getContent().getName()).isEqualTo("a");
    }

    @Test
    public void testZSet() {
        connection.zAdd(new byte[] {1}, -1, new byte[] {1});
        connection.zAdd(new byte[] {1}, 2, new byte[] {2});
        connection.zAdd(new byte[] {1}, 10, new byte[] {3});

        assertThat(connection.zRangeByScore(new byte[] {1}, Double.NEGATIVE_INFINITY, 5))
                .containsOnly(new byte[] {1}, new byte[] {2});
    }

    @Test
    public void testEcho() {
        assertThat(connection.echo("test".getBytes())).isEqualTo("test".getBytes());
    }

    @Test
    public void testSetGet() {
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testSetExpiration() {
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(111122), SetOption.SET_IF_ABSENT)).isTrue();
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testHSetGet() {
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isTrue();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testZScan() {
        connection.zAdd("key".getBytes(), 1, "value1".getBytes());
        connection.zAdd("key".getBytes(), 2, "value2".getBytes());

        Cursor<Tuple> t = connection.zScan("key".getBytes(), ScanOptions.scanOptions().build());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value1".getBytes());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value2".getBytes());
    }

    @Test
    public void testRandFieldWithValues() {
        connection.hSet("map".getBytes(), "key1".getBytes(), "value1".getBytes());
        connection.hSet("map".getBytes(), "key2".getBytes(), "value2".getBytes());
        connection.hSet("map".getBytes(), "key3".getBytes(), "value3".getBytes());

        List<Map.Entry<byte[], byte[]>> s = connection.hRandFieldWithValues("map".getBytes(), 2);
        assertThat(s).hasSize(2);

        Map.Entry<byte[], byte[]> s2 = connection.hRandFieldWithValues("map".getBytes());
        assertThat(s2).isNotNull();

        byte[] f = connection.hRandField("map".getBytes());
        assertThat((Object) f).isIn("key1".getBytes(), "key2".getBytes(), "key3".getBytes());
    }
    
}
