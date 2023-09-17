package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLexSortedSetRx;

public class RedissonLexSortedSetRxTest extends BaseRxTest {

    public static Iterable<String> sync(RLexSortedSetRx list) {
        return list.iterator().toList().blockingGet();
    }

    
    @Test
    public void testAddAllReactive() {
        RLexSortedSetRx list = redisson.getLexSortedSet("set");
        Assertions.assertTrue(sync(list.add("1")));
        Assertions.assertTrue(sync(list.add("2")));
        Assertions.assertTrue(sync(list.add("3")));
        Assertions.assertTrue(sync(list.add("4")));
        Assertions.assertTrue(sync(list.add("5")));

        RLexSortedSetRx list2 = redisson.getLexSortedSet("set2");
        Assertions.assertEquals(true, sync(list2.addAll(list.iterator())));
        Assertions.assertEquals(5, sync(list2.size()).intValue());
    }

    @Test
    public void testRemoveLexRangeTail() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(sync(set.add("a")));
        Assertions.assertFalse(sync(set.add("a")));
        Assertions.assertTrue(sync(set.add("b")));
        Assertions.assertTrue(sync(set.add("c")));
        Assertions.assertTrue(sync(set.add("d")));
        Assertions.assertTrue(sync(set.add("e")));
        Assertions.assertTrue(sync(set.add("f")));
        Assertions.assertTrue(sync(set.add("g")));

        Assertions.assertEquals(0, sync(set.removeRangeTail("z", false)).intValue());

        Assertions.assertEquals(4, sync(set.removeRangeTail("c", false)).intValue());
        assertThat(sync(set)).containsExactly("a", "b", "c");
        Assertions.assertEquals(1, sync(set.removeRangeTail("c", true)).intValue());
        assertThat(sync(set)).containsExactly("a", "b");
    }


    @Test
    public void testRemoveLexRangeHead() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        Assertions.assertEquals(2, sync(set.removeRangeHead("c", false)).intValue());
        assertThat(sync(set)).containsExactly("c", "d", "e", "f", "g");
        Assertions.assertEquals(1, (int)sync(set.removeRangeHead("c", true)));
        assertThat(sync(set)).containsExactly("d", "e", "f", "g");
    }

    @Test
    public void testRemoveLexRange() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        Assertions.assertEquals(5, sync(set.removeRange("aaa", true, "g", false)).intValue());
        assertThat(sync(set)).containsExactly("a", "g");
    }


    @Test
    public void testLexRangeTail() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        Assertions.assertTrue(sync(set.add("a")));
        Assertions.assertFalse(sync(set.add("a")));
        Assertions.assertTrue(sync(set.add("b")));
        Assertions.assertTrue(sync(set.add("c")));
        Assertions.assertTrue(sync(set.add("d")));
        Assertions.assertTrue(sync(set.add("e")));
        Assertions.assertTrue(sync(set.add("f")));
        Assertions.assertTrue(sync(set.add("g")));

        assertThat(sync(set.rangeTail("c", false))).containsExactly("d", "e", "f", "g");
        assertThat(sync(set.rangeTail("c", true))).containsExactly("c", "d", "e", "f", "g");
    }


    @Test
    public void testLexRangeHead() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        assertThat(sync(set.rangeHead("c", false))).containsExactly("a", "b");
        assertThat(sync(set.rangeHead("c", true))).containsExactly("a", "b", "c");
    }


    @Test
    public void testLexRange() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        assertThat(sync(set.range("aaa", true, "g", false))).contains("b", "c", "d", "e", "f");
    }

    @Test
    public void testLexCount() {
        RLexSortedSetRx set = redisson.getLexSortedSet("simple");
        sync(set.add("a"));
        sync(set.add("b"));
        sync(set.add("c"));
        sync(set.add("d"));
        sync(set.add("e"));
        sync(set.add("f"));
        sync(set.add("g"));

        Assertions.assertEquals(5, (int)sync(set.count("b", true, "f", true)));
        Assertions.assertEquals(3, (int)sync(set.count("b", false, "f", false)));
    }

}
