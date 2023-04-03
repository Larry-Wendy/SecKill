package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author larry
 * @since 2023-03-30
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
