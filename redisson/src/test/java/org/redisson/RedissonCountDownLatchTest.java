package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RCountDownLatch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonCountDownLatchTest extends BaseTest {

    @Test
    public void testAwaitTimeout() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        final RCountDownLatch latch = redisson.getCountDownLatch("latch1");
        Assertions.assertTrue(latch.trySetCount(1));

        executor.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
            latch.countDown();
        });


        executor.execute(() -> {
            try {
                Assertions.assertEquals(1, latch.getCount());
                boolean res = latch.await(550, TimeUnit.MILLISECONDS);
                Assertions.assertTrue(res);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        });

        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

    }

    @Test
    public void testAwaitTimeoutFail() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        final RCountDownLatch latch = redisson.getCountDownLatch("latch1");
        Assertions.assertTrue(latch.trySetCount(1));

        executor.execute(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
            latch.countDown();
        });


        executor.execute(() -> {
            try {
                Assertions.assertEquals(1, latch.getCount());
                boolean res = latch.await(500, TimeUnit.MILLISECONDS);
                Assertions.assertFalse(res);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        });

        executor.shutdown();
        Assertions.assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    public void testCountDown() throws InterruptedException {
        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        latch.trySetCount(2);
        Assertions.assertEquals(2, latch.getCount());
        latch.countDown();
        Assertions.assertEquals(1, latch.getCount());
        latch.countDown();
        Assertions.assertEquals(0, latch.getCount());
        latch.await();
        latch.countDown();
        Assertions.assertEquals(0, latch.getCount());
        latch.await();
        latch.countDown();
        Assertions.assertEquals(0, latch.getCount());
        latch.await();

        RCountDownLatch latch1 = redisson.getCountDownLatch("latch1");
        latch1.trySetCount(1);
        latch1.countDown();
        Assertions.assertEquals(0, latch.getCount());
        latch1.countDown();
        Assertions.assertEquals(0, latch.getCount());
        latch1.await();

        RCountDownLatch latch2 = redisson.getCountDownLatch("latch2");
        latch2.trySetCount(1);
        latch2.countDown();
        latch2.await();
        latch2.await();

        RCountDownLatch latch3 = redisson.getCountDownLatch("latch3");
        Assertions.assertEquals(0, latch.getCount());
        latch3.await();

        RCountDownLatch latch4 = redisson.getCountDownLatch("latch4");
        Assertions.assertEquals(0, latch.getCount());
        latch4.countDown();
        Assertions.assertEquals(0, latch.getCount());
        latch4.await();
    }

    @Test
    public void testDelete() throws Exception {
        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        latch.trySetCount(1);
        Assertions.assertTrue(latch.delete());
    }

    @Test
    public void testDeleteFailed() throws Exception {
        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        Assertions.assertFalse(latch.delete());
    }

    @Test
    public void testTrySetCount() throws Exception {
        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        assertThat(latch.trySetCount(1)).isTrue();
        assertThat(latch.trySetCount(2)).isFalse();
    }

    @Test
    public void testCount() {
        RCountDownLatch latch = redisson.getCountDownLatch("latch");
        assertThat(latch.getCount()).isEqualTo(0);
    }
}
