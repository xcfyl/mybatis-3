/*
 *    Copyright 2009-2023 the original author or authors.
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

/**
 * @author Clinton Begin
 */
public class GenericTokenParser {

  private final String openToken;
  private final String closeToken;
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 通用的token解析器
   *
   * @param text
   * @return
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    // token的开始位置
    int start = text.indexOf(openToken);
    if (start == -1) {
      // 如果没找到，说明没有占位符
      return text;
    }
    // 将包含占位符的文本转为char数组
    char[] src = text.toCharArray();
    // 搜索的起始位置
    int offset = 0;
    // 去除转义字符后的文本
    final StringBuilder builder = new StringBuilder();
    // 存放token中的内容，即${}中间的内容
    StringBuilder expression = null;
    do {
      // 如果开始\${，那么说明token开始符号被转义了
      // 为什么这里判断的时候是\\，因为在java中想表示\，需要用\\转义
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        // 把转义字符移除后，将剩下的文本添加到builder中
        builder.append(src, offset, start - offset - 1).append(openToken);
        // 更新下一次开始搜索的位置
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          // 惰性初始化，存放当前找到的token中的内容
          expression = new StringBuilder();
        } else {
          // 如果发现已经有值了，那么清空之前的expression
          expression.setLength(0);
        }
        // 把token之前的文本添加到builder中
        builder.append(src, offset, start - offset);
        // offset更新为token的开始位置，跳过${
        offset = start + openToken.length();
        // 找到token的结束位置
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          // 如果token没有包含转义字符，那么这里记录token表达式的内容
          // end什么时候会小于等于offset？offset的位置就是结束符的位置
          if ((end <= offset) || (src[end - 1] != '\\')) {
            expression.append(src, offset, end - offset);
            break;
          }
          // this close token is escaped. remove the backslash and continue.
          // 如果被转义了，那么移除转义符，将剩余的字符串添加到expression中
          expression.append(src, offset, end - offset - 1).append(closeToken);
          // offset更新为token的结束位置，跳过}，从这个位置重新开始搜索
          offset = end + closeToken.length();
          // 往下一个位置找token的结束位置，因为之前找到的结束位置被转义了
          end = text.indexOf(closeToken, offset);
        }
        // 如果没找到结束位置
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // 处理token中的内容
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      // 从开始搜索位置，搜索下一个开始的token
      start = text.indexOf(openToken, offset);
    } while (start > -1);

    // 将剩余内容添加到builder中
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }

    // 整体来看，builder中最后返回的就是处理后的结果，同时去除了转义字符
    return builder.toString();
  }
}
