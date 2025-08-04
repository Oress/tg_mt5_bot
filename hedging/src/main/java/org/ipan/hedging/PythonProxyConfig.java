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
}
