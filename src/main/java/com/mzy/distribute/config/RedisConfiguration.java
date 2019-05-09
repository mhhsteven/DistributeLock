package com.mzy.distribute.config;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

import java.util.List;

@Configuration
public class RedisConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfiguration.class);

    @Autowired
    private RedisProperties redisProperties;

    private ShardedJedisPool jedisPool;

    /**
     * 获取Jedis实例
     *
     * @return
     */
    @Bean
    public ShardedJedisPool getJedisPool() {
        if (jedisPool == null) {
            try {
                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxIdle(redisProperties.getPool().getMaxIdle());
                config.setMinIdle(redisProperties.getPool().getMinIdle());
                config.setMaxTotal(redisProperties.getPool().getMaxActive());
                config.setMaxWaitMillis(redisProperties.getPool().getMaxWait());
                config.setTestOnBorrow(true);

                JedisShardInfo shardsubInfo = new JedisShardInfo(redisProperties.getHost(), redisProperties.getPort());
                shardsubInfo.setPassword(redisProperties.getPassword());
                List<JedisShardInfo> jdsInfoList = Lists.newArrayList(shardsubInfo);

                jedisPool = new ShardedJedisPool(config, jdsInfoList);
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
        return jedisPool;
    }

    public ShardedJedis getResource() {
        return this.getJedisPool() == null ? null : this.getJedisPool().getResource();
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
