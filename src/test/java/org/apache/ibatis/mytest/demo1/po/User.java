package org.apache.ibatis.mytest.demo1.po;

/**
 * @author 西城风雨楼
 * @date create at 2025/3/8 18:30
 */
public class User {
    private Integer id;
    private String name;
    private Integer age;

    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }
}
