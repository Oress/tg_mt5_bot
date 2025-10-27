package org.ipan.hedging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.python-http-proxy")
@Data
public class PythonProxyConfig {
    private String url;

    private String url1;
    private String url2;
    private String url3;
    private String url4;
    private String url5;
    private String url6;
    private String url7;
    private String url8;
    private String url9;
    private String url10;
}
