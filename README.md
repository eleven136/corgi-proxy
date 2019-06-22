<div align=center><img width="350" height="262" src="https://github.com/gaoxianglong/corgi-proxy/blob/master/resources/imgs/corgi-logo.jpeg"/></div>

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html) [![Join the chat at https://gitter.im/gaoxianglong/shark](https://badges.gitter.im/gaoxianglong/shark.svg)](https://gitter.im/gaoxianglong/shark?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) ![License](https://img.shields.io/badge/version-0.1--SNAPSHOT-blue.svg)
> 最终一致性、增量数据拉取，且去中心化的Registry中间件<br/>
> 大规模服务化场景下注册中心性能瓶颈解决方案<br/>
## 项目背景
Zookeeper并不适合作为大规模服务化场景下的Registry，其写操作存在单点问题，不可扩展，由于采用的原子广播协议（>=N/2+1），集群节点越多，写入效率就越低，表象就是应用启动异常缓慢；其次服务节点的任何变化，都会导致消费者每次都全量拉取一个服务接口下的所有地址列表，大促前后服务的扩容/缩容操作所带来的瞬时流量容易瞬间就把Registry集群的的网卡打满。**因此我们需要一个最终一致性、增量数据拉取，且去中心化的Registry解决方案，corgi-proxy由此诞生**。

## corgi-proxy使用
- corgi-proxy基于SPI扩展了dubbo的注册中心:
```java
<dubbo:registry protocol="corgi" id="corgi" address="${host}"/>
```
- 在src/resources目录下新建META-INF/dubbo/com.alibaba.dubbo.registry.RegistryFactory文件：
```java
corgi=com.github.registry.corgi.CorgiRegistryFactory
```
- 非dubbo项目，原生客户端API的使用：
```java
CorgiFramework framework = new CorgiFramework.Builder(new HostAndPort(hostName, port))//绑定corgi-server的host和port
        .redirections(redirections)//指定重试次数
        .serialization(CorgiFramework.SerializationType.FST)//指定序列化协议
        .builder()
        .init();//相关初始化
framework.register("/dubbo/service", "127.0.0.1:20890");//服务注册
framework.subscribe("/dubbo/service");//订阅
framework.unRegister("/dubbo/service", "127.0.0.1:20890");//取消注册
Executors.newSingleThreadScheduledExecutor().execute(() -> {
    while (true) {
        framework.subscribe("/dubbo/service");//持续订阅
    }
});
```
目前corgi-proxy暂未提交到Maven中央仓库，请自行编译打包；更多使用案例，参考corgi-proxy-demo。