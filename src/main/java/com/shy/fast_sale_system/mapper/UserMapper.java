package com.shy.fast_sale_system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shy.fast_sale_system.pojo.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
