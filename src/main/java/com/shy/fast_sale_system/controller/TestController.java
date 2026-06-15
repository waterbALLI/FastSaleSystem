package com.shy.fast_sale_system.controller;

import com.shy.fast_sale_system.mapper.GoodsMapper;
import com.shy.fast_sale_system.pojo.Goods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController//标记这个类是一个控制器（Controller），专门负责接收来自浏览器的请求。
public class TestController {
    @Autowired //自动装配（依赖注入）
    private GoodsMapper goodsMapper;

    @GetMapping("/test")//指明请求路径和请求方式
    public List<Goods> test() {
        // 打印当前线程，确认是否开启了虚拟线程
        System.out.println("当前处理请求的线程是：" + Thread.currentThread());
        // 从数据库查询所有商品
        return goodsMapper.selectList(null);
    }
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/redis-test")
    public Object redisTest() {
        // 尝试读取你在 Docker 命令行里 set 的那个 key
        return redisTemplate.opsForValue().get("mykey");
    }
}//好了，现在我们的基础配置都已经完成了，接下来就是业务了
