package org.ipan.hedging.application.service;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ArrayMap;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.Config;
import org.ipan.hedging.GoogleSheetsConfig;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.*;
import org.ipan.hedging.domain.service.UnrealizedPnlCalculator;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@Profile("!test")
public class GoogleSheetsService {
    private static final String MD_KEY_SHEET_LAST_SYNC_DT = "last_sheet_sync_dt";
    private static final String APPLICATION_NAME = "Hedging Bot";
    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE, DriveScopes.DRIVE_FILE);
    public static final String EUR_USD = "EUR/USD";
    public static final String USD_CAD = "USD/CAD";

    private final GoogleSheetsConfig config;
    private final UserRepo userRepo;
    private final Sheets sheets;
    private final Drive drive;
    private final OrderRepo orderRepo;
    private final UserBalanceActionRepo userBalanceActionRepo;
    private final AppUserInstrumentRepository userInstrumentRepository;
    private final ReportingCalculationRepository reportingCalculationRepository;
    private final GoogleSheetsApiService googleSheetsApiService;
    private final Mt5Service mt5Service;
    private final UnrealizedPnlCalculator unrealizedPnlCalculator;

    public GoogleSheetsService(GoogleSheetsConfig config, UserRepo userRepo,
                               OrderRepo orderRepo, UserBalanceActionRepo userBalanceActionRepo,
                               AppUserInstrumentRepository userInstrumentRepository,
                               ReportingCalculationRepository reportingCalculationRepository,
                               GoogleSheetsApiService googleSheetsApiService,
                               @Qualifier(Config.AUX_MT5)
                               Mt5Service mt5Service, UnrealizedPnlCalculator unrealizedPnlCalculator) throws IOException {
        this.config = config;
        this.userRepo = userRepo;
        this.userInstrumentRepository = userInstrumentRepository;
        this.googleSheetsApiService = googleSheetsApiService;
        this.mt5Service = mt5Service;
        this.unrealizedPnlCalculator = unrealizedPnlCalculator;

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(Files.newInputStream(Path.of(config.getSaJson())))
                .createScoped(SCOPES);
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        this.sheets = new Sheets.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();

        this.drive = new Drive.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
        this.orderRepo = orderRepo;
        this.userBalanceActionRepo = userBalanceActionRepo;
        this.reportingCalculationRepository = reportingCalculationRepository;
    }

    public void removeSheetForAnInstrument(Long userInstrumentId) {
        AppUserInstrument userInstrument = userInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();

        if (user.getGoogleSheetId() == null) {
            log.error("User {} does not have a Google Sheet ID, cannot remove instrument sheet", user.getTelegramId());
            return;
        }

        try {
            Spreadsheet spreadsheet = sheets.spreadsheets().get(user.getGoogleSheetId()).setIncludeGridData(false).execute();
            removeInstrumentSheetIfExists(spreadsheet, userInstrument);
        } catch (Exception e) {
            log.error("Something went wrong during sync ", e);
        }
    }

    @Transactional
    @Async
    public void syncUser(Long userId) {
        try {
            Instant syncDt = Instant.now();
            AppUser user = userRepo.findByIdWithInstruments(userId);

            if (user.getGoogleSheetId() == null) {
                createSpreadSheet(user);
            }

            Spreadsheet spreadsheet = sheets.spreadsheets().get(user.getGoogleSheetId()).setIncludeGridData(false).execute();
            createSheetsForUserIfNotExist(spreadsheet, user);
            remove0SheetIfExists(spreadsheet, user);

            createTablesForUsersIfNotExist(spreadsheet, user);
            syncDataForUser(user, syncDt);

        } catch (Exception e) {
            log.error("Something went wrong during sync ", e);
        }
    }

    @SneakyThrows
    private void syncDataForUser(AppUser user, Instant syncDt) {
        syncCommonSheet(user);

        for (AppUserInstrument userInstrument : user.getAvailableInstruments()) {
            Instant lastSyncDt = userInstrument.getLastGoogleSheetSync() == null
                    ? Instant.EPOCH
                    : userInstrument.getLastGoogleSheetSync();
            Instrument instrument = userInstrument.getInstrument();
            Integer sheetId = userInstrument.getSheetId();

            List<UserBalanceAction> balanceActions = Collections.emptyList(); //userBalanceActionRepo.findAllForSyncByUser(lastSyncDt, user.getTelegramId());
            List<Order> ordersForSync = orderRepo.findAllForSyncByInstrument(lastSyncDt, user.getTelegramId(), instrument.getLabel());

            if (ordersForSync.isEmpty() && balanceActions.isEmpty()) {
                log.info("No orders to sync for user {}", user.getTelegramId());
                continue;
            }

            List<RowData> rowDataStream = getRowData(ordersForSync, balanceActions, instrument);

            BatchUpdateSpreadsheetResponse batchUpdate = sheets.spreadsheets().batchUpdate(
                    user.getGoogleSheetId(),
                    new BatchUpdateSpreadsheetRequest()
                            .setRequests(
                                    List.of(
                                            new Request().setAppendCells(
                                                    new AppendCellsRequest()
                                                            .setFields("*")
                                                            .setSheetId(sheetId)
                                                            .setRows(rowDataStream)
                                            )
                                    )
                            )
            ).execute();
            userInstrument.setLastGoogleSheetSync(syncDt);
            log.info("result batchUpdate {}", batchUpdate);
        }
    }

    public void syncCommonSheet(AppUser user) throws IOException {
        Integer commonSheetId = AppConstants.COMMON_SHEET_ID;

        googleSheetsApiService.clearSheet(user.getGoogleSheetId(), commonSheetId);

        List<UserBalanceAction> allForSyncByUser = userBalanceActionRepo.findAllForSyncByUser(Instant.EPOCH, user.getTelegramId());
        BigDecimal sumOfAllActionsByUser = BigDecimal.valueOf(calculateSum(allForSyncByUser));

        List<UpdateCellsRequest> updateRequests = new ArrayList<>();

        int nextRowIndex = 1;
        List<AppUserInstrument> sorted = user.getAvailableInstruments().stream().sorted(Comparator.comparing(k -> k.getInstrument().getLabel())).toList();
        for (AppUserInstrument userInstrument : sorted) {
            ReportingCalculation lastReportingRow = reportingCalculationRepository.getLastRowForTheUserInstrument(userInstrument.getId(), Limit.of(1));
            if (lastReportingRow != null) {
                BigDecimal balanceForTheUserInstrument = lastReportingRow.getBalance();
                nextRowIndex+=3;
                sumOfAllActionsByUser = sumOfAllActionsByUser.add(balanceForTheUserInstrument);

                Instrument instrument = userInstrument.getInstrument();
                RowData data = new RowData()
                        .setValues(List.of(
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setStringValue("Баланс по %s".formatted(instrument.getLabel()))
                                        ),
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setNumberValue(balanceForTheUserInstrument.setScale(2, RoundingMode.HALF_DOWN).doubleValue())
                                        )
                        ));
                updateRequests.add(
                        new UpdateCellsRequest()
                                .setFields("*")
                                .setStart(new GridCoordinate()
                                        .setRowIndex(nextRowIndex)
                                        .setSheetId(commonSheetId)
                                        .setColumnIndex(0))
                                .setRows(List.of(data))
                );

                boolean isLong = InstrumentMode.LONG.equals(instrument.getMode());
                String symbol = user.getSymbolByLabelAndAccType(instrument.getLabel());
                Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, symbol);
                BigDecimal currentRate = isLong
                        ? BigDecimal.valueOf(infoTick.getBid()).setScale(5, RoundingMode.HALF_DOWN)
                        : BigDecimal.valueOf(infoTick.getAsk()).setScale(5, RoundingMode.HALF_DOWN);

                BigDecimal unrealizedPnl = unrealizedPnlCalculator.calculate(userInstrument.getId(), currentRate).getResult();

                // unrealized PnL
                RowData data1 = new RowData()
                        .setValues(List.of(
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setStringValue("Нереализованная курсовая разница %s".formatted(instrument.getLabel()))
                                        ),
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setNumberValue(unrealizedPnl.setScale(2, RoundingMode.HALF_DOWN).doubleValue())
                                        )
                        ));
                RowData data2 = new RowData()
                        .setValues(List.of(
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setStringValue("Текущий крос курс")
                                        ),
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setNumberValue(currentRate.setScale(5, RoundingMode.HALF_DOWN).doubleValue())
                                        )
                        ));
                updateRequests.add(
                        new UpdateCellsRequest()
                                .setFields("*")
                                .setStart(new GridCoordinate()
                                        .setRowIndex(nextRowIndex)
                                        .setSheetId(commonSheetId)
                                        .setColumnIndex(4))
                                .setRows(List.of(data1, data2))
                );
            }
        }

        RowData commonBalanceRow = new RowData()
                .setValues(List.of(
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue("Общий баланс обеспечения")
                                ),
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(sumOfAllActionsByUser.setScale(2, RoundingMode.HALF_DOWN).doubleValue())
                                )
                ));
        updateRequests.add(
                new UpdateCellsRequest()
                        .setFields("*")
                        .setStart(new GridCoordinate()
                                .setRowIndex(1)
                                .setSheetId(commonSheetId)
                                .setColumnIndex(0))
                        .setRows(List.of(commonBalanceRow))
        );

        nextRowIndex+=3;
        RowData depositsHeader = new RowData()
                .setValues(List.of(
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue("Депозиты/Выводы")
                                )
                ));
        updateRequests.add(
                new UpdateCellsRequest()
                        .setFields("*")
                        .setStart(new GridCoordinate()
                                .setRowIndex(nextRowIndex)
                                .setSheetId(commonSheetId)
                                .setColumnIndex(0))
                        .setRows(List.of(depositsHeader))
        );

        googleSheetsApiService.updateCells(user.getGoogleSheetId(), updateRequests);

        int batchSize = 50;
        nextRowIndex++;
        List<UserBalanceAction> batch = new ArrayList<>();
        for (int i = 0; i < allForSyncByUser.size(); i++) {
            batch.add(allForSyncByUser.get(i));
            if (batch.size() >= batchSize || i == allForSyncByUser.size() - 1) {
                log.info("Syncing batch of {} balance actions for user {}", batch.size(), user.getTelegramId());
                List<RowData> batchRows = batch.stream().map(userBalanceAction -> new RowData()
                        .setValues(List.of(
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setStringValue(formatter.format(userBalanceAction.getDate().atZone(ZoneId.of("UTC"))))
                                        ),
                                new CellData()
                                        .setUserEnteredValue(
                                                new ExtendedValue().setNumberValue(userBalanceAction.getAmountForGoogleSheet().setScale(2, RoundingMode.HALF_DOWN).doubleValue())
                                        )
                        ))).toList();
                googleSheetsApiService.updateCells(
                        user.getGoogleSheetId(),
                        new UpdateCellsRequest()
                                .setFields("*")
                                .setStart(new GridCoordinate()
                                        .setRowIndex(nextRowIndex)
                                        .setSheetId(commonSheetId)
                                        .setColumnIndex(0))
                                .setRows(batchRows)
                );

                nextRowIndex += batchSize;
                batch.clear();
            }
        }
    }

    private Long calculateSum(List<UserBalanceAction> allForSyncByUser) {
        long result = 0L;

        for (UserBalanceAction action : allForSyncByUser) {
            if (action.getType() == BalanceActionType.DEPOSIT) {
                result += action.getAmount().longValue();
            } else if (action.getType() == BalanceActionType.WITHDRAWAL) {
                result -= action.getAmount().longValue();
            }
        }

        return result;
    }

    @NotNull
    private List<RowData> getRowData(List<Order> ordersForSync, List<UserBalanceAction> balanceActions, Instrument instrument) {
        List<RowData> result = new ArrayList<>();
        // assume that balanceActions are sorted

        for(int i=0,j=0; i < ordersForSync.size() || j < balanceActions.size();) {
            Order order = i < ordersForSync.size() ? ordersForSync.get(i) : null;
            UserBalanceAction balanceAction = j < balanceActions.size() ? balanceActions.get(j) : null;

            if (order != null && (balanceAction == null || order.getCreatedAt().isBefore(balanceAction.getDate()))) {
                RowData rowData = createRowDataFromOrderDependingOnInstrument(order, instrument);
                result.add(rowData);
                i++;
            } else if (balanceAction != null && (order == null || balanceAction.getDate().isBefore(order.getCreatedAt()))) {
                RowData rowData = createRowDataFromBalanceAction(balanceAction);
                result.add(rowData);
                j++;
            }
        }
        return result;
    }

    private RowData createRowDataFromOrderDependingOnInstrument(Order order, Instrument instrument) {
        if (EUR_USD.equals(instrument.getLabel())) {
            return createRowDataFromOrderEURUSDHedge(order);
        } else if (USD_CAD.equals(instrument.getLabel())) {
            return createRowDataFromOrderUSDCADHedge(order);
        } else {
            log.error("Unsupported instrument mode: {}", instrument.getMode());
            throw new RuntimeException("Unsupported instrument mode: " + instrument.getMode());
        }
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private RowData createRowDataFromOrderEURUSDHedge(Order order) {
        BigDecimal euroAmount = order.getUserAmountForGoogleSheet();

        String rateInnerPart = "IF(B:B<0,(%s*%s+B:B*C:C)/G:G,%s)".formatted(relCell(-1, -1), relCell(0, -1), relCell(0, -1));
        String rateOuterPart = "=%s".formatted(isFirstRow("C:C", rateInnerPart));

        String depTotalInnerPart = "%s+J:J+%s".formatted(relCell(0, -1), relCell(+1, -1));
        String depTotalOuterPart = "=%s".formatted(isFirstRow("J:J", depTotalInnerPart));

        CellFormat currencyFormat = new CellFormat().setNumberFormat(new NumberFormat().setType("NUMBER").setPattern("#,##0.00"));
        CellFormat numberFormat = new CellFormat().setNumberFormat(new NumberFormat().setType("NUMBER").setPattern("0.00000"));
        return new RowData()
                .setValues(List.of(
                        // Дата
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue(formatter.format(order.getCreatedAt().atZone(ZoneId.of("UTC"))))
                                ),
                        // Сумма
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(euroAmount.doubleValue())
                                ),
                        // Фикс
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(order.getPrice().doubleValue())
                                ).setUserEnteredFormat(
                                        numberFormat
                                ),
                        // USDT
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B<>0,B:B*C:C,\"\")")
                                ).setUserEnteredFormat(
                                        currencyFormat
                                ),
                        // Ком. процент
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(order.getCommissionCoef().doubleValue())
                                ).setUserEnteredFormat(
                                        currencyFormat
                                ),
                        // Ком. 0,10
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(D:D<0,D:D*-E:E%,\"\")")
                                ).setUserEnteredFormat(
                                        currencyFormat
                                ),
                        // В позиции
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=B:B+%s".formatted(isFirstRow("0", relCell(0, -1))))
                                ),
                        // Rate
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(rateOuterPart)
                                ).setUserEnteredFormat(numberFormat),
                        // PnL
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B>0,(C:C/H:H-1)*B:B/C:C,\"\")")
                                ).setUserEnteredFormat(currencyFormat),
                        // Deposit
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B>0,-B:B*(C:C-H:H),-F:F)")
                                ).setUserEnteredFormat(currencyFormat),
                        // Balance
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(depTotalOuterPart)
                                ).setUserEnteredFormat(currencyFormat)
                ));
    }

    private RowData createRowDataFromOrderUSDCADHedge(Order order) {
        BigDecimal cadAmount = order.getUserAmountForGoogleSheet();
        BigDecimal usdtEquivalent = order.getBaseEquivalentForGoogleSheet();

        String rateInnerPart = "IF(B:B<0,(%s*%s+B:B*C:C)/G:G,%s)".formatted(relCell(-1, -1), relCell(0, -1), relCell(0, -1));
        String rateOuterPart = "=%s".formatted(isFirstRow("C:C", rateInnerPart));

        String depTotalInnerPart = "%s+J:J+%s".formatted(relCell(0, -1), relCell(+1, -1));
        String depTotalOuterPart = "=%s".formatted(isFirstRow("J:J", depTotalInnerPart));

        CellFormat currencyFormat = new CellFormat().setNumberFormat(new NumberFormat().setType("NUMBER").setPattern("#,##0.00"));
        CellFormat numberFormat = new CellFormat().setNumberFormat(new NumberFormat().setType("NUMBER").setPattern("0.00000"));
        return new RowData()
                .setValues(List.of(
                        // Дата
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue(formatter.format(order.getCreatedAt().atZone(ZoneId.of("UTC"))))
                                ),
                        // Сумма CAD
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(cadAmount.doubleValue())
                                ),
                        // Фикс
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(order.getPrice().doubleValue())
                                ).setUserEnteredFormat(
                                        numberFormat
                                ),
                        // USDT
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B<>0,B:B/C:C,\"\")")
                                ).setUserEnteredFormat(
                                        currencyFormat
                                ),
                        // Ком. процент
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(order.getCommissionCoef().doubleValue())
                                ).setUserEnteredFormat(
                                        currencyFormat
                                ),
                        // Ком. 0,10
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(D:D<0,D:D*-E:E%,\"\")")
                                ).setUserEnteredFormat(
                                        currencyFormat
                                ),
                        // Захеджировано
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=B:B+%s".formatted(isFirstRow("0", relCell(0, -1))))
                                ),
                        // Rate
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(rateOuterPart)
                                ).setUserEnteredFormat(numberFormat),
                        // PnL
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B>0,(C:C/H:H-1)*B:B/C:C,\"\")")
                                ).setUserEnteredFormat(currencyFormat),
                        // Deposit
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B>0,-(C:C/H:H-1)*B:B/C:C,-F:F)")
                                ).setUserEnteredFormat(currencyFormat),
                        // Balance
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(depTotalOuterPart)
                                ).setUserEnteredFormat(currencyFormat)
                ));
    }

    private RowData createRowDataFromBalanceAction(UserBalanceAction balanceAction) {
        String rateInnerPart = "IF(B:B<0,(%s*%s+B:B*C:C)/F:F,%s)".formatted(relCell(-1, -1), relCell(0, -1), relCell(0, -1));
        String rateOuterPart = "=%s".formatted(isFirstRow("C:C", rateInnerPart));

        String depTotalInnerPart = "%s+H:H+%s".formatted(relCell(0, -1), relCell(+1, -1));
        String depTotalOuterPart = "=%s".formatted(isFirstRow("H:H", depTotalInnerPart));

        CellFormat currencyFormat = new CellFormat().setNumberFormat(new NumberFormat().setType("NUMBER").setPattern("#,##0.00"));
        CellFormat numberFormat = new CellFormat().setNumberFormat(new NumberFormat().setType("NUMBER").setPattern("0.00000"));
        BigDecimal amount = balanceAction.getAmountForGoogleSheet();
        return new RowData()
                .setValues(List.of(
                        // Дата
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue(formatter.format(balanceAction.getDate().atZone(ZoneId.of("UTC"))))
                                ),
                        // Сумма
                        new CellData(),
                        // Фикс
                        new CellData(),
                        // USDT
                        new CellData(),
                        // Ком. 0,10
                        new CellData(),
                        // Захеджировано
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=B:B+%s".formatted(isFirstRow("0", relCell(0, -1))))
                                ),
                        // Rate
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(rateOuterPart)
                                ).setUserEnteredFormat(numberFormat),
                        // Deposit
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B>0,(C:C/G:G-1)*B:B/C:C,-E:E)")
                                ).setUserEnteredFormat(currencyFormat),
                        // Deposit total
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(depTotalOuterPart)
                                ).setUserEnteredFormat(currencyFormat),
                        // Payment deposit amount
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setNumberValue(amount.doubleValue())
                                ).setUserEnteredFormat(currencyFormat),
                        // Balance
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=I:I+J:J")
                                ).setUserEnteredFormat(currencyFormat)
                ));
    }

    private String isFirstRow(String tStm, String fStm) {
        return "IF(ROW()=2,%s,%s)".formatted(tStm, fStm);
    }

    private String relCell(Integer x, Integer y) {
        String yStr = y != 0 ? String.format("%+d", y) : "";
        String xStr = x != 0 ? String.format("%+d", x) : "";
        return "INDIRECT(ADDRESS(ROW()%s,COLUMN()%s))".formatted(yStr, xStr);
    }

    @SneakyThrows
    private void createTablesForUsersIfNotExist(Spreadsheet spreadsheet, AppUser user) {

        outer: for (AppUserInstrument userInstrument : user.getAvailableInstruments()) {
            Instrument instrument = userInstrument.getInstrument();
            Integer sheetId = userInstrument.getSheetId();

            String expectedId = getTableIdForUser(user, instrument);
            for (Sheet sheet : spreadsheet.getSheets()) {
                List<ArrayMap<String, Object>> tables = (List<ArrayMap<String, Object>>) sheet.get("tables");
                if (tables != null) {
                    for (ArrayMap<String, Object> table : tables) {
                        if (expectedId.equals(table.get("tableId"))) {
                            log.info("Table with id {} already exists for user {}", expectedId, user.getTelegramId());
                            continue outer;
                        }
                    }
                }
            }

            ArrayMap<String, Object> addTableRequest = new ArrayMap<>();
            addTableRequest.put("table", getCreateTableRequestBasedOnTheInstrument(user, instrument, expectedId, sheetId));

            BatchUpdateSpreadsheetResponse execute = sheets.spreadsheets().batchUpdate(user.getGoogleSheetId(),
                    new BatchUpdateSpreadsheetRequest().setRequests(
                            List.of(
                                    new Request().set("addTable", addTableRequest)
                            )
                    )).execute();
            log.info("Table created for user {} with id {}, {}", user.getTelegramId(), expectedId, execute);
        }

    }

    @NotNull
    private ArrayMap<String, Object> getCreateTableRequestBasedOnTheInstrument(AppUser user, Instrument instrument, String expectedId, Integer sheetId) {
        List<? extends ArrayMap<String, Object>> coloumnProperties = switch (instrument.getLabel()) {
            case EUR_USD -> EUR_USD_COLUMNS_PROPS;
            case USD_CAD -> USD_CAD_COLUMNS_PROPS;
            default -> new ArrayList<>();
        };

        ArrayMap<String, Object> arrayMap = new ArrayMap<>() {{
            put("name", getTableNameForUser(user, instrument));
            put("tableId", expectedId);
            put("range", new ArrayMap<String, Object>() {{
                put("sheetId", sheetId);
                put("startColumnIndex", 0);
                put("endColumnIndex", coloumnProperties.size());
                put("startRowIndex", 0);
            }});
            put("columnProperties", coloumnProperties);
        }};
        return arrayMap;
    }

    @SneakyThrows
    private void remove0SheetIfExists(Spreadsheet spreadsheet, AppUser user) {
        List<Integer> sheetIds = spreadsheet.getSheets().stream().map(s -> s.getProperties().getSheetId()).toList();

        if (!sheetIds.contains(0)) {
            log.info("Sheet with id 0 does not exist for user {}", user.getTelegramId());
            return;
        }

        sheets.spreadsheets().batchUpdate(user.getGoogleSheetId(),
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(
                                List.of(
                                        new Request()
                                                .setDeleteSheet(new DeleteSheetRequest().setSheetId(0)
                                                )
                                )
                        )).execute();
    }

    @SneakyThrows
    private void removeInstrumentSheetIfExists(Spreadsheet spreadsheet, AppUserInstrument userInstrument) {
        List<Integer> sheetIds = spreadsheet.getSheets().stream().map(s -> s.getProperties().getSheetId()).toList();

        Integer sheetId = userInstrument.getSheetId();
        AppUser user = userInstrument.getUser();
        if (!sheetIds.contains(sheetId)) {
            log.info("Instrument Sheet with id {} does not exist for user {}", sheetId, user.getTelegramId());
            return;
        }

        sheets.spreadsheets().batchUpdate(user.getGoogleSheetId(),
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(
                                List.of(
                                        new Request()
                                                .setDeleteSheet(new DeleteSheetRequest().setSheetId(sheetId)
                                                )
                                )
                        )).execute();
    }

    @SneakyThrows
    private void createSheetsForUserIfNotExist(Spreadsheet spreadsheet, AppUser user) {
        List<Integer> sheetIds = spreadsheet.getSheets().stream().map(s -> s.getProperties().getSheetId()).toList();

        if (!sheetIds.contains(AppConstants.COMMON_SHEET_ID)) {
            log.info("Creating common sheet with id {} already exists for user {}", AppConstants.COMMON_SHEET_ID, user.getTelegramId());
            AddSheetRequest addCommonSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties()
                            .setTitle(AppConstants.COMMON_SHEET_TITLE)
                            .setSheetId(AppConstants.COMMON_SHEET_ID)
                            .setGridProperties(
                                    new GridProperties()
                                            .setRowCount(1000)
                                            .setColumnCount(26)
                            )
                    );
            googleSheetsApiService.createSheet(user.getGoogleSheetId(), addCommonSheetRequest);
        }

        for (AppUserInstrument userInstrument : user.getAvailableInstruments()) {
            Instrument instrument = userInstrument.getInstrument();
            Integer sheetId = userInstrument.getSheetId();

            if (sheetIds.contains(sheetId)) {
                log.info("Sheet with id {} already exists for user {} instrument {}", sheetId, user.getTelegramId(), instrument.getLabel());
                continue;
            }

            AddSheetRequest addSheetRequest = new AddSheetRequest()
                    .setProperties(new SheetProperties()
                            .setTitle(instrument.getLabel())
                            .setSheetId(sheetId)
                            .setGridProperties(
                                    new GridProperties()
                                            .setRowCount(1000)
                                            .setColumnCount(26)
                            )
                    );
            googleSheetsApiService.createSheet(user.getGoogleSheetId(), addSheetRequest);
        }

    }

    @SneakyThrows
    private void createSpreadSheet(AppUser user) {
        Spreadsheet spreadsheet = new Spreadsheet().setProperties(new SpreadsheetProperties().setTitle(getSpreadsheetTitle(user)));
        spreadsheet = sheets.spreadsheets().create(spreadsheet)
                .setFields("spreadsheetId")
                .execute();
        log.info("Spreadsheet created for user {} {}", user.getTelegramId(), spreadsheet.getSpreadsheetId());

        for (String email : this.config.getEmails()) {
            drive.permissions().create(spreadsheet.getSpreadsheetId(),
                    new Permission()
                            .setType("user")
                            .setRole("writer")
                            .setEmailAddress(email)
            ).setSendNotificationEmail(true).execute();
        }
        user.setGoogleSheetId(spreadsheet.getSpreadsheetId());
    }

    private String getSpreadsheetTitle(AppUser user) {
        if (user.getTelegramUsername() != null) {
            return user.getTelegramUsername();
        } else if (user.getTelegramFirstName() != null) {
            return user.getTelegramFirstName();
        } else {
            return user.getTelegramId().toString();
        }
    }

    private String getTableIdForUser(AppUser user, Instrument instrument) {
        return "%s_%s".formatted(user.getTelegramId().toString(), instrument.getLabel());
    }

    private String getTableNameForUser(AppUser user, Instrument instrument) {
        return "HEDGE_%s_%s".formatted(user.getTelegramId(), instrument.getLabel().replace("/","_"));
    }

    private String getMetadataKeyForInstrument(Instrument instrument) {
        return "%s_%s_%s".formatted(MD_KEY_SHEET_LAST_SYNC_DT, instrument.getBase(), instrument.getQuote());
    }

    @AllArgsConstructor
    private static class ColumnDefinition {
        private String columnName;
        private String columnType;
    }

    private static final List<ColumnDefinition> EUR_USD_COLUMNS = List.of(
            new ColumnDefinition("Дата (UTC)", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Сумма", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Фикс", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("USDT", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ком. 0.1%", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Захеджировано", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ставка", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Реализ. курсовая", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Депозит", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Баланс", "COLUMN_TYPE_UNSPECIFIED")
    );

    private static final List<ColumnDefinition> USD_CAD_COLUMNS = List.of(
            new ColumnDefinition("Дата (UTC)", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Сумма CAD", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Фикс", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("USDT", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ком. процент", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ком.", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("В позиции", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ставка", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Реализ. курсовая", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Депозит", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Баланс", "COLUMN_TYPE_UNSPECIFIED")
    );

    AtomicInteger ai = new AtomicInteger(0);
    List<? extends ArrayMap<String, Object>> EUR_USD_COLUMNS_PROPS = EUR_USD_COLUMNS.stream().map(c -> new ArrayMap<String, Object>() {{
        put("columnIndex", ai.getAndIncrement());
        put("columnName", c.columnName);
        put("columnType", c.columnType);
    }}).toList();

    AtomicInteger ai1 = new AtomicInteger(0);
    List<? extends ArrayMap<String, Object>> USD_CAD_COLUMNS_PROPS = USD_CAD_COLUMNS.stream().map(c -> new ArrayMap<String, Object>() {{
        put("columnIndex", ai1.getAndIncrement());
        put("columnName", c.columnName);
        put("columnType", c.columnType);
    }}).toList();
}
