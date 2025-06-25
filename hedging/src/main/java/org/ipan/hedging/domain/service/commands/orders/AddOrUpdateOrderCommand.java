package org.ipan.hedging.domain.service.commands.orders;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddOrUpdateOrderCommand {
    private Long id;
    private Long chatId;
    private Integer messageIdToUpdate;
    private Long userId;
    private Long itrxFeeSunAmount;
    private Long sunAmountPerTx;
    private Integer txAmount;
    private Integer energyAmountPerTx;
    private String receiveAddress;
    private String duration;
    private String correlationId;
    private String serial;
    private Long tariffId;

    private Long autoDelegationSessionId;
//    private OrderStatus orderStatus;

    private Integer itrxStatus;
    private String txId;

}
