package com.shy.fast_sale_system.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shy.fast_sale_system.pojo.Goods;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    // 继承 BaseMapper<Goods> 后，你已经拥有了 selectById, insert, update 等所有方法
}
