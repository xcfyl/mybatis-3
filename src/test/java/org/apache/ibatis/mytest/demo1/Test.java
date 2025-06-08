package org.apache.ibatis.mytest.demo1;

import org.apache.ibatis.parsing.PropertyParser;

import java.util.Properties;

/**
 * @author 西城风雨楼
 * @date create at 2025/6/8 15:42
 */
public class Test {
  public static void main(String[] args) {
    Properties variables = new Properties();
    variables.put("username", "zhangsan");
    String parse = PropertyParser.parse("\\$${username}", variables);
    System.out.println(parse);
  }
}
