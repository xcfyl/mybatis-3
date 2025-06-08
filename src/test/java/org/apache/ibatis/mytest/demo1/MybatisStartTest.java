package org.apache.ibatis.mytest.demo1;

import java.io.IOException;
import java.io.InputStream;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mytest.demo1.mapper.UserMapper;
import org.apache.ibatis.mytest.demo1.po.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

/**
 * @author 西城风雨楼
 * @date create at 2025/3/8 18:35
 */
public class MybatisStartTest {
    @Test
    public void test() throws IOException {
      // 利用classLoader加载类路径下面的资源，获得资源输入流
      InputStream inputStream = Resources.getResourceAsStream("org/apache/ibatis/mytest/mapper/demo1/mybatis-config.xml");
      // 加载配置文件
      SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(inputStream);

      // 获取SqlSession
      try (SqlSession session = factory.openSession()) {
          UserMapper mapper = session.getMapper(UserMapper.class);
          User user = mapper.selectUserById(1);
          System.out.println(user.getName());
      }
    }
}
