package org.ipan.hedging.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@Profile("!test")
public class ConfigTelegram {
    @Value("${app.telegram.bot.token}")
    String botToken = "12345:YOUR_TOKEN";

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsLongPollingApplication(RentEnergyBot rentEnergyBot) throws TelegramApiException {
        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(botToken, rentEnergyBot);
        return botsApplication;
    }

    @Bean
    public TelegramClient getTelegramClient() {
        TelegramClient telegramClient = new OkHttpTelegramClient(botToken);
        return telegramClient;
    }
}
