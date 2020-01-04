## 前言
脱离Eureka等服务注册与发现组件，使用Zuul和Ribbon来做网关负载均衡，可以在application.properties中配置listOfServer指定后台主机地址。
然而这种方式存在一些弊端，强依赖配置文件，无法做到实时变更和维护。

通过构建自定义的路由加载类、service-id等配置，可以实现从数据库加载路有规则。
同时实现自定义的ServerList、IRule、IPing等来维护网关的可扩展性。

### application.properties
采用配置文件的路由规则配置通常采用如下方式。
``` yaml
#定义名为ironman的路由规则，负载均衡的主机使用ribbon的listOfServers配置
#可以采用以下方式配置路由规则的IRule、IPing、ServerList等
#<clientName>.ribbon.NFLoadBalancerClassName: Should implement ILoadBalancer
#<clientName>.ribbon.NFLoadBalancerRuleClassName: Should implement IRule
#<clientName>.ribbon.NFLoadBalancerPingClassName: Should implement IPing
#<clientName>.ribbon.NIWSServerListClassName: Should implement ServerList
#<clientName>.ribbon.NIWSServerListFilterClassName: Should implement ServerListFilter

zuul.routes.ironman.path=/**
zuul.routes.ironman.service-id=fly
fly.ribbon.listOfServers=http://localhost:9000,http://localhost:9001
fly.ribbon.NFLoadBalancerRuleClassName=com.wpstan.custom.ribbon.CustomRule
fly.ribbon.NFLoadBalancerPingClassName=com.wpstan.custom.ribbon.CustomPing
fly.ribbon.NIWSServerListClassName=com.wpstan.custom.ribbon.CustomServerList
```
### CustomZuulConfig.java
自定义Zuul配置类，采用CustomRouteLocator类来加载路由规则。

### CustomRouteLocator.java
自定义Zuul路由规则加载类，重写locateRoutes方法，首先从配置加载，再从其他地方加载。
``` java
    ZuulProperties.ZuulRoute zuulRoute = new ZuulProperties.ZuulRoute();
    zuulRoute.setId("ironman");
    zuulRoute.setServiceId("fly");
    zuulRoute.setPath("/**");
    routesMap.put(zuulRoute.getPath(), zuulRoute);
```

### CustomRibbonAutoConfiguration.java
自定义Ribbon的配置类，用以指定RibbonClient采用CustomRibbonClientConfiguration类进行初始化。
``` java
@RibbonClients(
        defaultConfiguration = {CustomRibbonClientConfiguration.class}
)
```

### CustomRibbonClientConfiguration.java
自定义ribbon相关的配置类，可以在此类中构造IPing、IRule、ServerList等数据。
可以从数据库配置中根据ribbon的service-id来构造不同的IPing、IRule、ServerList等实现类。
当service-id没有构造的时候，会从此类中进行构造。

### CustomServerList.java
自定义ServerList类，等价于重写listOfServer配置，通过getUpdatedListOfServers来维护service-id对应的后台主机服务。
PollingServerListUpdater类定时任务更新时，会调用getUpdatedListOfServers方法。

### CustomPing.java
自定义IPing类，通过定时任务，维护后台机器活性，采用HttpClient与后台机器通信。

### CustomRule.java
自定义IRule类，RibbonRoutingFilter过滤器会根据请求的url地址，首先匹配service-id，
然后根据service-id匹配的IRule实现了你，从IRule中获取一个目的主机地址，当前CustomRule采用IP Hash算法，同时根据ServerStatus维护的主机状态获取目的主机。
