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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(Configuration.class, reader, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, Reader reader, String environment,
      Properties props) {
    this(configClass, new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    // 创建XMLConfigBuilder
    this(Configuration.class, inputStream, environment, props);
  }

  public XMLConfigBuilder(Class<? extends Configuration> configClass, InputStream inputStream, String environment,
      Properties props) {
    // XPathParser
    // 通过xpath语法解析xml中的元素
    // XMLMapperEntityResolver
    // 解析xml的时候需要验证xml语法的合法性，默认验证方式是dtd的方式，而dtd文件默认需要联网下载，为了避免网络太差无法下载
    // mybatis通过实现XMLMapperEntityResolver，可以避免走网络加载而是直接走本地文件加载
    // 创建XPathParser的时候，其实就已经创建好mybatis-config.xml的Document对象了
    this(configClass, new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * XMLConfigBuilder其实是通过XPathParser解析mybatis-config.xml的，然后根据mybatis-config.xml的元素进行解析
   *
   * @param configClass
   * @param parser
   * @param environment
   * @param props
   */
  private XMLConfigBuilder(Class<? extends Configuration> configClass, XPathParser parser, String environment,
      Properties props) {
    // 通过反射的方式创建Configuration对象
    super(newConfig(configClass));
    // todo 暂时不知道这个ErrorContext是为了做什么用的
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // Configuration对象中存放额外的配置信息
    this.configuration.setVariables(props);
    // 是否解析的标识位，目前还是未解析的状态
    this.parsed = false;
    // 激活的环境，因为在mybatis-config.xml文件中，可以配置多个环境，这里选择要使用哪个环境
    this.environment = environment;
    // XPathParser，解析xml文件全靠这个类了，因为创建这个类的时候，已经创建好了
    // mybatis-config.xml文件的Document对象
    this.parser = parser;
  }

  public Configuration parse() {
    // 如果已经加载了，那么直接抛异常，并不能保证线程安全
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // 将解析的标识位设置为true
    parsed = true;
    // 解析mybatis的配置，configuration是mybatis-config.xml的根元素
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 从xml中解析mybatis-config.xml，将其转为Configuration对象
   *
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      // 解析configuration元素下面的properties标签
      propertiesElement(root.evalNode("properties"));
      // 解析configuration元素下面的settings标签，并将setting标签中的元素转为键值对形式
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // todo 在mybatis里面vfs是一个什么概念？虚拟文件系统吗？是干什么的？
      loadCustomVfsImpl(settings);
      // 加在自定义日志实现
      loadCustomLogImpl(settings);
      // 解析configuration元素下面的typeAliases标签
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析configuration元素下面的plugin标签
      pluginsElement(root.evalNode("plugins"));
      // 解析configuration元素下面的objectFactory标签
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析configuration元素下面的objectWrapperFactory标签
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析configuration元素下面的reflectorFactory标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 解析configuration元素下面的setting标签
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析configuration元素下面的environments标签
      environmentsElement(root.evalNode("environments"));
      // 解析configuration元素下面的databaseIdProvider标签
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析configuration元素下面的typeHandlers标签
      typeHandlersElement(root.evalNode("typeHandlers"));
      // 解析configuration元素下面的mappers标签
      mappersElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 获取settings标签下面的子元素
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 获取Configuration类的元类型信息，MetaClass是mybatis自己封装的，用于封装JavaBean的元信息，比如属性名，属性类型，属性的getter和setter方法等等
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    // 校验settings标签下面的子元素的key是否合法
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException(
            "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfsImpl(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value == null) {
      return;
    }
    String[] clazzes = value.split(",");
    for (String clazz : clazzes) {
      if (!clazz.isEmpty()) {
        @SuppressWarnings("unchecked")
        Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
        configuration.setVfsImpl(vfsImpl);
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode context) {
    if (context == null) {
      return;
    }
    // 获取typeAliases标签下面的子元素
    for (XNode child : context.getChildren()) {
      // 如果发现package标签，那么就进行批量注册，包下面的类都会进行注册
      if ("package".equals(child.getName())) {
        String typeAliasPackage = child.getStringAttribute("name");
        configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
      } else {
        // 如果name不是package标签，那么就是typeAlias标签，进行单个注册
        // 获取typeAlias标签的alias属性和type属性
        // 其中alias属性是别名，type属性是要注册别名的类
        String alias = child.getStringAttribute("alias");
        String type = child.getStringAttribute("type");
        try {
          // 获取type对应的class对象
          Class<?> clazz = Resources.classForName(type);
          if (alias == null) {
            // 如果alias属性为空，那么默认通过注解的方式或者通过类名进行注册别名
            typeAliasRegistry.registerAlias(clazz);
          } else {
            // 否则直接使用给定的别名进行注册
            typeAliasRegistry.registerAlias(alias, clazz);
          }
        } catch (ClassNotFoundException e) {
          throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
        }
      }
    }
  }

  private void pluginsElement(XNode context) throws Exception {
    if (context != null) {
      // 获取plugins标签下面的子元素
      for (XNode child : context.getChildren()) {
        // 获取子元素plugin的interceptor属性，interceptor属性是拦截器的实现类，由该类拦截
        // mybatis执行SQL的部分关键流程，比如改写sql
        String interceptor = child.getStringAttribute("interceptor");
        // 获取子元素plugin的子元素，并将其转换为键值对
        Properties properties = child.getChildrenAsProperties();
        // 通过反射的方式创建拦截器实例
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor()
            .newInstance();
        // 设置拦截器实例需要的属性
        interceptorInstance.setProperties(properties);
        // 注册拦截器
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * mybatis创建对象的时候，默认是使用ObjectFactory创建对象，ObjectFactory默认是DefaultObjectFactory
   * 为了方便扩展，这里提供objectFactory属性，用户可以自定义ObjectFactory
   *
   * @param context
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 首先获取objectFactory标签的type属性
      String type = context.getStringAttribute("type");
      // 获取objectFactory标签的子元素
      Properties properties = context.getChildrenAsProperties();
      // 通过空参数构造函数创建对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置属性
      factory.setProperties(properties);
      // 设置对象工厂
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获取reflectorFactory标签的type属性
      String type = context.getStringAttribute("type");
      // 通过反射创建ReflectorFactory对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置ReflectorFactory
      configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    // 获取properties标签下面的所有子元素
    Properties defaults = context.getChildrenAsProperties();
    // 获取properties标签的resource属性，resource属性可以指定一个外部的properties文件
    // mybatis会获取该文件中定义的键值对数据
    String resource = context.getStringAttribute("resource");
    // 获取properties标签的url属性，url属性可以指定从网络中加载数据
    String url = context.getStringAttribute("url");
    // url和resource属性不能同时存在，如果同时存在抛出异常
    if (resource != null && url != null) {
      throw new BuilderException(
          "The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
    }
    if (resource != null) {
      // 从文件中加载properties
      defaults.putAll(Resources.getResourceAsProperties(resource));
    } else if (url != null) {
      // 从网络中加载properties
      defaults.putAll(Resources.getUrlAsProperties(url));
    }
    // 这个是手动设置的键值对
    Properties vars = configuration.getVariables();
    if (vars != null) {
      defaults.putAll(vars);
    }
    // parser自己也要缓存一份，可能是为了解析的时候用吧
    // 为什么要做这种数据隔离呢
    parser.setVariables(defaults);
    // 这里相当于是拿全了的键值对
    configuration.setVariables(defaults);
  }

  private void settingsElement(Properties props) {
    configuration
        .setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(
        AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    // 参考这篇博客对executorType的设置
    // https://wch853.github.io/posts/mybatis/MyBatis%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90%EF%BC%88%E5%85%AB%EF%BC%89%EF%BC%9A%E6%89%A7%E8%A1%8C%E5%99%A8.html#%E5%88%9B%E5%BB%BA%E6%89%A7%E8%A1%8C%E5%99%A8
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(
        stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
    configuration.setArgNameBasedConstructorAutoMapping(
        booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
    configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
    configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    if (environment == null) {
      // 记录默认环境名称
      environment = context.getStringAttribute("default");
    }
    for (XNode child : context.getChildren()) {
      String id = child.getStringAttribute("id");
      // 这里只记录默认的环境名称，非默认的不进行解析了
      if (isSpecifiedEnvironment(id)) {
        // 获取事务管理器工厂
        TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
        // 获取数据源工厂
        DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
        // 从工厂中获取数据源
        DataSource dataSource = dsFactory.getDataSource();
        // 创建Environment对象，记录事务管理器工厂和数据源
        Environment.Builder environmentBuilder = new Environment.Builder(id).transactionFactory(txFactory)
            .dataSource(dataSource);
        // 设置environment对象
        configuration.setEnvironment(environmentBuilder.build());
        break;
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    String type = context.getStringAttribute("type");
    // awful patch to keep backward compatibility
    // 为了向前兼容，所以这里配置VENDOR和DB_VENDOR是一样的
    if ("VENDOR".equals(type)) {
      type = "DB_VENDOR";
    }
    Properties properties = context.getChildrenAsProperties();
    // 根据类型反射获取对应的DatabaseIdProvider，如果是DB_VENDOR的话，默认使用的是VendorDatabaseIdProvider
    // VendorDatabaseIdProvider主要是根据数据库的ProductName进行匹配，如果在properties中指定了数据库的别名，那么优先找第一个能匹配上的
    DatabaseIdProvider databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor()
        .newInstance();
    databaseIdProvider.setProperties(properties);
    Environment environment = configuration.getEnvironment();
    if (environment != null) {
      // 获取数据库标识，后面可以根据这个标识去执行指定的SQL
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 获取事务管理器工厂的类型
      String type = context.getStringAttribute("type");
      // 获取子元素
      Properties props = context.getChildrenAsProperties();
      // resolveClass，是根据类型或者别名来获取对象，然后通过反射的方式创建对象
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      // 设置属性
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlersElement(XNode context) {
    if (context == null) {
      return;
    }

    // 获取typeHandlers标签下面的所有子元素
    for (XNode child : context.getChildren()) {
      // 如果子元素名称是package的话
      if ("package".equals(child.getName())) {
        // 获取package标签的name属性，name属性是一个包名
        String typeHandlerPackage = child.getStringAttribute("name");
        // 该包下面的所有实现了TypeHandler接口的类都会被注册为TypeHandler
        typeHandlerRegistry.register(typeHandlerPackage);
      } else {
        String javaTypeName = child.getStringAttribute("javaType");
        String jdbcTypeName = child.getStringAttribute("jdbcType");
        String handlerTypeName = child.getStringAttribute("handler");
        Class<?> javaTypeClass = resolveClass(javaTypeName);
        JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
        Class<?> typeHandlerClass = resolveClass(handlerTypeName);
        if (javaTypeClass != null) {
          if (jdbcType == null) {
            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
          } else {
            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
          }
        } else {
          typeHandlerRegistry.register(typeHandlerClass);
        }
      }
    }
  }

  private void mappersElement(XNode context) throws Exception {
    if (context == null) {
      return;
    }
    // 获取mappers标签下的所有子元素
    for (XNode child : context.getChildren()) {
      // 如果子元素的名称是package
      if ("package".equals(child.getName())) {
        // 获取name属性，name是包路径
        String mapperPackage = child.getStringAttribute("name");
        // 添加这个包下面的所有Mapper接口到配置中
        configuration.addMappers(mapperPackage);
      } else {
        String resource = child.getStringAttribute("resource");
        String url = child.getStringAttribute("url");
        String mapperClass = child.getStringAttribute("class");
        if (resource != null && url == null && mapperClass == null) {
          ErrorContext.instance().resource(resource);
          try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                configuration.getSqlFragments());
            mapperParser.parse();
          }
        } else if (resource == null && url != null && mapperClass == null) {
          ErrorContext.instance().resource(url);
          try (InputStream inputStream = Resources.getUrlAsStream(url)) {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url,
                configuration.getSqlFragments());
            mapperParser.parse();
          }
        } else if (resource == null && url == null && mapperClass != null) {
          Class<?> mapperInterface = Resources.classForName(mapperClass);
          configuration.addMapper(mapperInterface);
        } else {
          throw new BuilderException(
              "A mapper element may only specify a url, resource or class, but not more than one.");
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    }
    if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    }
    return environment.equals(id);
  }

  private static Configuration newConfig(Class<? extends Configuration> configClass) {
    try {
      return configClass.getDeclaredConstructor().newInstance();
    } catch (Exception ex) {
      throw new BuilderException("Failed to create a new Configuration instance.", ex);
    }
  }

}
