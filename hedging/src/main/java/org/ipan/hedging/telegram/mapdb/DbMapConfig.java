package org.ipan.hedging.telegram.mapdb;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class DbMapConfig {

    @Value("${app.mapdb.db-path}")
    String dbPath = "cache/hedging.db";

    @Bean
    public DB db() {
        // TODO: handle this shit somehow Caused by: org.mapdb.DBException$FileLocked: File is already opened and is locked: cache/nrgyrent.db 
        File file = new File(dbPath);
        file.getParentFile().mkdirs(); // create folders if they do not exist
        return DBMaker.fileDB(file)
                .fileMmapEnable()
                .fileMmapPreclearDisable()
                .closeOnJvmShutdown()
                // .transactionEnable()
                .make();
    }
}
