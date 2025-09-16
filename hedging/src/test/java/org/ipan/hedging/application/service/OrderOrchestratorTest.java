package org.ipan.hedging.application.service;

import org.ipan.hedging.application.OrderPlaceMt5ErrorException;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.application.service.command.PlaceOrderResponse;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.repository.*;
import org.ipan.hedging.domain.service.OrderService;
import org.ipan.hedging.domain.service.ReportingCalculationService;
import org.ipan.hedging.domain.service.UserService;
import org.ipan.hedging.domain.service.VolumeCalculationService;
import org.ipan.hedging.domain.service.commands.users.CreateUserCommand;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// TODO: unrealized PnL calculation
// TODO: House PnL calculation !!!!
// TODO: INTERNAL TRANSFERS!!!!!
// TODO: Test reporting calculation when there are orders with error status
// TODO: Test reporting calculation when first operation is deposit (prevRecord == null)
@SpringBootTest(classes = {TestAsyncConfig.class})
@Testcontainers
@ActiveProfiles("test")
//@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderOrchestratorTest {
    private static final String EURUSD = "EUR/USD";
    private static final String USDCAD = "USD/CAD";
    private static final Long USER_ID = 1L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("hedging_test")
            .withReuse(false)
            .withUsername("test")
            .withPassword("test");
    @Autowired
    private LifoPositionRepository lifoPositionRepository;
    @Autowired
    private HousePositionConsumptionRepository housePositionConsumptionRepository;


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private final UserService userService;
    private final UserRepo userRepo;
    private final OrderService orderService;
    private final AppUserInstrumentRepository userInstrumentRepository;
    private final OrderRepo orderRepo;
    private final VolumeCalculationService volumeCalculationService;
    private final ReportingCalculationService reportingCalculationService;
    private final UserBalanceService userBalanceService;
    private final ReportingCalculationRepository reportingCalculationRepository;
    private final UserBalanceActionRepo userBalanceActionRepo;


    private OrderOrchestrator orderOrchestrator;
    private Mt5Service mt5Service;

    @Autowired
    public OrderOrchestratorTest(UserService userService, UserRepo userRepo,
                                 OrderService orderService,
                                 AppUserInstrumentRepository appUserInstrumentRepository,
                                 OrderRepo orderRepo,
                                 VolumeCalculationService volumeCalculationService,
                                 ReportingCalculationService reportingCalculationService,
                                 ReportingCalculationRepository reportingCalculationRepository,
                                 UserBalanceActionRepo userBalanceActionRepo,
                                 UserBalanceService userBalanceService) {
        this.userService = userService;
        this.userRepo = userRepo;
        this.orderService = orderService;
        this.userInstrumentRepository = appUserInstrumentRepository;
        this.orderRepo = orderRepo;
        this.volumeCalculationService = volumeCalculationService;
        this.reportingCalculationService = reportingCalculationService;
        this.reportingCalculationRepository = reportingCalculationRepository;
        this.userBalanceActionRepo = userBalanceActionRepo;
        this.userBalanceService = userBalanceService;
    }

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @BeforeEach
    void setUp() {
        mt5Service = mock(Mt5Service.class);
        orderOrchestrator = new OrderOrchestrator(
                userRepo,
                orderService,
                mt5Service,
                volumeCalculationService,
                reportingCalculationService,
                userInstrumentRepository,
                Optional.ofNullable(null)
        );

        // clean orders for clean state every test.
        reportingCalculationRepository.deleteAll();
        housePositionConsumptionRepository.deleteAll();
        lifoPositionRepository.deleteAll();
        userBalanceActionRepo.deleteAll();
        orderRepo.deleteAll();
        userInstrumentRepository.deleteAll();

        CreateUserCommand command = CreateUserCommand.builder()
                .telegramId(USER_ID)
                .username("testuser")
                .firstName("Test")
                .languageCode("en")
                .build();
        AppUser user = userService.createUser(command);
        user.setMt5AccNumber(123456);
        user.setAccountType(AccountType.MINI);
        user = userRepo.save(user);

        if (user.getAvailableInstruments().isEmpty()) {
            userService.toggleInstrument(USER_ID, USDCAD);
            userService.toggleInstrument(USER_ID, EURUSD);
        }
    }

    @Test
    public void reportingCalculation_depositFirst() {
        userBalanceService.createDepositAction(USER_ID, BigDecimal.valueOf(1000), Instant.now());
        placeEurUsdOrderResponse(1000, false, 1.16755000);

        List<ReportingCalculation> all = reportingCalculationRepository.findAllOrdByDate();
        assertThat(all).hasSize(2);
        ReportingCalculation firstElem = all.get(0);
        assertThat(firstElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(firstElem.getRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(firstElem.getInPosition()).isEqualByComparingTo(BigDecimal.ZERO);
    }


    @Test
    public void reportingCalculation_eurUsd_3hedges() {
        placeEurUsdOrderResponse(1000, false, 1.16755000);
        placeEurUsdOrderResponse(2000, false, 1.16754000);
        placeEurUsdOrderResponse(3000, false, 1.16754000);

        List<ReportingCalculation> all = reportingCalculationRepository.findAllOrdByDate();
        assertThat(all).hasSize(3);
        ReportingCalculation firstElem = all.get(0);
        assertThat(firstElem.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-1000));
        assertThat(firstElem.getUsdt()).isEqualByComparingTo(BigDecimal.valueOf(-1167.55000000));
        assertThat(firstElem.getCommission()).isEqualByComparingTo(BigDecimal.valueOf(1.16755000));
        assertThat(firstElem.getInPosition()).isEqualByComparingTo(BigDecimal.valueOf(-1000));
        assertThat(firstElem.getDeposit()).isEqualByComparingTo(BigDecimal.valueOf(-1.16755000));
        assertThat(firstElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(firstElem.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(-1.16755000));

        ReportingCalculation secondElem = all.get(1);
        assertThat(secondElem.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-2000));
        assertThat(secondElem.getUsdt()).isEqualByComparingTo(BigDecimal.valueOf(-2335.08000000));
        assertThat(secondElem.getCommission()).isEqualByComparingTo(BigDecimal.valueOf(2.33508000));
        assertThat(secondElem.getInPosition()).isEqualByComparingTo(BigDecimal.valueOf(-3000));
        assertThat(secondElem.getDeposit()).isEqualByComparingTo(BigDecimal.valueOf(-2.33508000));
        assertThat(secondElem.getDepositTotal()).isEqualByComparingTo(BigDecimal.valueOf(-3.50263000));
        assertThat(secondElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(secondElem.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(-3.50263000));

        ReportingCalculation thirdElem = all.get(2);
        assertThat(thirdElem.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-3000));
        assertThat(thirdElem.getUsdt()).isEqualByComparingTo(BigDecimal.valueOf(-3502.62000000));
        assertThat(thirdElem.getCommission()).isEqualByComparingTo(BigDecimal.valueOf(3.50262000));
        assertThat(thirdElem.getInPosition()).isEqualByComparingTo(BigDecimal.valueOf(-6000));
        assertThat(thirdElem.getDeposit()).isEqualByComparingTo(BigDecimal.valueOf(-3.50262000));
        assertThat(thirdElem.getDepositTotal()).isEqualByComparingTo(BigDecimal.valueOf(-7.00525000));
        assertThat(thirdElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(thirdElem.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(-7.00525000));
    }

    // TODO: reimplement. deposits no longer affect calculations
    @Test
    public void reportingCalculation_eurUsd_hedgeDepositHedge() {
        PlaceOrderResponse response = placeEurUsdOrderResponse(1000, false, 1.16758000);
        userBalanceService.createDepositAction(response.getOrder().getUserInstrument().getId(), BigDecimal.valueOf(1000), Instant.now());
        placeEurUsdOrderResponse(3000, false, 1.16756000);

        List<ReportingCalculation> all = reportingCalculationRepository.findAllOrdByDate();
        assertThat(all).hasSize(3);
        ReportingCalculation firstElem = all.get(0);
        assertThat(firstElem.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-1000));
        assertThat(firstElem.getUsdt()).isEqualByComparingTo(BigDecimal.valueOf(-1167.58000000));
        assertThat(firstElem.getCommission()).isEqualByComparingTo(BigDecimal.valueOf(1.16758000));
        assertThat(firstElem.getInPosition()).isEqualByComparingTo(BigDecimal.valueOf(-1000));
        assertThat(firstElem.getDeposit()).isEqualByComparingTo(BigDecimal.valueOf(-1.16758000));
        assertThat(firstElem.getDepositTotal()).isEqualByComparingTo(BigDecimal.valueOf(-1.16758000));
        assertThat(firstElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(firstElem.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(-1.16758000));

        ReportingCalculation secondElem = all.get(1);
        assertThat(secondElem.getInPosition()).isEqualByComparingTo(BigDecimal.valueOf(-1000));
        assertThat(secondElem.getDepositTotal()).isEqualByComparingTo(BigDecimal.valueOf(-1.16758000));
        assertThat(secondElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(secondElem.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(998.83242000));

        ReportingCalculation thirdElem = all.get(2);
        assertThat(thirdElem.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(-3000));
        assertThat(thirdElem.getUsdt()).isEqualByComparingTo(BigDecimal.valueOf(-3502.68000000));
        assertThat(thirdElem.getCommission()).isEqualByComparingTo(BigDecimal.valueOf(3.50268000));
        assertThat(thirdElem.getInPosition()).isEqualByComparingTo(BigDecimal.valueOf(-4000));
        assertThat(thirdElem.getDeposit()).isEqualByComparingTo(BigDecimal.valueOf(-3.50268000));
        assertThat(thirdElem.getDepositTotal()).isEqualByComparingTo(BigDecimal.valueOf(995.32974000));
        assertThat(thirdElem.getDepositPayment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(thirdElem.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(995.32974000));
    }

    @Test
    public void reportingCalculation_eurUsd_hedgeWithdrawalDeposit() {
        // hedge 1
        // withdrawal
        // deposit
    }

    @Test
    public void reportingCalculation_eurUsd_hedgeDepositWithdrawal() {
        // hedge 1
        // deposit
        // withdrawal
    }

    // hedge 5k, unhedge 1k, hedge 5k more
    @Test
    public void placeOrder_usdcad_failedScenario1() {
        PlaceOrderResponse response1 = placeEurUsdOrderResponse(5000, false, 1.16725);
        Order order1 = response1.getOrder();
        AppUserInstrument userInstrument1 = order1.getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(order1.getType()).isEqualByComparingTo(OrderType.MT5_SHORT);
        assertThat(order1.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        PlaceOrderResponse response2 = placeEurUsdOrderResponse(1000, true, 1.16725);
        Order order2 = response2.getOrder();
        AppUserInstrument userInstrument2 = order2.getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(order2.getType()).isEqualByComparingTo(OrderType.MT5_LONG);

        PlaceOrderResponse response3 = placeEurUsdOrderResponse(5000, false, 1.16725);
        Order order3 = response3.getOrder();
        AppUserInstrument userInstrument3 = order3.getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(9000));
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(9000));

        PlaceOrderResponse response4 = placeEurUsdOrderResponse(9000, true, 1.16725);
        Order order4 = response4.getOrder();
        AppUserInstrument userInstrument4 = order4.getUserInstrument();
        assertThat(userInstrument4.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument4.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
    }

    // hedge 5k EUR, hedge 5k CAD, unhedge 1k EUR
    @Test
    public void placeOrder_usdcad_failedScenario2() {
        PlaceOrderResponse response1 = placeEurUsdOrderResponse(5000, false, 1.16725);
        Order order1 = response1.getOrder();
        AppUserInstrument userInstrument1 = order1.getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(order1.getType()).isEqualByComparingTo(OrderType.MT5_SHORT);
        assertThat(order1.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        PlaceOrderResponse response2 = placeUsdCadOrderResponse(5000, true, 1.37334);
        Order order2 = response2.getOrder();
        AppUserInstrument userInstrument2 = order2.getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(3640.75902544));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(359.24097456));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(order2.getType()).isEqualByComparingTo(OrderType.MT5_LONG);
        assertThat(order2.getHouseOwnedPosition()).isNotNull();

        PlaceOrderResponse response3 = placeEurUsdOrderResponse(1000, true, 1.16725);
        Order order3 = orderRepo.findOrderWithConsumptions(response3.getOrder().getId());
        LifoPosition lifoPosition = order3.getLifoPosition();
        AppUserInstrument userInstrument3 = order3.getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(lifoPosition).isNull();
        List<LifoPositionConsumption> consumptions = order3.getConsumptions();
        assertThat(consumptions).hasSize(0);

        PlaceOrderResponse response4 = placeUsdCadOrderResponse(1000, false, 1.37410);
        Order order4 = orderRepo.findOrderWithConsumptions(response4.getOrder().getId());
        LifoPosition lifoPosition4 = order4.getLifoPosition();
        AppUserInstrument userInstrument4 = order3.getUserInstrument();
        assertThat(userInstrument4.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(userInstrument4.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
        assertThat(lifoPosition4).isNull();
        List<LifoPositionConsumption> consumptions4 = order4.getConsumptions();
        assertThat(consumptions4).hasSize(1);
        assertThat(consumptions4.get(0).getConsumedPosition().getId()).isEqualTo(order2.getLifoPosition().getId());

        order4 = orderRepo.findOrderWithHouseConsumptions(response4.getOrder().getId());
        List<HousePositionConsumption> houseConsumptions4 = order4.getHousePositionConsumptions();
        assertThat(houseConsumptions4).hasSize(1);
        assertThat(houseConsumptions4.get(0).getId()).isEqualTo(order2.getHouseOwnedPosition().getId());
    }

    @Test
    public void placeOrder_usdcad_internalTransfersSellAndBuy() {
        Assertions.fail("Not implemented");
    }

    @Test
    public void placeOrder_simpleHedge_mt5NonAcceptableCode() {
        Assertions.assertThrows(OrderPlaceMt5ErrorException.class, () -> placeEurUsdOrderCanceledResponse(1000, false, 1.36));
    }

    @Test
    public void placeOrder_simpleHedge_lessThanOwnException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> placeEurUsdOrderResponse(1000, true, 1.36));
    }

    @Test
    public void placeOrder_invHedge_lessThanOwnException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> placeUsdCadOrderResponse(1000, false, 1.36));
    }

    @Test
    public void placeOrder_simpleHedge_orderFieldsCheck() {
        PlaceOrderResponse response1 = placeEurUsdOrderResponse(1000, false, 1.36);
        Order order1 = response1.getOrder();
        AppUserInstrument userInstrument1 = order1.getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(order1.getType()).isEqualByComparingTo(OrderType.MT5_SHORT);
        assertThat(order1.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        assertThat(order1.getMt5AccNumber()).isNotNull();

        assertThat(order1.getClientBaseAmountFrom()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order1.getClientBaseAmountTo()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(order1.getUserBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        assertThat(order1.getUserEnteredAmount()).isNull();
        assertThat(order1.getLifoPosition()).isNull();
    }

    @Test
    public void placeOrder_EurUsdpartialOrdering_OpenAndClose() {
        PlaceOrderResponse response1 = placeEurUsdOrderResponse(1560, false, 1.36);
        Order order1 = response1.getOrder();
        AppUserInstrument userInstrument1 = order1.getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1560));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(440));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(order1.getType()).isEqualByComparingTo(OrderType.MT5_SHORT);
        assertThat(order1.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        assertThat(order1.getMt5AccNumber()).isNotNull();

        assertThat(order1.getClientBaseAmountFrom()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order1.getClientBaseAmountTo()).isEqualByComparingTo(BigDecimal.valueOf(1560));
        assertThat(order1.getUserBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1560));

        assertThat(order1.getUserEnteredAmount()).isNull();
        assertThat(order1.getLifoPosition()).isNull();

        PlaceOrderResponse response2 = placeEurUsdOrderResponse(1560, true, 1.36);
        Order order2 = response2.getOrder();
        AppUserInstrument userInstrument2 = order2.getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(order2.getType()).isEqualByComparingTo(OrderType.MT5_LONG);
        assertThat(order2.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);
    }

    @Test
    public void placeOrder_EurUsdpartialOrdering_CloseIn2Steps() {
        PlaceOrderResponse response1 = placeEurUsdOrderResponse(1560, false, 1.36);
        Order order1 = response1.getOrder();
        AppUserInstrument userInstrument1 = order1.getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1560));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(440));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(order1.getType()).isEqualByComparingTo(OrderType.MT5_SHORT);
        assertThat(order1.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        assertThat(order1.getMt5AccNumber()).isNotNull();

        assertThat(order1.getClientBaseAmountFrom()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order1.getClientBaseAmountTo()).isEqualByComparingTo(BigDecimal.valueOf(1560));
        assertThat(order1.getUserBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1560));


        PlaceOrderResponse response2 = placeEurUsdOrderResponse(1400, true, 1.36);
        Order order2 = response2.getOrder();
        AppUserInstrument userInstrument2 = order2.getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(160));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-160));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(order2.getType()).isEqualByComparingTo(OrderType.MT5_LONG);
        assertThat(order2.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        PlaceOrderResponse response3 = placeEurUsdOrderResponse(160, true, 1.36);
        Order order3 = response3.getOrder();
        AppUserInstrument userInstrument3 = order3.getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument3.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(0));
        assertThat(order3.getType()).isEqualByComparingTo(OrderType.HOUSE_CLIENT_TRANSFER_LONG);
        assertThat(order3.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);
    }


    @Test
    public void placeOrder_inverseHedge_orderFieldsCheck() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(1000, true, 1.36);
        Order order1 = response1.getOrder();
        AppUserInstrument userInstrument1 = order1.getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(264.70588235));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument1.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        assertThat(order1.getType()).isEqualByComparingTo(OrderType.MT5_LONG);
        assertThat(order1.getStatus()).isEqualByComparingTo(OrderStatus.COMPLETE);

        assertThat(order1.getMt5AccNumber()).isNotNull();

        assertThat(order1.getClientBaseAmountFrom()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order1.getClientBaseAmountTo()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));
        assertThat(order1.getUserBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));

        assertThat(order1.getUserEnteredAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(order1.getLifoPosition().getRemainingAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void placeOrder_invHedging_shouldMake2VirtualTransfers() {
        Mt5ServiceImpl.SymbolInfo symbolInfo = createMockSymbolInfo();
        when(mt5Service.symbolInfo(any(), any())).thenReturn(symbolInfo);

        PlaceOrderResponse response1 = placeUsdCadOrderResponse(1000, true, 1.36);
        AppUserInstrument userInstrument1 = response1.getOrder().getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(264.70588235));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument1.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        PlaceOrderResponse response2 = placeUsdCadOrderResponse(1000, true, 1.37);
        AppUserInstrument userInstrument2 = response2.getOrder().getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1465.22112495));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-465.22112495));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument2.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        PlaceOrderResponse response3 = placeUsdCadOrderResponse(1000, true, 1.38);
        AppUserInstrument userInstrument3 = response3.getOrder().getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2189.85880611));
        assertThat(userInstrument3.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-189.85880611));
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(userInstrument3.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        PlaceOrderResponse response4 = placeUsdCadOrderResponse(1000, true, 1.39);
        AppUserInstrument userInstrument4 = response4.getOrder().getUserInstrument();
        assertThat(userInstrument4.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2909.28326654));
        assertThat(userInstrument4.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(90.71673346));
        assertThat(userInstrument4.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(userInstrument4.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(4000));
    }

    @Test
    void placeOrder_invHedging_open3kClose2k() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(1000, true, 1.36);
        AppUserInstrument userInstrument1 = response1.getOrder().getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(264.70588235));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument1.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        PlaceOrderResponse response2 = placeUsdCadOrderResponse(1000, true, 1.37);
        AppUserInstrument userInstrument2 = response2.getOrder().getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1465.22112495));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-465.22112495));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument2.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        PlaceOrderResponse response3 = placeUsdCadOrderResponse(1000, true, 1.38);
        AppUserInstrument userInstrument3 = response3.getOrder().getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2189.85880611));
        assertThat(userInstrument3.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-189.85880611));
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(userInstrument3.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        PlaceOrderResponse response4 = placeUsdCadOrderResponse(2000, false, 1.39);
        AppUserInstrument userInstrument4 = response4.getOrder().getUserInstrument();
        assertThat(userInstrument4.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(724.63768116));
        assertThat(userInstrument4.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(275.36231884));
        assertThat(userInstrument4.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument4.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void placeOrder_invHedging_open10kCloseAll() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(10000, true, 1.36);
        AppUserInstrument userInstrument1 = response1.getOrder().getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(7352.94117647));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-352.94117647));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000));
        assertThat(userInstrument1.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        PlaceOrderResponse response2 = placeUsdCadOrderResponse(3000, false, 2.0);
        AppUserInstrument userInstrument2 = response2.getOrder().getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5147.05882353));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-147.05882353));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(userInstrument2.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000));

        Order order2 = orderRepo.findOrderWithConsumptions(response2.getOrder().getId());
        List<LifoPositionConsumption> consumptions = order2.getConsumptions();
        assertThat(consumptions).hasSize(1);
        assertThat(consumptions.get(0).getConsumedPosition().getId()).isEqualByComparingTo(response1.getOrder().getLifoPosition().getId());
        assertThat(consumptions.get(0).getConsumedAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        PlaceOrderResponse response3 = placeUsdCadOrderResponse(7000, false, 2.1);
        AppUserInstrument userInstrument3 = response3.getOrder().getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        Order order3 = orderRepo.findOrderWithConsumptions(response3.getOrder().getId());
        List<LifoPositionConsumption> consumptions3 = order3.getConsumptions();
        assertThat(consumptions3).hasSize(1);
        assertThat(consumptions3.get(0).getConsumedPosition().getId()).isEqualByComparingTo(response1.getOrder().getLifoPosition().getId());
        assertThat(consumptions3.get(0).getConsumedAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000));
        assertThat(consumptions3.get(0).getConsumedPosition().getClosedAt()).isNotNull();

        Order order1AfterUnhedge = orderRepo.findById(response1.getOrder().getId()).get();
        assertThat(order1AfterUnhedge.getLifoPosition().getRemainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }


    @Test
    void placeOrder_invHedging_open2kCloseAll() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(1000, true, 1.36);
        AppUserInstrument userInstrument1 = response1.getOrder().getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(264.70588235));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument1.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        PlaceOrderResponse response2 = placeUsdCadOrderResponse(1000, true, 1.37);
        AppUserInstrument userInstrument2 = response2.getOrder().getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1465.22112495));
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(-465.22112495));
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument2.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        PlaceOrderResponse response3 = placeUsdCadOrderResponse(2000, false, 1.38);
        AppUserInstrument userInstrument3 = response3.getOrder().getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        Order order3 = orderRepo.findOrderWithConsumptions(response3.getOrder().getId());
        List<LifoPositionConsumption> consumptions = order3.getConsumptions();
        assertThat(consumptions).hasSize(2);

        assertThat(consumptions.get(0).getConsumedPosition().getOrder().getId()).isEqualByComparingTo(response1.getOrder().getId());
        assertThat(consumptions.get(0).getConsumedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        assertThat(consumptions.get(1).getConsumedPosition().getOrder().getId()).isEqualByComparingTo(response2.getOrder().getId());
        assertThat(consumptions.get(1).getConsumedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        Order order1AfterUnhedge = orderRepo.findById(response1.getOrder().getId()).get();
        Order order2AfterUnhedge = orderRepo.findById(response2.getOrder().getId()).get();

        assertThat(order1AfterUnhedge.getLifoPosition().getRemainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order2AfterUnhedge.getLifoPosition().getRemainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void placeOrder_invHedging_simpleOpenAndClose() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(1000, true, 1.36);
        AppUserInstrument userInstrument1 = response1.getOrder().getUserInstrument();
        assertThat(userInstrument1.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(735.29411765));
        assertThat(userInstrument1.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(264.70588235));
        assertThat(userInstrument1.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(userInstrument1.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        PlaceOrderResponse response2 = placeUsdCadOrderResponse(1000, false, 1.36);
        AppUserInstrument userInstrument2 = response2.getOrder().getUserInstrument();
        assertThat(userInstrument2.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument2.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument2.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument2.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.ZERO);

    }

    // This test covers scenario with loss of precision. w/o corrections in VolumeCalculation service
    // we get 1e-8 USD in the end.
    @Test
    void placeOrder_invHedging_roundingProblem1() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(3000, true, 1.36877);
        PlaceOrderResponse response2 = placeUsdCadOrderResponse(10000, true, 1.36877);
        PlaceOrderResponse response3 = placeUsdCadOrderResponse(11000, false, 1.36867);
        PlaceOrderResponse response4 = placeUsdCadOrderResponse(2000, false, 1.36866);
        AppUserInstrument userInstrument4 = response4.getOrder().getUserInstrument();
        assertThat(userInstrument4.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void liquidate_hugeHedge_splitIntoMultipleOrders() {
        PlaceOrderResponse response1 = placeEurUsdOrderResponse(1_000_000, false, 1.16);
        PlaceOrderResponse response2 = placeEurUsdOrderResponse(1_000_000, false, 1.16);
        PlaceOrderResponse response3 = placeEurUsdOrderResponse(1_000_000, false, 1.16);

        AppUserInstrument userInstrument = response1.getOrder().getUserInstrument();
        orderOrchestrator = spy(orderOrchestrator);
        orderOrchestrator.liquidate(userInstrument.getId());
        verify(orderOrchestrator, times(3)).placeOrder(any());
    }

    @Test
    void testReportingCalculation() {
        PlaceOrderResponse response1 = placeUsdCadOrderResponse(1000, true, 1.36567);
        PlaceOrderResponse response2 = placeUsdCadOrderResponse(4000, true, 1.32678);
        PlaceOrderResponse response3 = placeUsdCadOrderResponse(9000, true, 1.25764);

        List<ReportingCalculation> all = reportingCalculationRepository.findAllOrdByDate();
        assertThat(all).hasSize(3);

        ReportingCalculation reportingCalculation1 = all.get(0);
        assertThat(reportingCalculation1.getUsdt().setScale(0, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-732));
        assertThat(reportingCalculation1.getCommission().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(0.73));
        assertThat(reportingCalculation1.getInPosition().setScale(0, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-1000));
        assertThat(reportingCalculation1.getRate().setScale(5, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(1.36567));
        assertThat(reportingCalculation1.getDeposit().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-0.73));
        assertThat(reportingCalculation1.getDepositTotal().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-0.73));
        assertThat(reportingCalculation1.getBalance().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-0.73));

        ReportingCalculation reportingCalculation2 = all.get(1);
        assertThat(reportingCalculation2.getUsdt().setScale(0, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-3015));
        assertThat(reportingCalculation2.getCommission().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(3.01));
        assertThat(reportingCalculation2.getInPosition().setScale(0, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-5000));
        assertThat(reportingCalculation2.getRate().setScale(5, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(1.33456));
        assertThat(reportingCalculation2.getDeposit().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-3.01));
        assertThat(reportingCalculation2.getDepositTotal().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-3.75));
        assertThat(reportingCalculation2.getBalance().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-3.75));

        ReportingCalculation reportingCalculation3 = all.get(2);
        assertThat(reportingCalculation3.getUsdt().setScale(0, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-7156));
        assertThat(reportingCalculation3.getCommission().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(7.16));
        assertThat(reportingCalculation3.getInPosition().setScale(0, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-14_000));
        assertThat(reportingCalculation3.getRate().setScale(5, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(1.28511));
        assertThat(reportingCalculation3.getDeposit().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-7.16));
        assertThat(reportingCalculation3.getDepositTotal().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-10.90).setScale(2));
        assertThat(reportingCalculation3.getBalance().setScale(2, RoundingMode.HALF_DOWN)).isEqualTo(BigDecimal.valueOf(-10.90).setScale(2));
    }

    private PlaceOrderResponse placeEurUsdOrderResponse(Integer userAmount, boolean isBuy, Double price) {
        Mt5ServiceImpl.SymbolInfo symbolInfo = createMockSymbolInfo();
        when(mt5Service.symbolInfo(any(), any())).thenReturn(symbolInfo);

        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .isBuy(isBuy)
                .userId(USER_ID)
                .instrumentLabel(EURUSD)
                .userAmount(userAmount)
                .build();

        Mt5ServiceImpl.InfoTick infoTick = createMockInfoTick(price);

        Double volume1 = BigDecimal.valueOf(userAmount).divide(BigDecimal.valueOf(symbolInfo.getTrade_contract_size()), 2, RoundingMode.HALF_DOWN).doubleValue();
        Mt5ServiceImpl.OrderDetails orderDetails1 = createMockOrderDetails(price, volume1);

        when(mt5Service.getInfoTick(any(), any())).thenReturn(infoTick);
        when(mt5Service.placeLongOrder(any(),any(),any(), any(), any())).thenReturn(orderDetails1);
        when(mt5Service.placeShortOrder(any(),any(), any(), any(), any())).thenReturn(orderDetails1);
        return orderOrchestrator.placeOrder(command);
    }

    private PlaceOrderResponse placeEurUsdOrderCanceledResponse(Integer userAmount, boolean isBuy, Double price) {
        Mt5ServiceImpl.SymbolInfo symbolInfo = createMockSymbolInfo();
        when(mt5Service.symbolInfo(any(), any())).thenReturn(symbolInfo);

        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .isBuy(isBuy)
                .userId(USER_ID)
                .instrumentLabel(EURUSD)
                .userAmount(userAmount)
                .build();

        Mt5ServiceImpl.InfoTick infoTick = createMockInfoTick(price);

        Double volume1 = BigDecimal.valueOf(userAmount).divide(BigDecimal.valueOf(symbolInfo.getTrade_contract_size()), 2, RoundingMode.HALF_DOWN).doubleValue();
        Mt5ServiceImpl.OrderDetails orderDetails1 = createMockFailedOrderDetails(price, volume1);

        when(mt5Service.getInfoTick(any(), any())).thenReturn(infoTick);
        when(mt5Service.placeLongOrder(any(), any(),any(),any(), any())).thenReturn(orderDetails1);
        when(mt5Service.placeShortOrder(any(), any(),any(), any(), any())).thenReturn(orderDetails1);
        return orderOrchestrator.placeOrder(command);
    }

    private PlaceOrderResponse placeUsdCadOrderResponse(Integer userAmount, boolean isBuy, Double price) {
        Mt5ServiceImpl.SymbolInfo symbolInfo = createMockSymbolInfo();
        when(mt5Service.symbolInfo(any(), any())).thenReturn(symbolInfo);

        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .isBuy(isBuy)
                .userId(USER_ID)
                .instrumentLabel(USDCAD)
                .userAmount(userAmount)
                .build();

        Mt5ServiceImpl.InfoTick infoTick = createMockInfoTick(price);

        Double volume1 = BigDecimal.valueOf(userAmount).divide(BigDecimal.valueOf(symbolInfo.getTrade_contract_size()), 2, RoundingMode.HALF_DOWN).doubleValue();
        Mt5ServiceImpl.OrderDetails orderDetails1 = createMockOrderDetails(price, volume1);

        when(mt5Service.getInfoTick(any(), any())).thenReturn(infoTick);
        when(mt5Service.placeLongOrder(any(), any(),any(),any(), any())).thenReturn(orderDetails1);
        when(mt5Service.placeShortOrder(any(), any(),any(), any(), any())).thenReturn(orderDetails1);
        return orderOrchestrator.placeOrder(command);
    }

/*    // Helper methods for creating test data
    private AppUser createTestUser() {
        AppUser user = new AppUser();
        user.setTelegramId(1L);
        user.setMt5AccNumber(123456);
        user.setAccountType(AccountType.STANDARD);
        user.setAvailableInstruments(Set.of(testUserInstrument));
        return user;
    }*/

    /*private AppUserInstrument createTestUserInstrument() {
        AppUserInstrument userInstrument = new AppUserInstrument();
        userInstrument.setUser(testUser);
        userInstrument.setInstrument(testInstrument);
        userInstrument.setHouseOwnedBaseAmount(BigDecimal.valueOf(0.02)); // Sufficient for house transfer
        return userInstrument;
    }*/

    private Mt5ServiceImpl.SymbolInfo createMockSymbolInfo() {
        Mt5ServiceImpl.SymbolInfo symbolInfo = new Mt5ServiceImpl.SymbolInfo();
        symbolInfo.setTrade_contract_size(100000.0);
        return symbolInfo;
    }

    private Mt5ServiceImpl.InfoTick createMockInfoTick(Double price) {
        Mt5ServiceImpl.InfoTick infoTick = new Mt5ServiceImpl.InfoTick();
        infoTick.setBid(price);
        infoTick.setAsk(price);
        return infoTick;
    }

/*
    private Order createMockOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserInstrument(testUserInstrument);
        order.setType(OrderType.MT5_OPEN);
        order.setStatus(OrderStatus.COMPLETE);
        order.setCreatedAt(Instant.now());
        return order;
    }
*/
    private Mt5ServiceImpl.OrderDetails createMockFailedOrderDetails(Double price, Double volume) {
        Mt5ServiceImpl.OrderDetails orderDetails = new Mt5ServiceImpl.OrderDetails();
        orderDetails.setRetcode(10006); // rejected for some reason
        orderDetails.setPrice(price);
        orderDetails.setBid(price);
        orderDetails.setAsk(price);
        orderDetails.setVolume(volume);
        return orderDetails;
    }


    private Mt5ServiceImpl.OrderDetails createMockOrderDetails(Double price, Double volume) {
        Mt5ServiceImpl.OrderDetails orderDetails = new Mt5ServiceImpl.OrderDetails();
        orderDetails.setRetcode(10009); // Success code
        orderDetails.setDeal(987654L);
        orderDetails.setOrder(123456L);
        orderDetails.setPrice(price);
        orderDetails.setBid(price);
        orderDetails.setAsk(price);
        orderDetails.setVolume(volume);
        return orderDetails;
    }

    private Mt5ServiceImpl.OrderDetails createMockOrderDetailsWithFailureCode() {
        Mt5ServiceImpl.OrderDetails orderDetails = new Mt5ServiceImpl.OrderDetails();
        orderDetails.setRetcode(10007); // Failure code
        return orderDetails;
    }
}