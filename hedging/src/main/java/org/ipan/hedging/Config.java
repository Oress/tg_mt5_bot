package org.ipan.hedging;

import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.domain.service.PasswordService;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableRetry
@EnableAsync
@EnableJpaRepositories
@EnableAspectJAutoProxy
@Slf4j
public class Config {
    public static final String MAIN_MT5 = "mainMt5";
    public static final String AUX_MT5 = "auxMt5";

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Bean(name = MAIN_MT5)
    @Primary
    public Mt5Service getMainMt5Service(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AUX_MT5)
    public Mt5Service getAuxMt5Service(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl1());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
