package org.ipan.hedging;

import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.domain.AppConstants;
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

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean(name = MAIN_MT5)
    @Primary
    public Mt5Service getMainMt5Service(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        log.info("Whole config is {}", proxyConfig);
        pythonProxyConfig.setUrl(proxyConfig.getUrl());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY1)
    public Mt5Service getAuxMt5Service(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl1());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY2)
    public Mt5Service getMt5Service_2(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl2());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY3)
    public Mt5Service getMt5Service_3(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl3());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY4)
    public Mt5Service getMt5Service_4(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl4());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY5)
    public Mt5Service getMt5Service_5(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl5());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY6)
    public Mt5Service getMt5Service_6(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl6());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY7)
    public Mt5Service getMt5Service_7(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl7());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY8)
    public Mt5Service getMt5Service_8(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl8());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY9)
    public Mt5Service getMt5Service_9(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl9());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

    @Bean(name = AppConstants.TERMINAL_PROXY10)
    public Mt5Service getMt5Service_10(RestTemplate restTemplate, PythonProxyConfig proxyConfig, PasswordService passwordService) {
        PythonProxyConfig pythonProxyConfig = new PythonProxyConfig();
        pythonProxyConfig.setUrl(proxyConfig.getUrl10());
        return new Mt5ServiceImpl(
                restTemplate,
                pythonProxyConfig,
                passwordService
        );
    }

}
