package com.nursena.payflow.clientcontext.adapter.in.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    TrustedProxyProperties.class
)
class TrustedClientContextConfiguration {
}
