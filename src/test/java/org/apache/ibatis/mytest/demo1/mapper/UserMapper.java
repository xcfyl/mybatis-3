package org.apache.ibatis.mytest.demo1.mapper;

import org.apache.ibatis.mytest.demo1.po.User;

/**
 * @author 西城风雨楼
 */
public interface UserMapper {
   User selectUserById(Integer id);
}
