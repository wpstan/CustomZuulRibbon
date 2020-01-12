## 前言
脱离Eureka等服务注册与发现组件，使用Zuul和Ribbon来做网关负载均衡，可以在application.properties中配置listOfServer指定后台主机地址。
然而这种方式存在一些弊端，强依赖配置文件，无法做到实时变更和维护。

通过构建自定义的路由加载类、service-id等配置，可以实现从数据库或者其他地方加载路有规则。
同时还可以实现自定义的ServerList、IRule、IPing等来维护网关的可扩展性。

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
``` java
@Configuration
public class CustomZuulConfig {

    private final ZuulProperties zuulProperties;

    private final ServerProperties serverProperties;
    @Autowired
    public CustomZuulConfig(ZuulProperties zuulProperties,ServerProperties serverProperties){
        this.zuulProperties = zuulProperties;
        this.serverProperties = serverProperties;

    }

    @Bean
    public CustomRouteLocator routeLocator(){
        CustomRouteLocator routeLocator = new CustomRouteLocator(serverProperties.getServlet().getContextPath(), zuulProperties);
        return routeLocator;
    }
}
``` 
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
``` java
public class CustomRibbonClientConfiguration extends RibbonClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Override
    public IClientConfig ribbonClientConfig() {
        IClientConfig config = super.ribbonClientConfig();
        //可以增加一些配置，例如Ping后台主机的时间间隔5s
        config.set(CommonClientConfigKey.NFLoadBalancerPingInterval, 5);
        //可以在此设置Ping实现类（无需重写ribbonPing，base中会根据如下配置反射构造IPing），也可以使用方法直接构造，例如下面的ribbonPing()方法
        config.set(CommonClientConfigKey.NFLoadBalancerPingClassName,"com.wpstan.custom.ribbon.CustomPing");
        //设置MaxAutoRetries为2，确保某个服务挂的瞬间，ServerStats的Successive Connection Failure大于或等于
        //ServerStats中的connectionFailureThreshold的默认次数3。这样ServerStats的isCircuitBreakerTripped才被置位短路。
        //在Retry重试的时候，CustomRule中Choose下一个服务的时候，确保之前的服务已经短路，选用下一个服务，否则可能继续选择不可用服务导致报错。
        //还有一种方式就是调小ServerStats的connectionFailureThreshold。
        config.set(CommonClientConfigKey.MaxAutoRetries, 2);
        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    @Override
    public ServerList<Server> ribbonServerList(IClientConfig config) {
        CustomServerList serverList = new CustomServerList();
        serverList.initWithNiwsConfig(config);
        return serverList;
    }

    @Bean
    @ConditionalOnMissingBean
    @Override
    public IRule ribbonRule(IClientConfig config) {
        CustomRule rule = new CustomRule();
        rule.initWithNiwsConfig(config);
        return rule;
    }

    @Bean
    @ConditionalOnMissingBean
    @Override
    public IPing ribbonPing(IClientConfig config) {
        return new CustomPing();
    }
}
```

### CustomServerList.java
自定义ServerList类，等价于重写listOfServer配置，通过getUpdatedListOfServers来维护service-id对应的后台主机服务。
PollingServerListUpdater类定时任务更新时，会调用getUpdatedListOfServers方法。
``` java
public class CustomServerList implements ServerList<Server>, IClientConfigAware {
    private IClientConfig clientConfig;

    @Override
    public List<Server> getInitialListOfServers() {
        return getUpdatedListOfServers();
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        List<Server> servers = new ArrayList<>();
        Server server1 = new Server("http://localhost:9000");
        Server server2 = new Server("http://localhost:9001");
        servers.add(server1);
        servers.add(server2);
        return servers;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
        this.clientConfig = iClientConfig;
    }
}

```
### CustomPing.java
自定义IPing类，通过定时任务，维护后台机器活性，采用HttpClient与后台机器通信。
``` java
 @Override
    public boolean isAlive(Server server) {
        String urlStr = "";
        if (this.isSecure) {
            urlStr = "https://";
        } else {
            urlStr = "http://";
        }

        urlStr = urlStr + server.getId();
        urlStr = urlStr + this.getPingAppendString();
        boolean isAlive = false;
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpUriRequest getRequest = new HttpGet(urlStr);
        String content = null;

        try {
            HttpResponse response = httpClient.execute(getRequest);
            content = EntityUtils.toString(response.getEntity());
            isAlive = response.getStatusLine().getStatusCode() == 200;
            if (this.getExpectedContent() != null) {
                if (content == null) {
                    isAlive = false;
                } else if (content.equals(this.getExpectedContent())) {
                    isAlive = true;
                } else {
                    isAlive = false;
                }
            }
        } catch (IOException var11) {
            var11.printStackTrace();
        } finally {
            getRequest.abort();
        }

        return isAlive;
    }
```
### CustomRule.java
自定义IRule类，RibbonRoutingFilter过滤器会根据请求的url地址，首先匹配service-id，
然后根据service-id匹配的IRule实现类，从IRule中获取一个目的主机地址，当前CustomRule采用IP Hash算法，同时根据ServerStatus维护的主机状态获取目的主机。
``` java
public Server choose(ILoadBalancer lb, Object key) {
        //首先获取可用的后台主机，可用的后台主机会根据定时任务不断调用ServerList中的getUpdatedListOfServers()方法来维护
        List<Server> upServerList = lb.getReachableServers();
        RequestContext requestContext = RequestContext.getCurrentContext();
        HttpServletRequest request = requestContext.getRequest();
        //获取IP地址，根据IP地址做Hash
        String ip = request.getRemoteAddr();
        int hashCode = Math.abs(ip.hashCode());
        int num = hashCode % upServerList.size();
        LoadBalancerStats loadBalancerStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();
        //ServerStats类维护的是后台主机的状态
        ServerStats serverStats = loadBalancerStats.getSingleServerStat(upServerList.get(num));
        System.out.println("当前机器失败次数：" + serverStats.getServer().getId()+" "+serverStats.getSuccessiveConnectionFailureCount());
        int count = 0;
        //轮训判断后台服务是否短路挂起
        while (serverStats.isCircuitBreakerTripped()) {
            if (++count >= upServerList.size()) {
                return null;
            }
            num = (num + 1) % upServerList.size();
            serverStats = loadBalancerStats.getSingleServerStat(upServerList.get(num));
        }
        System.out.println("#####:选择了" + serverStats.getServer().getId());
        return upServerList.get(num);
    }
```
