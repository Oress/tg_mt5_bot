package org.ipan.hedging.domain;

import java.math.BigDecimal;

public class AppConstants {
    public static final String EUR_USD = "EUR/USD";
    public static final String USD_CAD = "USD/CAD";

    public static final Integer COMMON_SHEET_ID = 1;
    public static final String COMMON_SHEET_TITLE = "Общий";

    public static final BigDecimal DEFAULT_COMMISSION_PERCENTAGE = BigDecimal.valueOf(0.001);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
}
