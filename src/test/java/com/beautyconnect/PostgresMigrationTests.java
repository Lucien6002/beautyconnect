package com.beautyconnect;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

/** Schémas isolés, jamais de clean/truncate sur les tables de l'application. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresMigrationTests {
    private final String url = System.getenv("POSTGRES_TEST_URL");
    private final String user = System.getenv("POSTGRES_TEST_USER");
    private final String password = System.getenv("POSTGRES_TEST_PASSWORD");
    private Flyway flyway(String schema) {
        return com.beautyconnect.config.FlywayPhotoHistoryCompatibility.configure(Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .baselineOnMigrate(true).baselineVersion("0")).load();
    }
    @Test void bothV8HistoriesConvergeWithoutChangingAppliedRows() throws Exception {
        for (String script : new String[]{com.beautyconnect.config.FlywayPhotoHistoryCompatibility.MAIN_V8,
                com.beautyconnect.config.FlywayPhotoHistoryCompatibility.DESIGN_V8}) {
            String schema = "v8_compat_" + System.nanoTime();
            try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
                try {
                    Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("8")
                            .resourceProvider(com.beautyconnect.config.FlywayPhotoHistoryCompatibility.migrations(script, StandardCharsets.UTF_8)).load().migrate();
                    sql.execute("SET search_path TO " + schema);
                    String before;
                    try (var rows = sql.executeQuery("SELECT row_to_json(h)::text FROM flyway_schema_history h WHERE version='8'")) {
                        rows.next(); before = rows.getString(1); assertThat(before).contains(script);
                    }
                    flyway(schema).migrate();
                    try (var rows = sql.executeQuery("SELECT row_to_json(h)::text FROM flyway_schema_history h WHERE version='8'")) {
                        rows.next(); assertThat(rows.getString(1)).isEqualTo(before);
                    }
                    sql.executeQuery("SELECT public_id FROM prestation_photos LIMIT 0").close();
                    sql.executeQuery("SELECT profile_photo_public_id FROM users LIMIT 0").close();
                    sql.executeQuery("SELECT profile_photo_public_id FROM professional_profiles LIMIT 0").close();
                    sql.executeQuery("SELECT public_id FROM prestation_images LIMIT 0").close();
                    assertThat(flyway(schema).validateWithResult().validationSuccessful).isTrue();
                    assertThat(flyway(schema).migrate().migrationsExecuted).isZero();
                } finally {
                    sql.execute("SET search_path TO public"); sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }

    @Test void preservesTheAlreadyAppliedIntermediateV9() throws Exception {
        String schema = "v9_compat_" + System.nanoTime();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try {
                Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                        .resourceProvider(com.beautyconnect.config.FlywayPhotoHistoryCompatibility.migrations(
                                com.beautyconnect.config.FlywayPhotoHistoryCompatibility.DESIGN_V8,
                                com.beautyconnect.config.FlywayPhotoHistoryCompatibility.LEGACY_V9, StandardCharsets.UTF_8)).load().migrate();
                sql.execute("SET search_path TO " + schema);
                String before;
                try (var rows = sql.executeQuery("SELECT json_agg(h ORDER BY installed_rank)::text FROM flyway_schema_history h")) {
                    rows.next(); before = rows.getString(1);
                }
                assertThat(flyway(schema).validateWithResult().validationSuccessful).isTrue();
                assertThat(flyway(schema).migrate().migrationsExecuted).isZero();
                try (var rows = sql.executeQuery("SELECT json_agg(h ORDER BY installed_rank)::text FROM flyway_schema_history h")) {
                    rows.next(); assertThat(rows.getString(1)).isEqualTo(before);
                }
            } finally {
                sql.execute("SET search_path TO public"); sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test void oldImageRecordsJoinTheUnifiedGalleryWithoutDuplicates() throws Exception {
        String schema = "media_upgrade_" + System.nanoTime();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try {
                com.beautyconnect.config.FlywayPhotoHistoryCompatibility.configure(Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).target("8")).load().migrate();
                sql.execute("SET search_path TO " + schema);
                sql.execute("INSERT INTO users(id,email,password,first_name,last_name,role,enabled,created_at) VALUES (1,'media@test','hash','Pro','Media','PROFESSIONAL',true,now())");
                sql.execute("INSERT INTO professional_profiles(id,user_id,business_name,city,target_gender,validated,created_at) VALUES(1,1,'Salon','Paris','MIXTE',true,now())");
                sql.execute("INSERT INTO prestations(id,professional_id,name,type,price,duration_minutes,active) VALUES(1,1,'Coupe','COIFFURE',20,30,true)");
                sql.execute("INSERT INTO prestation_photos(prestation_id,photo_url,display_order) VALUES(1,'/uploads/current.png',0)");
                sql.execute("INSERT INTO prestation_images(prestation_id,public_id,secure_url,display_order,created_at) VALUES(1,'current.png','/uploads/current.png',0,now()),(1,'legacy.png','/uploads/legacy.png',1,now())");
                flyway(schema).migrate();
                try (var rows = sql.executeQuery("SELECT photo_url, public_id FROM prestation_photos ORDER BY display_order")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("/uploads/current.png");
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("/uploads/legacy.png");
                    assertThat(rows.getString(2)).isEqualTo("local:legacy.png"); assertThat(rows.next()).isFalse();
                }
                assertThat(flyway(schema).validateWithResult().validationSuccessful).isTrue();
            } finally {
                sql.execute("SET search_path TO public"); sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @Test void newAndLegacySchemasMigrateWithoutLosingCancelledAppointments() throws Exception {
        String fresh = "audit_fresh_" + System.nanoTime();
        String legacy = "audit_legacy_" + System.nanoTime();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try {
                flyway(fresh).migrate();
                assertThat(flyway(fresh).validateWithResult().validationSuccessful).isTrue();
                sql.executeQuery("SELECT catalog_key, target_id, imported_at FROM " + fresh + ".showcase_imports LIMIT 0").close();
                sql.execute("CREATE SCHEMA " + legacy);
                sql.execute("SET search_path TO " + legacy);
                try (var resource = getClass().getResourceAsStream("/db/migration/V1__initial_schema.sql")) {
                    sql.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                }
                sql.execute("ALTER TABLE appointments ADD CONSTRAINT old_slot_unique UNIQUE(time_slot_id)");
                sql.execute("INSERT INTO users(id,email,password,first_name,last_name,role,enabled,created_at) VALUES (1,'pro@test','hash','Pro','Test','PROFESSIONAL',true,now()),(2,'client@test','hash','Client','Test','CLIENT',true,now())");
                sql.execute("INSERT INTO professional_profiles(id,user_id,business_name,city,target_gender,validated,created_at) VALUES(1,1,'Salon','Paris','MIXTE',true,now())");
                sql.execute("INSERT INTO prestations(id,professional_id,name,type,price,duration_minutes,active) VALUES(1,1,'Coupe','COIFFURE',20,30,true)");
                sql.execute("INSERT INTO time_slots(id,professional_id,start_date_time,available) VALUES(1,1,now()+interval '1 day',true)");
                sql.execute("INSERT INTO appointments(id,client_id,professional_id,prestation_id,time_slot_id,status,created_at) VALUES(1,2,1,1,1,'ANNULE',now())");
                flyway(legacy).migrate();
                sql.execute("INSERT INTO appointments(id,client_id,professional_id,prestation_id,time_slot_id,active_time_slot_id,status,created_at) VALUES(2,2,1,1,1,1,'EN_ATTENTE',now())");
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM appointments")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(2); }
                assertThatThrownBy(() -> sql.execute("INSERT INTO appointments(id,client_id,professional_id,prestation_id,time_slot_id,active_time_slot_id,status,created_at) VALUES(3,2,1,1,1,1,'EN_ATTENTE',now())"))
                        .isInstanceOf(SQLException.class);
                assertThat(flyway(legacy).validateWithResult().validationSuccessful).isTrue();
            } finally {
                sql.execute("SET search_path TO public");
                sql.execute("DROP SCHEMA IF EXISTS " + fresh + " CASCADE");
                sql.execute("DROP SCHEMA IF EXISTS " + legacy + " CASCADE");
            }
        }
    }
}
