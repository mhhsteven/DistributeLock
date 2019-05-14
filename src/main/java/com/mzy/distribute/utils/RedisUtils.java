package com.mzy.distribute.utils;

import com.alibaba.fastjson.JSON;
import com.mzy.distribute.config.RedisConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import java.util.List;

public class RedisUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisUtils.class);

    private static RedisUtils redisUtils = new RedisUtils();

    private static ShardedJedisPool jedisPool;

    private static final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = 100;

    /**
     * Lock key path.
     */
    private String lockKey = "lock";

    /**
     * 锁超时时间，防止线程在入锁以后，无限的执行等待
     */
    private int expireMsecs = 1;

    /**
     * 锁等待时间，防止线程饥饿
     */
    private int timeoutMsecs = 1;

    private volatile boolean locked = false;

    public synchronized static RedisUtils getInstance() {
        if (redisUtils == null) {
            redisUtils = new RedisUtils();
        }
        return redisUtils;
    }

    private RedisUtils() {
        if (jedisPool == null) {
            RedisConfiguration redisConfiguration = SpringContextUtils.getBean(RedisConfiguration.class);
            jedisPool = redisConfiguration.getJedisPool();
        }
    }

    public <T> T get(final String key, final Class<T> clazz) {
        Object obj = null;
        ShardedJedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            StringRedisSerializer serializer = new StringRedisSerializer();
            String data = jedis.get(key);
            return JSON.parseObject(data, clazz);
        } catch (Exception e) {
            LOGGER.error("get redis error, key : {}", key);
        } finally {
            returnResource(jedis);
        }
        return null;
    }

    public <V> void set(final String key, final V value, int seconds) {
        ShardedJedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.setex(key, seconds, JSON.toJSONString(value));
        } catch (Exception e) {
            LOGGER.error("set redis error, key : {}", key);
        } finally {
            returnResource(jedis);
        }
    }

    public <V> boolean setnx(final String key, final V value) {
        ShardedJedis jedis = null;
        Long result = null;
        try {
            jedis = jedisPool.getResource();
            result = jedis.setnx(key, JSON.toJSONString(value));
        } catch (Exception e) {
            LOGGER.error("setnx redis error, key : {}", key, e);
        } finally {
            returnResource(jedis);
        }
        return result != null && result > 0L;
    }

    public <V> String getSet(final String key, final V value) {
        ShardedJedis jedis = null;
        String oldValue = null;
        try {
            jedis = jedisPool.getResource();
            oldValue = jedis.getSet(key, JSON.toJSONString(value));
        } catch (Exception e) {
            LOGGER.error("getSet redis error, key : {}", key);
        } finally {
            returnResource(jedis);
        }
        return oldValue;
    }

    public Long llen(final String key) {
        ShardedJedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return jedis.llen(key);
        } catch (Exception e) {
            LOGGER.error("llen redis error, key : {}", key);
        } finally {
            returnResource(jedis);
        }
        return null;
    }

    public List<String> lrange(final String key, final long start, final long end) {
        ShardedJedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return jedis.lrange(key, start, end);
        } catch (Exception e) {
            LOGGER.error("lrange redis error, key : {}", key);
        } finally {
            returnResource(jedis);
        }
        return null;
    }

    public Long rpush(final String key, final String... strings) {
        ShardedJedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            return jedis.rpush(key, strings);
        } catch (Exception e) {
            LOGGER.error("rpush redis error, key : {}", key);
        } finally {
            returnResource(jedis);
        }
        return null;
    }

    /**
     * 获得 lock. 实现思路: 主要是使用了redis 的setnx命令,缓存了锁. reids缓存的key是锁的key,所有的共享,
     * value是锁的到期时间(注意:这里把过期时间放在value了,没有时间上设置其超时时间) 执行过程:
     * 1.通过setnx尝试设置某个key的值,成功(当前没有这个锁)则返回,成功获得锁
     * 2.锁已经存在则获取锁的到期时间,和当前时间比较,超时的话,则设置新的值
     *
     * @return true if lock is acquired, false acquire timeouted
     * @throws InterruptedException in case of thread interruption
     */
    public synchronized boolean lock(String threadName) {
        int timeout = timeoutMsecs;
        while (timeout >= 0) {
            long expires = System.currentTimeMillis() + expireMsecs + 1;
            String expiresStr = String.valueOf(expires); // 锁到期时间
            boolean flag = this.setnx(lockKey, expiresStr);
            if (flag) {
                // lock acquired
                locked = true;
                LOGGER.info("thread{}抢到了", threadName);
                return true;
            }

            String currentValueStr = this.get(lockKey, String.class); // redis里的时间
            if (currentValueStr != null && Long.parseLong(currentValueStr) < System.currentTimeMillis()) {
                // 判断是否为空，不为空的情况下，如果被其他线程设置了值，则第二个条件判断是过不去的
                // lock is expired

                String oldValueStr = this.getSet(lockKey, expiresStr);
                // 获取上一个锁到期时间，并设置现在的锁到期时间，
                // 只有一个线程才能获取上一个线上的设置时间，因为jedis.getSet是同步的
                if (oldValueStr != null && oldValueStr.equals(currentValueStr)) {
                    // 防止误删（覆盖，因为key是相同的）了他人的锁——这里达不到效果，这里值会被覆盖，但是因为什么相差了很少的时间，所以可以接受

                    // [分布式的情况下]:如过这个时候，多个线程恰好都到了这里，但是只有一个线程的设置值和当前值相同，他才有权利获取锁
                    // lock acquired
                    locked = true;
                    return true;
                }
            }
            timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;

            /*
             * 延迟100 毫秒, 这里使用随机时间可能会好一点,可以防止饥饿进程的出现,即,当同时到达多个进程,
             * 只会有一个进程获得锁,其他的都用同样的频率进行尝试,后面有来了一些进行,也以同样的频率申请锁,这将可能导致前面来的锁得不到满足.
             * 使用随机的等待时间可以一定程度上保证公平性
             */
            try {
                Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS);
            } catch (Exception e) {

            }

        }
        return false;
    }

    public void lockV2(String threadName) {
        long lockTime = System.currentTimeMillis();
        String lockTimeStr = String.valueOf(lockTime);
//        int count = 0;
        while (true) {
            if (this.setnx(lockKey, lockTimeStr)) {
                LOGGER.info("thread({})抢到了", threadName);
                return;
            }
//            LOGGER.info("thread({})没抢到，尝试了{}次", threadName, ++count);
        }
    }

    /**
     * Acqurired lock release.
     */
    public void unlock(String threadName) {
        if (locked) {
            ShardedJedis jedis = null;
            try {
                jedis = jedisPool.getResource();
                jedis.del(lockKey);
                locked = false;
                LOGGER.info("thread({})释放了锁", threadName);
            } catch (Exception e) {
                LOGGER.error("unlock redis error, key : {}", lockKey);
            } finally {
                returnResource(jedis);
            }
        }
    }

    /**
     * Acqurired lock release.
     */
    public void unlockV2(String threadName) {
        ShardedJedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            jedis.del(lockKey);
            LOGGER.info("thread{}释放了锁", threadName);
        } catch (Exception e) {
            LOGGER.error("unlock redis error, key : {}", lockKey);
        } finally {
            returnResource(jedis);
        }
    }

    /**
     * 释放jedis资源
     *
     * @param jedis
     */
    public void returnResource(final ShardedJedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }
}
