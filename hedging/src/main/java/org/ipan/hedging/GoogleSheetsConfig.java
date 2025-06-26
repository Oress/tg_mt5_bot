package org.ipan.hedging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.gsheets")
@Data
public class GoogleSheetsConfig {
    private String saJson;
    private List<String> emails;
}
