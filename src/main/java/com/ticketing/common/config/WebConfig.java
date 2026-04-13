package com.ticketing.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ticketing.common.filter.CorrelationIdFilter;

@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
        CorrelationIdFilter correlationIdFilter) {
        FilterRegistrationBean<CorrelationIdFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(correlationIdFilter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
