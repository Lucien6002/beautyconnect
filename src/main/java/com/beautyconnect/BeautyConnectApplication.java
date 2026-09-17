package com.beautyconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entree de l'application (le fichier "main" que Java execute pour
 * demarrer). C'est la classe declaree comme "mainClass" dans build.gradle et
 * dans .vscode/launch.json.
 *
 * @SpringBootApplication est une annotation "raccourci" qui en combine 3 :
 * - @SpringBootConfiguration : cette classe peut elle-meme definir des beans.
 * - @EnableAutoConfiguration : laisse Spring Boot configurer automatiquement
 *   tout ce qu'il detecte sur le classpath (ex: comme spring-boot-starter-web
 *   est present, il configure automatiquement un serveur Tomcat embarque ;
 *   comme spring-boot-starter-data-jpa + le driver PostgreSQL sont presents,
 *   il configure automatiquement la connexion a la base de donnees, etc.).
 * - @ComponentScan : scanne automatiquement TOUTES les classes du package
 *   com.beautyconnect (et ses sous-packages : controller, service,
 *   repository, config, security...) a la recherche des annotations comme
 *   @Controller, @Service, @Repository, @Component, pour les transformer en
 *   "beans" geres par Spring. C'est ce mecanisme qui fait que chaque classe
 *   de ce projet est automatiquement detectee sans avoir besoin de les
 *   enregistrer une par une manuellement quelque part.
 */
@SpringBootApplication
public class BeautyConnectApplication {

    public static void main(String[] args) {
        // Demarre le "contexte" Spring : cree tous les beans, configure le
        // serveur web embarque, ouvre la connexion a la base de donnees, etc.
        // Une fois cette ligne terminee (sans erreur), l'application ecoute
        // les requetes HTTP sur le port configure (8080 par defaut, voir
        // application.properties).
        SpringApplication.run(BeautyConnectApplication.class, args);
    }
}
