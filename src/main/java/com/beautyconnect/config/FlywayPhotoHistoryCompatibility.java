package com.beautyconnect.config;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Préserve les V8 et V9 réellement appliquées lors de la réunion des branches. */
@Configuration(proxyBeanMethods = false)
public class FlywayPhotoHistoryCompatibility {
    public static final String MAIN_V8 = "V8__prestation_images.sql";
    public static final String DESIGN_V8 = "V8__unified_photo_storage.sql";
    public static final String COMPLETE_V9 = "V9__complete_photo_schema.sql";
    public static final String LEGACY_V9 = "V9__prestation_images.sql";

    @Bean
    FlywayConfigurationCustomizer preserveAppliedPhotoHistory() { return configuration -> configure(configuration); }

    public static FluentConfiguration configure(FluentConfiguration configuration) {
        String selected = MAIN_V8;
        String selectedV9 = COMPLETE_V9;
        try (var connection = configuration.getDataSource().getConnection()) {
            String schema = configuration.getDefaultSchema();
            if (schema == null) schema = connection.getSchema();
            boolean exists = false;
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), schema,
                    configuration.getTable(), new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (schema.equals(tables.getString("TABLE_SCHEM")) && configuration.getTable().equals(tables.getString("TABLE_NAME"))) exists = true;
                }
            }
            if (exists) {
                String history = quote(schema) + "." + quote(configuration.getTable());
                try (var statement = connection.prepareStatement("SELECT version, script FROM " + history + " WHERE version IN ('8','9') AND success = true"); var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        if ("8".equals(rows.getString(1))) selected = rows.getString(2);
                        if ("9".equals(rows.getString(1))) selectedV9 = rows.getString(2);
                    }
                }
            }
        } catch (java.sql.SQLException ex) { throw new IllegalStateException("Impossible de lire l'historique Flyway.", ex); }
        return configuration.resourceProvider(migrations(selected, selectedV9, configuration.getEncoding()));
    }

    private static String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }

    /** Les noms relatifs et checksums restent ceux enregistrés par Flyway. */
    public static ResourceProvider migrations(String selected, Charset encoding) {
        return migrations(selected, COMPLETE_V9, encoding);
    }

    public static ResourceProvider migrations(String selected, String selectedV9, Charset encoding) {
        if (!List.of(COMPLETE_V9, LEGACY_V9).contains(selectedV9)) throw new IllegalStateException("Migration V9 inconnue : " + selectedV9);
        if (!List.of(MAIN_V8, DESIGN_V8).contains(selected)) throw new IllegalStateException("Migration V8 inconnue : " + selected);
        var resources = new ArrayList<LoadableResource>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*")) {
                String name = resource.getFilename();
                if (List.of(MAIN_V8, DESIGN_V8).contains(name) && !name.equals(selected)) continue;
                if (List.of(COMPLETE_V9, LEGACY_V9).contains(name) && !name.equals(selectedV9)) continue;
                String absolute = resource.getURL().toExternalForm();
                resources.add(new LoadableResource() {
                    @Override public Reader read() {
                        try { return new InputStreamReader(resource.getInputStream(), encoding); }
                        catch (java.io.IOException ex) { throw new IllegalStateException("Migration illisible : " + name, ex); }
                    }
                    @Override public String getFilename() { return name; }
                    @Override public String getRelativePath() { return name; }
                    @Override public String getAbsolutePath() { return absolute; }
                    @Override public String getAbsolutePathOnDisk() { return absolute; }
                });
            }
        } catch (java.io.IOException ex) { throw new IllegalStateException("Chargement des migrations impossible.", ex); }
        return new ResourceProvider() {
            @Override public LoadableResource getResource(String name) {
                return resources.stream().filter(resource -> resource.getFilename().equals(name)).findFirst().orElse(null);
            }
            @Override public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
                return resources.stream().filter(resource -> resource.getFilename().startsWith(prefix)
                        && Arrays.stream(suffixes).anyMatch(suffix -> resource.getFilename().endsWith(suffix))).toList();
            }
        };
    }
}
