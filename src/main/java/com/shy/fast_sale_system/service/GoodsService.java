package com.shy.fast_sale_system.service;

import com.shy.fast_sale_system.pojo.Goods;
import com.shy.fast_sale_system.vo.GoodsVo;

import java.util.List;

public interface GoodsService {
    Goods getGoodsById(Long goodsId);
    boolean reductionStock(Long goodsId);
    GoodsVo getGoodsVoByGoodsId(Long goodsId);

    /** 获取所有秒杀商品列表（含秒杀价 + Redis实时库存） */
    List<GoodsVo> getSeckillList();
}
