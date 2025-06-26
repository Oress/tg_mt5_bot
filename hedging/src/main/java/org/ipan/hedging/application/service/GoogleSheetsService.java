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
import org.ipan.hedging.GoogleSheetsConfig;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.OrderType;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class GoogleSheetsService {
    private static final String MD_KEY_SHEET_LAST_SYNC_DT = "last_sheet_sync_dt";
    private static final String APPLICATION_NAME = "Hedging Bot";
    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE, DriveScopes.DRIVE_FILE);

    private final GoogleSheetsConfig config;
    private final UserRepo userRepo;
    private final Sheets sheets;
    private final Drive drive;
    private final OrderRepo orderRepo;

    public GoogleSheetsService(GoogleSheetsConfig config, UserRepo userRepo, OrderRepo orderRepo) throws IOException {
        this.config = config;
        this.userRepo = userRepo;

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
    }

    @Transactional
    @Async
    public void syncUser(Long userId) {
        try {
            Instant syncDt = Instant.now();
            AppUser user = userRepo.findById(userId).get();

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
        Instant lastSyncDt = Instant.EPOCH;
        Boolean createMeta = true;

        List<DataFilter> metadataFilters = List.of(
                new DataFilter()
                        .setDeveloperMetadataLookup(
                                new DeveloperMetadataLookup()
                                        .setMetadataKey(MD_KEY_SHEET_LAST_SYNC_DT)
                                        .setMetadataLocation(
                                                new DeveloperMetadataLocation()
                                                        .setSheetId(user.getSheetId())
                                        )
                        )
        );
        SearchDeveloperMetadataResponse execute = sheets.spreadsheets().developerMetadata().search(user.getGoogleSheetId(),
                new SearchDeveloperMetadataRequest().setDataFilters(metadataFilters)
        ).execute();

        List<MatchedDeveloperMetadata> matchedDeveloperMetadata = execute.getMatchedDeveloperMetadata();
        if (matchedDeveloperMetadata != null && !matchedDeveloperMetadata.isEmpty()) {
            String metadataValue = matchedDeveloperMetadata.get(0).getDeveloperMetadata().getMetadataValue();
            lastSyncDt = Instant.parse(metadataValue);
            createMeta = false;
        }

        List<Order> ordersForSync = orderRepo.findAllForSync(lastSyncDt, user.getTelegramId());

        if (ordersForSync.isEmpty()) {
            log.info("No orders to sync for user {}", user.getTelegramId());
            return;
        }

        CreateDeveloperMetadataRequest createMt = new CreateDeveloperMetadataRequest()
                .setDeveloperMetadata(new DeveloperMetadata()
                        .setVisibility("DOCUMENT")
                        .setMetadataId(user.getSheetId())
                        .setMetadataKey(MD_KEY_SHEET_LAST_SYNC_DT)
                        .setMetadataValue(syncDt.toString())
                        .setLocation(
                                new DeveloperMetadataLocation()
                                        .setSheetId(user.getSheetId())
                        )
                );

        UpdateDeveloperMetadataRequest updateMt = new UpdateDeveloperMetadataRequest()
                .setDataFilters(metadataFilters)
                .setFields("*")
                .setDeveloperMetadata(new DeveloperMetadata()
                        .setVisibility("DOCUMENT")
                        .setMetadataId(user.getSheetId())
                        .setMetadataKey(MD_KEY_SHEET_LAST_SYNC_DT)
                        .setMetadataValue(syncDt.toString())
                        .setLocation(
                                new DeveloperMetadataLocation()
                                        .setSheetId(user.getSheetId())
                        )
                );

        BatchUpdateSpreadsheetResponse batchUpdate = sheets.spreadsheets().batchUpdate(
                user.getGoogleSheetId(),
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(
                                List.of(
                                        new Request().setAppendCells(
                                                new AppendCellsRequest()
                                                        .setFields("*")
                                                        .setSheetId(user.getSheetId())
                                                        .setRows(ordersForSync.stream().map(this::createRowDataFromOrder).toList())
                                        ),
                                        createMeta
                                                ? new Request().setCreateDeveloperMetadata(createMt)
                                                : new Request().setUpdateDeveloperMetadata(updateMt)
                                )
                        )
        ).execute();
        log.info("result batchUpdate {}", batchUpdate);
    }

    private RowData createRowDataFromOrder(Order order) {
        BigDecimal euroAmount = order.getEuroAmount().multiply(OrderType.OPEN.equals(order.getType()) ? BigDecimal.valueOf(-1) : BigDecimal.ONE);

        String rateInnerPart = "IF(B:B<0,(%s*%s+B:B*C:C)/F:F,%s)".formatted(relCell(-1, -1), relCell(0, -1), relCell(0, -1));
        String rateOuterPart = "=%s".formatted(isFirstRow("C:C", rateInnerPart));

        String depTotalInnerPart = "%s+H:H+%s".formatted(relCell(0, -1), relCell(+1, -1));
        String depTotalOuterPart = "=%s".formatted(isFirstRow("H:H", depTotalInnerPart));

        return new RowData()
                .setValues(List.of(
                        // Дата
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue(order.getCreatedAt().toString())
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
                                ),
                        // USDT
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B<>0,B:B*C:C,\"\")")
                                ),
                        // Ком. 0,10
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(D:D<0,D:D*-0.1%,\"\")")
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
                                ),
                        // Deposit
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=IF(B:B>0,-B:B*(C:C-G:G),-E:E)")
                                ),
                        // Deposit total
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue(depTotalOuterPart)
                                ),
                        // Payment deposit amount
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setStringValue("")
                                ),
                        // Balance
                        new CellData()
                                .setUserEnteredValue(
                                        new ExtendedValue().setFormulaValue("=I:I+J:J")
                                )
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
        String expectedId = getTableIdForUser(user);
        for (Sheet sheet : spreadsheet.getSheets()) {
            List<ArrayMap<String, Object>> tables = (List<ArrayMap<String, Object>>) sheet.get("tables");
            if (tables != null) {
                for (ArrayMap<String, Object> table : tables) {
                    if (expectedId.equals(table.get("tableId"))) {
                        log.info("Table with id {} already exists for user {}", expectedId, user.getTelegramId());
                        return;
                    }
                }
            }
        }

        ArrayMap<String, Object> addTableRequest = new ArrayMap<>();
        addTableRequest.put("table", new ArrayMap<String, Object>() {{
            put("name", getTableNameForUser(user));
            put("tableId", expectedId);
            put("range", new ArrayMap<String, Object>() {{
                put("sheetId", user.getSheetId());
                put("startColumnIndex", 0);
                put("endColumnIndex", SHORT_COLUMNS_PROPS.size());
                put("startRowIndex", 0);
            }});
            put("columnProperties", SHORT_COLUMNS_PROPS);
        }});

        BatchUpdateSpreadsheetResponse execute = sheets.spreadsheets().batchUpdate(user.getGoogleSheetId(),
                new BatchUpdateSpreadsheetRequest().setRequests(
                        List.of(
                                new Request().set("addTable", addTableRequest)
                        )
                )).execute();
        log.info("Table created for user {} with id {}, {}", user.getTelegramId(), expectedId, execute);
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
    private void createSheetsForUserIfNotExist(Spreadsheet spreadsheet, AppUser user) {
        List<Integer> sheetIds = spreadsheet.getSheets().stream().map(s -> s.getProperties().getSheetId()).toList();

        if (sheetIds.contains(user.getSheetId())) {
            log.info("Sheet with id {} already exists for user {}", user.getSheetId(), user.getTelegramId());
            return;
        }

        AddSheetRequest addSheetRequest = new AddSheetRequest()
                .setProperties(new SheetProperties()
                        .setTitle(getSpreadsheetTitle(user))
                        .setSheetId(user.getSheetId())
                        .setGridProperties(
                                new GridProperties()
                                        .setRowCount(1000)
                                        .setColumnCount(26)
                        )
                );
        BatchUpdateSpreadsheetResponse execute = sheets.spreadsheets().batchUpdate(user.getGoogleSheetId(),
                new BatchUpdateSpreadsheetRequest().setRequests(
                        List.of(new Request().setAddSheet(addSheetRequest))
                )
        ).execute();
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

    private String getTableIdForUser(AppUser user) {
        return user.getTelegramId().toString();
    }

    private String getTableNameForUser(AppUser user) {
        return "HEDGE_%s".formatted(user.getTelegramId());
    }

    @AllArgsConstructor
    private static class ColumnDefinition {
        private String columnName;
        private String columnType;
    }

    private static final List<ColumnDefinition> SHORT_COLUMNS = List.of(
            new ColumnDefinition("Дата", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Сумма", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Фикс", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("USDT", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ком. 0.1%", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Захеджировано", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Ставка", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Депозин", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Депозит  всего", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Сумма оплаты депозита", "COLUMN_TYPE_UNSPECIFIED"),
            new ColumnDefinition("Баланс", "COLUMN_TYPE_UNSPECIFIED")
    );

    AtomicInteger ai = new AtomicInteger(0);
    List<? extends ArrayMap<String, Object>> SHORT_COLUMNS_PROPS = SHORT_COLUMNS.stream().map(c -> new ArrayMap<String, Object>() {{
        put("columnIndex", ai.getAndIncrement());
        put("columnName", c.columnName);
        put("columnType", c.columnType);
    }}).toList();
}
