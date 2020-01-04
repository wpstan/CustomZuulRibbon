package com.wpstan.custom.zuul;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.RefreshableRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.SimpleRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Desc 自定义Zuul的路由配置
 * @Author wpstan
 * @Create 2019-12-31 22:10
 */
public class CustomRouteLocator extends SimpleRouteLocator implements RefreshableRouteLocator {

    @Autowired
    ZuulProperties properties;

    public CustomRouteLocator(String servletPath, ZuulProperties properties) {
        super(servletPath, properties);
    }

    /**
     * 获取自定义路由规则，可以来自数据库
     *
     * @return
     */
    @Override
    protected Map<String, ZuulProperties.ZuulRoute> locateRoutes() {
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routesMap = new LinkedHashMap<>();
        //将父类中的路由规则存入HashMap，父类加载的是配置文件中的路由规则
        routesMap.putAll(super.locateRoutes());
        //从自定义的加载方式加载路由规则
        locateRoutesFromDb(routesMap);
        return routesMap;
    }

    /**
     * zuul.routes.ironman.path=/**
     * zuul.routes.ironman.service-id=fly
     * fly.ribbon.listOfServers=http://localhost:9000,http://localhost:9001
     * fly.ribbon.NFLoadBalancerRuleClassName=com.wpstan.custom.ribbon.CustomRule
     * fly.ribbon.NFLoadBalancerPingClassName=com.wpstan.custom.ribbon.CustomPing
     * fly.ribbon.NIWSServerListClassName=com.wpstan.custom.ribbon.CustomServerList
     * @param routesMap
     */
    private void locateRoutesFromDb(LinkedHashMap routesMap){
        ZuulProperties.ZuulRoute zuulRoute = new ZuulProperties.ZuulRoute();
        zuulRoute.setId("ironman");
        zuulRoute.setServiceId("fly");
        zuulRoute.setPath("/**");
        routesMap.put(zuulRoute.getPath(), zuulRoute);
    }











    @Override
    public void refresh() {

    }
}
