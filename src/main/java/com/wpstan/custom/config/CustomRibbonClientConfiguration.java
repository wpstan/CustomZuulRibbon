package com.wpstan.custom.config;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import com.wpstan.custom.ribbon.CustomPing;
import com.wpstan.custom.ribbon.CustomRule;
import com.wpstan.custom.ribbon.CustomServerList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.netflix.ribbon.RibbonClientConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @Desc 自定义ribbon相关的配置类，可以在此类中构造IPing、IRule、ServerList等数据。
 *       可以从数据库配置中根据ribbon的service-id来构造不同的IPing、IRule、ServerList等实现类。
 *       当service-id没有构造的时候，会从此类中进行构造。
 * @Author wpstan
 * @Create 2019-12-31 22:21
 */
public class CustomRibbonClientConfiguration extends RibbonClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Override
    public IClientConfig ribbonClientConfig() {
        IClientConfig config = super.ribbonClientConfig();
        //可以增加一些配置，例如Ping后台主机的时间间隔5s
        config.set(CommonClientConfigKey.NFLoadBalancerPingInterval, 5);
        //可以在此设置Ping实现类，也可以使用方法直接构造，例如下面的ribbonPing()方法
        config.set(CommonClientConfigKey.NFLoadBalancerPingClassName,"com.wpstan.custom.ribbon.CustomPing");
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

//    @Bean
//    @ConditionalOnMissingBean
//    @Override
//    public ILoadBalancer ribbonLoadBalancer(IClientConfig config, ServerList<Server> serverList, ServerListFilter<Server> serverListFilter, IRule rule, IPing ping, ServerListUpdater serverListUpdater) {
//        DynamicServerListLoadBalancer loadBalancer = new DynamicServerListLoadBalancer(config, rule, ping, serverList, serverListFilter, serverListUpdater);
//        loadBalancer.addServerStatusChangeListener(new CustomStatusChangeListener(loadBalancer));
//        return loadBalancer;
//    }

//    @Bean
//    @ConditionalOnMissingBean
//    @Override
//    public ServerListUpdater ribbonServerListUpdater(IClientConfig config) {
//        return new PollingServerListUpdater(config);
//    }

}
