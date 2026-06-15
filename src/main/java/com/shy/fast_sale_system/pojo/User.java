package com.shy.fast_sale_system.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
@TableName("t_user")
public class User {
    @TableId
    private Long id;

    private String nickname;

    private String phone;

    @JsonIgnore  // 永远不返回密码给前端
    @TableField("password_hash")
    private String password;
}
