package org.ipan.hedging.application.service;

import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.application.service.command.PlaceOrderResponse;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.OrderService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest()
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

    private OrderOrchestrator orderOrchestrator;
    private Mt5Service mt5Service;

    @Autowired
    public OrderOrchestratorTest(UserService userService, UserRepo userRepo,
                                 OrderService orderService, AppUserInstrumentRepository appUserInstrumentRepository,
                                 OrderRepo orderRepo, VolumeCalculationService volumeCalculationService) {
        this.userService = userService;
        this.userRepo = userRepo;
        this.orderService = orderService;
        this.userInstrumentRepository = appUserInstrumentRepository;
        this.orderRepo = orderRepo;
        this.volumeCalculationService = volumeCalculationService;
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
                volumeCalculationService
        );

        // clean orders for clean state every test.
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
    public void placeOrder_simpleHedge_lessThanOwnException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> placeEurUsdOrderResponse(1000, true, 1.36));
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

        assertThat(order1.getUserEnteredQuoteAmount()).isNull();
        assertThat(order1.getQuoteAmountLeftToUnhedge()).isNull();
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

        assertThat(order1.getUserEnteredQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(order1.getQuoteAmountLeftToUnhedge()).isEqualByComparingTo(BigDecimal.valueOf(1000));
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
        List<OrderConsumption> consumptions = order2.getConsumptions();
        assertThat(consumptions).hasSize(1);
        assertThat(consumptions.get(0).getConsumedOrder().getId()).isEqualByComparingTo(response1.getOrder().getId());
        assertThat(consumptions.get(0).getConsumedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));

        PlaceOrderResponse response3 = placeUsdCadOrderResponse(7000, false, 2.1);
        AppUserInstrument userInstrument3 = response3.getOrder().getUserInstrument();
        assertThat(userInstrument3.getClientOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getHouseOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getTotalOwnedBaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(userInstrument3.getClientOwnedQuoteAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        Order order3 = orderRepo.findOrderWithConsumptions(response3.getOrder().getId());
        List<OrderConsumption> consumptions3 = order3.getConsumptions();
        assertThat(consumptions3).hasSize(1);
        assertThat(consumptions3.get(0).getConsumedOrder().getId()).isEqualByComparingTo(response1.getOrder().getId());
        assertThat(consumptions3.get(0).getConsumedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(7000));

        Order order1AfterUnhedge = orderRepo.findById(response1.getOrder().getId()).get();
        assertThat(order1AfterUnhedge.getQuoteAmountLeftToUnhedge()).isEqualByComparingTo(BigDecimal.ZERO);
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
        List<OrderConsumption> consumptions = order3.getConsumptions();
        assertThat(consumptions).hasSize(2);

        assertThat(consumptions.get(0).getConsumedOrder().getId()).isEqualByComparingTo(response1.getOrder().getId());
        assertThat(consumptions.get(0).getConsumedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        assertThat(consumptions.get(1).getConsumedOrder().getId()).isEqualByComparingTo(response2.getOrder().getId());
        assertThat(consumptions.get(1).getConsumedQuoteAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));

        Order order1AfterUnhedge = orderRepo.findById(response1.getOrder().getId()).get();
        Order order2AfterUnhedge = orderRepo.findById(response2.getOrder().getId()).get();

        assertThat(order1AfterUnhedge.getQuoteAmountLeftToUnhedge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order2AfterUnhedge.getQuoteAmountLeftToUnhedge()).isEqualByComparingTo(BigDecimal.ZERO);
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
        when(mt5Service.placeLongOrder(any(),any(),any(), any())).thenReturn(orderDetails1);
        when(mt5Service.placeShortOrder(any(),any(), any(), any())).thenReturn(orderDetails1);
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
        when(mt5Service.placeLongOrder(any(),any(),any(), any())).thenReturn(orderDetails1);
        when(mt5Service.placeShortOrder(any(),any(), any(), any())).thenReturn(orderDetails1);
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