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
package org.apache.ibatis.mapping;

import org.apache.ibatis.builder.BuilderException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Vendor DatabaseId provider.
 * <p>
 * It returns database product name as a databaseId. If the user provides a properties it uses it to translate database
 * product name key="Microsoft SQL Server", value="ms" will return "ms". It can return null, if no database product name
 * or a properties was specified and no translation was found.
 *
 * @author Eduardo Macarron
 */
public class VendorDatabaseIdProvider implements DatabaseIdProvider {

  private Properties properties;

  @Override
  public String getDatabaseId(DataSource dataSource) {
    if (dataSource == null) {
      throw new NullPointerException("dataSource cannot be null");
    }
    try {
      return getDatabaseName(dataSource);
    } catch (SQLException e) {
      throw new BuilderException("Error occurred when getting DB product name.", e);
    }
  }

  @Override
  public void setProperties(Properties p) {
    this.properties = p;
  }

  private String getDatabaseName(DataSource dataSource) throws SQLException {
    // 获取数据库的产品名称
    String productName = getDatabaseProductName(dataSource);
    if (properties == null || properties.isEmpty()) {
      // 如果没有指定的属性别名，那么直接返回原生的数据库产品名称
      return productName;
    }
    // 如果用户指定了属性别名，那么就进行匹配，如果没有匹配上，那么返回null
    // 这个返回null是不是有点风险啊，为什么不是返回原始的数据库名称呢，这样还可以有一个兜底
    return properties.entrySet().stream().filter(entry -> productName.contains((String) entry.getKey()))
        .map(entry -> (String) entry.getValue()).findFirst().orElse(null);
  }

  private String getDatabaseProductName(DataSource dataSource) throws SQLException {
    try (Connection con = dataSource.getConnection()) {
      return con.getMetaData().getDatabaseProductName();
    }
  }

}
