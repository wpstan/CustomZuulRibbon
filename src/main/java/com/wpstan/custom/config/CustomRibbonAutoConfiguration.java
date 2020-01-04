package com.wpstan.custom.config;

import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.context.annotation.Configuration;

/**
 * @Desc 自定义ribbon自动配置类
 * @Author wpstan
 * @Create 2019-12-31 22:21
 */
@Configuration(
        proxyBeanMethods = false
)
@RibbonClients(
        defaultConfiguration = {CustomRibbonClientConfiguration.class}
)
public class CustomRibbonAutoConfiguration {
}
