package com.mzy.redis;

import com.mzy.distribute.SpringBaseTest;
import com.mzy.distribute.config.RedisConfiguration;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class JedisTest extends SpringBaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisTest.class);

    private int limiti = 1;

    private int limitj = 1000;

    private CountDownLatch lanch = new CountDownLatch(limiti * limitj);

    private String key = "key";

    @Autowired
    private ShardedJedisPool shardedJedisPool;

    @Test
    public void test() throws Exception {
        for (int i = 0; i < limiti; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < limitj; j++) {
                    Thread h = new Thread(() -> {
                        try {
                            Random random = new Random();
                            ShardedJedis jedis = shardedJedisPool.getResource();
                            if (jedis.llen(key) < 10) {
                                jedis.rpush(key, random.nextInt(100000000) + "");
                            }
                        } catch (Throwable e) {
                        }
                        lanch.countDown();
                    });
                    h.start();
                }
            });
            t.start();
        }
        lanch.await();

        try {
            ShardedJedis jedis = shardedJedisPool.getResource();
            Long length = jedis.llen(key);
            LOGGER.info("{}", length);
            jedis.lrange(key, 0, length).stream().forEach(LOGGER::info);
        } catch (Exception e) {
        }

    }
}
