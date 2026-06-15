package com.shy.fast_sale_system.controller;

import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.service.GoodsService;
import com.shy.fast_sale_system.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    /** 获取秒杀商品列表（所有上架秒杀商品 + 实时库存） */
    @GetMapping("/list")
    public Result<List<GoodsVo>> getSeckillList() {
        List<GoodsVo> list = goodsService.getSeckillList();
        return Result.success(list);
    }
}
