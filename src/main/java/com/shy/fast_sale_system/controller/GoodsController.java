package com.shy.fast_sale_system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shy.fast_sale_system.common.AuthContext;
import com.shy.fast_sale_system.common.Result;
import com.shy.fast_sale_system.mapper.GoodsReviewMapper;
import com.shy.fast_sale_system.pojo.GoodsReview;
import com.shy.fast_sale_system.service.GoodsService;
import com.shy.fast_sale_system.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private GoodsReviewMapper goodsReviewMapper;

    /** 获取秒杀商品列表（所有上架秒杀商品 + 实时库存） */
    @GetMapping("/list")
    public Result<List<GoodsVo>> getSeckillList() {
        List<GoodsVo> list = goodsService.getSeckillList();
        return Result.success(list);
    }

    /** 商品详情（含图片、评论、活动时间窗口） */
    @GetMapping("/detail")
    public Result<GoodsVo> getDetail(@RequestParam("goodsId") Long goodsId) {
        GoodsVo vo = goodsService.getGoodsVoByGoodsId(goodsId);
        if (vo == null) {
            return Result.error("商品不存在");
        }
        return Result.success(vo);
    }

    /** 分页获取商品评论 */
    @GetMapping("/reviews")
    public Result<Map<String, Object>> getReviews(
            @RequestParam("goodsId") Long goodsId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GoodsReview> p = new Page<>(page, size);
        p = goodsReviewMapper.selectPage(p,
                new LambdaQueryWrapper<GoodsReview>()
                        .eq(GoodsReview::getGoodsId, goodsId)
                        .orderByDesc(GoodsReview::getCreateTime));
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return Result.success(data);
    }

    /** 发表评论 */
    @PostMapping("/review")
    public Result<String> postReview(@RequestBody GoodsReview review) {
        Long userId = AuthContext.getUserId();
        if (userId == null) return Result.error("请先登录");
        review.setUserId(userId);
        goodsReviewMapper.insert(review);
        return Result.success("评论发表成功");
    }
}
