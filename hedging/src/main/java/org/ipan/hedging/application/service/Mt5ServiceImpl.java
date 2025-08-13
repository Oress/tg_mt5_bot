package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.PythonProxyConfig;
import org.ipan.hedging.domain.model.AccountType;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.service.PasswordService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@AllArgsConstructor
public class Mt5ServiceImpl implements Mt5Service {
    private final ReentrantLock lock = new ReentrantLock(true);

    private RestTemplate restTemplate;
    private PythonProxyConfig pythonProxyConfig;
    private PasswordService passwordService;

    public Boolean checkCredentials(Integer accountNumber, String password) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/check-credentials");

            BaseCredsPayload request = BaseCredsPayload.builder()
                    .account_number(accountNumber)
                    .password(password)
                    .build();
            ResponseEntity<CheckCredsResponse> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, CheckCredsResponse.class);
            log.info("validate credentials response {}", resp.toString());
            return resp.getBody().getValid();
        } finally {
            lock.unlock();
        }
    }

    public SymbolInfo symbolInfo(AppUser user, String symbol) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/symbol-info");

            GetSymbolReq getSymbolReq = GetSymbolReq.builder()
                    .symbol(symbol)
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .build();
            ResponseEntity<SymbolInfoResponse> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), getSymbolReq, SymbolInfoResponse.class);
            log.info("symbolInfo response {}", resp.toString());
            return resp.getBody().getInfo();
        } finally {
            lock.unlock();
        }
    }

    public AccountType determineAccountType(Integer accountNumber, String password) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/determine-account-type");

            BaseCredsPayload request = BaseCredsPayload.builder()
                    .account_number(accountNumber)
                    .password(password)
                    .build();
            ResponseEntity<AccountTypeResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, AccountTypeResp.class);
            log.info("determineAccountType response {}", resp.toString());
            return resp.getBody().getAcc_type();
        } finally {
            lock.unlock();
        }
    }

    public TradePos positionsGet(AppUser user, String symbol) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/positions-get");

            GetSymbolReq request = GetSymbolReq.builder()
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .symbol(symbol)
                    .build();
            ResponseEntity<PositionsGetResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, PositionsGetResp.class);
            log.info("positionsGet response {}", resp.toString());
            return resp.getBody().getPositions().isEmpty() ? null : resp.getBody().getPositions().get(0);
        } finally {
            lock.unlock();
        }
    }

    public List<TradePos> positionsGetAll(AppUser user) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/positions-get-all");

            GetSymbolReq request = GetSymbolReq.builder()
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .build();
            ResponseEntity<PositionsGetResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, PositionsGetResp.class);
            log.info("positionsGet response {}", resp.toString());
            return resp.getBody().getPositions();
        } finally {
            lock.unlock();
        }
    }

    public Double getPriceForShort(AppUser user, String symbol) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/get-price-for-short");

            GetSymbolReq request = GetSymbolReq.builder()
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .symbol(symbol)
                    .build();
            ResponseEntity<PriceForShortResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, PriceForShortResp.class);
            log.info("getPriceForShort response {}", resp.toString());
            return resp.getBody().getPrice();
        } finally {
            lock.unlock();
        }
    }

    public InfoTick getInfoTick(AppUser user, String symbol) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/get-price-tick");

            GetSymbolReq request = GetSymbolReq.builder()
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .symbol(symbol)
                    .build();
            ResponseEntity<InfoTickResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, InfoTickResp.class);
            log.info("getPriceForShort response {}", resp.toString());
            return resp.getBody().getTick();
        } finally {
            lock.unlock();
        }
    }

    public OrderDetails placeLongOrder(AppUser user, Long ticket, String symbol, Double volume) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/close-short-order");

            OrderReq request = OrderReq.builder()
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .symbol(symbol)
                    .position_id(ticket)
                    .volume(volume)
                    .build();
            ResponseEntity<OrderResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, OrderResp.class);
            log.info("placeLongOrder response {}", resp);
            return resp.getBody().getOrder();
        } finally {
            lock.unlock();

        }
    }

    public OrderDetails placeShortOrder(AppUser user, Long ticket, String symbol, Double volume) {
        lock.lock();
        try {
            UriComponentsBuilder componentsBuilder = UriComponentsBuilder
                    .fromUriString(pythonProxyConfig.getUrl())
                    .path("/place-short-order");

            OrderReq request = OrderReq.builder()
                    .account_number(user.getMt5AccNumber())
                    .password(passwordService.decrypt(user.getMt5AccPwdEnc()))
                    .symbol(symbol)
                    .position_id(ticket)
                    .volume(volume)
                    .build();
            ResponseEntity<OrderResp> resp = restTemplate.postForEntity(componentsBuilder.toUriString(), request, OrderResp.class);
            log.info("placeShortOrder response {}", resp);
            return resp.getBody().getOrder();
        } finally {
            lock.unlock();
        }
    }

    @SuperBuilder
    @Data
    private static class BaseCredsPayload {
        private Integer account_number;
        private String password;
    }

    @SuperBuilder
    @Data
    private static class GetSymbolReq extends BaseCredsPayload {
        private String symbol;
    }


    @SuperBuilder
    @Data
    private static class OrderReq extends GetSymbolReq {
        private Long position_id;
        private Double volume;
    }

    @Data
    public static class OrderResp {
        private OrderDetails order;
    }

    @Data
    public static class OrderDetails {
        private Double ask;
        private Double bid;
        private String comment;
        private Long deal;
        private Long order;
        private Double price;
        private OrderRequest request;
        private Long requestId;
        private Integer retcode;
        private Integer retcodeExternal;
        private Double volume;
    }

    @Data
    public static class InfoTickResp {
        private InfoTick tick;
    }

    @Data
    public static class InfoTick {
        private Long time;
        private Double ask;
        private Double bid;
    }

    @Data
    public static class OrderRequest {
        private Integer action;
        private String comment;
        private Integer deviation;
        private Integer expiration;
        private Integer magic;
        private Long order;
        private Long position;
        private Long positionBy;
        private Double price;
        private Double sl;
        private Double stoplimit;
        private String symbol;
        private Double tp;
        private Integer type;
        private Integer typeFilling;
        private Integer typeTime;
        private Double volume;
    }

    @Data
    private static class PriceForShortResp {
        private Double price;
    }

    @Data
    private static class CheckCredsResponse {
        private Boolean valid;
    }

    @Data
    private static class AccountTypeResp {
        private AccountType acc_type;
    }


    @Data
    private static class PositionsGetResp {
        private List<TradePos> positions;
    }

    @Data
    public static class TradePos {
        private String comment;
        private String external_id;
        private Long identifier;
        private Integer magic;
        private Double price_current;
        private Double price_open;
        private Double profit;
        private Integer reason;
        private Double sl;
        private Double swap;
        private String symbol;
        private Long ticket;
        private Long time;
        private Long time_msc;
        private Long time_update;
        private Long time_update_msc;
        private Double tp;
        private Integer type;
        private Double volume;
    }

    @Data
    private static class SymbolInfoResponse {
        private SymbolInfo info;
    }

    @Data
    public static class SymbolInfo {
        private String description;
        private Double trade_contract_size;
        private Double volume_limit;
        private Double volume_max;
        private Double volume_min;
        private Double volume_step;
    }
}
