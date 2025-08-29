package org.ipan.hedging.application.service;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.GoogleSheetsConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
@Profile("!test")
public class GoogleSheetsApiService {
    private static final String MD_KEY_SHEET_LAST_SYNC_DT = "last_sheet_sync_dt";
    private static final String APPLICATION_NAME = "Hedging Bot";
    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE, DriveScopes.DRIVE_FILE);
    public static final String EUR_USD = "EUR/USD";
    public static final String USD_CAD = "USD/CAD";

    private final GoogleSheetsConfig config;
    private final Sheets sheets;
    private final Drive drive;

    public GoogleSheetsApiService(GoogleSheetsConfig config) throws IOException {
        this.config = config;

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

    @Retryable
    public void updateCells(String sheetId, UpdateCellsRequest request) throws IOException {
        sheets.spreadsheets().batchUpdate(
                sheetId,
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(
                                List.of(
                                        new Request().setUpdateCells(request)
                                )
                        )
        ).execute();
    }

    public void clearSheet(String googleSheetId, int sheetId) throws IOException {
        sheets.spreadsheets().batchUpdate(
                googleSheetId,
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(
                                List.of(
                                        new Request().setDeleteRange(
                                                new DeleteRangeRequest()
                                                        .setRange(
                                                                new GridRange()
                                                                        .setSheetId(sheetId)
                                                        )
                                                        .setShiftDimension("ROWS"))
                                )
                        )
        ).execute();

    }
}
