package com.mzy.redis;

import com.mzy.distribute.SpringBaseTest;
import com.mzy.distribute.utils.RedisUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class JedisTest extends SpringBaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisTest.class);

    private int limiti = 1;

    private int limitj = 1000;

    private CountDownLatch lanch = new CountDownLatch(limiti * limitj);

    private String key = "key";

    @Test
    public void test() throws Exception {
        for (int i = 0; i < limiti; i++) {
            final int ii = i;
            Thread t = new Thread(() -> {
                for (int j = 0; j < limitj; j++) {
                    final String threadName = "B-" + String.valueOf(ii) + "-" + String.valueOf(j);
                    Thread h = new Thread(() -> {
                        try {
                            Random random = new Random();
                            long waitTime = random.nextInt(1000);
                            Thread.sleep(waitTime);
                        } catch (Exception e) {

                        }
                        boolean lock = RedisUtils.getInstance().lockV2(threadName, 500);
                        if (lock) {
                            Long length = RedisUtils.getInstance().llen(key);
                            if (length == null || length < 10) {
                                RedisUtils.getInstance().rpush(key, threadName);
                                LOGGER.info("thread({})推入了数据", threadName);
                            }
                            RedisUtils.getInstance().unlockV2(threadName);
                        }
                        lanch.countDown();
                        LOGGER.info("thread({}) over...", threadName);
                    });
                    h.start();
                }
            });
            t.start();
        }
        lanch.await();

        try {
            Long length = RedisUtils.getInstance().llen(key);
            LOGGER.info("{}", length);
            RedisUtils.getInstance().lrange(key, 0, length).stream().forEach(LOGGER::info);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
