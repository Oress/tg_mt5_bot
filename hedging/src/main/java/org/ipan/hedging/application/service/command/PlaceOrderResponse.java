package org.ipan.hedging.application.service.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.ipan.hedging.domain.model.Order;

@Data
@AllArgsConstructor
public class PlaceOrderResponse {
    private boolean isSuccessful;
    private Order order;
}
