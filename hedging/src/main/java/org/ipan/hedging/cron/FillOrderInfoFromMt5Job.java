package org.ipan.hedging.cron;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.OrderMt5InfoFetcherService;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class FillOrderInfoFromMt5Job {
    private final OrderRepo orderRepo;
    private final OrderMt5InfoFetcherService fetcherService;

    public void run() throws IOException {
        log.info("FillOrderInfoFromMt5Job started");

        List<Order> orders = orderRepo.findOrdersWithEmptySwap();
        for (Order order : orders) {
            log.info("FillOrderInfoFromMt5Job. fetching info for order id {}", order.getId());
            fetcherService.fetchInfoForOrderSync(order.getId());
        }

        log.info("FillOrderInfoFromMt5Job finished");
    }
}
