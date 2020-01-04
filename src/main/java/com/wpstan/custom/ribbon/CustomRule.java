package com.wpstan.custom.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import com.netflix.zuul.context.RequestContext;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @Desc RibbonRoutingFilter过滤器会根据请求的url地址，首先匹配service-id，
 *       然后根据service-id匹配的IRule实现了你，从IRule中获取一个目的主机地址，
 *       当前CustomRule采用IP Hash算法，同时根据ServerStatus维护的主机状态获取目的主机。
 * @Author wpstan
 * @Create 2019-12-31 22:20
 */
public class CustomRule extends AbstractLoadBalancerRule {


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

    @Override
    public Server choose(Object key) {
        return this.choose(this.getLoadBalancer(), key);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
    }
}
