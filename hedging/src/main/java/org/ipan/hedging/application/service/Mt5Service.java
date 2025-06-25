package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.PythonProxyConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@AllArgsConstructor
public class Mt5Service {
    private RestTemplate restTemplate;
    private PythonProxyConfig pythonProxyConfig;

    public Boolean validateCredentials(Integer accountNumber, String password) {
        UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                .fromUriString(pythonProxyConfig.getUrl())
                .path("/determine-account-type");

        ResponseEntity<Boolean> booleanResponseEntity = restTemplate.postForEntity(componentsBuilder.toUriString(), new ValidateCredsPayload(accountNumber, password), Boolean.class);
        log.info("validate credentials response {}", booleanResponseEntity.toString());
        return booleanResponseEntity.getBody();
    }

    @Data
    @AllArgsConstructor
    private static class ValidateCredsPayload {
        private Integer account_number;
        private String password;
    }
}
