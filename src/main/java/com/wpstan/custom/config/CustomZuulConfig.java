package com.wpstan.custom.config;

import com.wpstan.custom.zuul.CustomRouteLocator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Desc 自定义Zuul配置
 * @Author wpstan
 * @Create 2019-12-31 22:10
 */
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
