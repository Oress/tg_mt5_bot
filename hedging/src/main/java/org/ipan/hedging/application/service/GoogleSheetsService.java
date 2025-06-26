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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.GoogleSheetsConfig;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class GoogleSheetsService {
    private static final String APPLICATION_NAME = "Hedging Bot";

    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE, DriveScopes.DRIVE_FILE);

    private final GoogleSheetsConfig config;
    private final UserRepo userRepo;
    private final Sheets sheets;
    private final Drive drive;

    public GoogleSheetsService(GoogleSheetsConfig config, UserRepo userRepo) throws IOException {
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
    }

    @Transactional
    @Async
    public void syncUser(Long userId) {
        try {
            AppUser user = userRepo.findById(userId).get();

            if (user.getGoogleSheetId() == null) {
                createSpreadSheet(user);
            }

            Spreadsheet spreadsheet = sheets.spreadsheets().get(user.getGoogleSheetId()).setIncludeGridData(false).execute();
            createSheetsForUserIfNotExist(spreadsheet, user);
            remove0SheetIfExists(spreadsheet, user);

            create_tables_for_users_if_not_exist(spreadsheet, user);





        } catch (Exception e) {
            log.error("Something went wrong during sync ", e);
        }
    }

    private void create_tables_for_users_if_not_exist(Spreadsheet spreadsheet, AppUser user) {
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


//        sheets.spreadsheets().batchUpdate(user.getGoogleSheetId(),
//                new BatchUpdateSpreadsheetRequest().setRequests(
//                        List.of(
//                                new AddTable
//                        )
//                ))
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

}
