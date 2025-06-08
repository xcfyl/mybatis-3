/*
 *    Copyright 2009-2024 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   * The default value is {@code false} (indicate disable a default value on placeholder) If you specify the
   * {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   *
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   * The default separator is {@code ":"}.
   *
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  /**
   * 是否支持在properties中指定默认值，下面为username这个占位符就指定了一个默认值
   * <dataSource type="POOLED">
   *   <!-- ... -->
   *   <property name="username" value="${username:ut_user}"/> <!-- 如果属性 'username' 没有被配置，'username' 属性的值将为 'ut_user' -->
   * </dataSource>
   */
  private static final String ENABLE_DEFAULT_VALUE = "false";

  /**
   * 默认值的分隔符
   */
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
    // 这个string字符串里面可能有一些占位符，VariableTokenHandler是GenericTokenParser的内部类
    // 实现了TokenHandler接口，该接口接受一个string类型的参数
    // 该对象存储了如下信息：
    // （1）是否支持占位符中包含默认值
    // （2）如果支持，默认值和占位符的分隔符是什么
    // （3）properties环境变量
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    // 创建GenericTokenParser对象，占位符的开始符号和结束符号
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {
    private final Properties variables;
    private final boolean enableDefaultValue;
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      // 环境变量，用于替换字符串文本中的占位符
      this.variables = variables;
      // 是否允许默认值
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      // 默认的值分隔符
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return variables == null ? defaultValue : variables.getProperty(key, defaultValue);
    }

    /**
     * 处理占位符
     *
     * @param content
     * @return
     */
    @Override
    public String handleToken(String content) {
      // 处理的前提是：variables不为空
      if (variables != null) {
        // 获取当前要处理的内容
        String key = content;
        // 判断是否允许默认值
        if (enableDefaultValue) {
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
            key = content.substring(0, separatorIndex);
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }
        }
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      return "${" + content + "}";
    }
  }

}
