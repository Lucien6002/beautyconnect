# BeautyConnect

Plateforme web de mise en relation entre clients et prestataires de beaute
independants (coiffure, onglerie, soins). Projet base sur les specifications
fonctionnelles fournies (personas Client / Professionnel / Administrateur).

## Stack technique

- **Backend** : Java 21, Spring Boot 4.0 (Spring MVC, Spring Data JPA, Spring Security, Spring Mail)
- **Frontend** : Thymeleaf (rendu cote serveur) + Bootstrap 5 (CDN) + CSS custom
- **Base de donnees** : PostgreSQL (H2 en memoire pour les tests automatises)
- **Build** : Gradle (wrapper fourni, aucune installation locale requise)

Architecture en monolithe : le backend Spring Boot sert directement les
pages HTML via Thymeleaf (pas d'API REST separee dans cette base).

## Perimetre couvert

- Creation de compte Client et de compte Professionnel
- Creation/consultation du profil professionnel (vitrine : bio, ville, tarifs, clientele visee)
- Gestion des prestations (nom, type, duree, tarif) et des creneaux de disponibilite
- Recherche de prestataires avec filtres : ville, type de prestation, sexe de la clientele visee
- Prise de rendez-vous, confirmation/refus par le professionnel
- Envoi d'un mail de confirmation de rendez-vous
- Avis clients et signalements (avis abusif, faux profil)
- Espace administrateur : validation des comptes professionnels, moderation des avis/signalements, metriques d'usage

Hors perimetre (cf. specifications) : la gestion des paiements.

## Demarrage rapide

### 1. Base de donnees

```bash
docker compose up -d
```

Cela demarre PostgreSQL sur `localhost:5433` (base `beautyconnect`,
utilisateur/mot de passe `beautyconnect`). Le port 5433 est utilise plutot
que le 5432 standard pour ne pas entrer en conflit avec une eventuelle
instance PostgreSQL deja installee localement sur la machine.

Sans Docker, installez PostgreSQL localement et creez une base + un
utilisateur correspondants, ou surchargez les variables d'environnement
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` pour pointer vers
votre propre instance.

### 2. Lancer l'application

```bash
./gradlew bootRun
```

L'application demarre sur [http://localhost:8080](http://localhost:8080).
Le schema de base de donnees est cree/mis a jour automatiquement au demarrage
(`spring.jpa.hibernate.ddl-auto=update`).

### 3. Compte administrateur

Un compte administrateur est cree automatiquement au premier demarrage s'il
n'en existe aucun :

- Email : `admin@beautyconnect.local`
- Mot de passe : `Admin123!`
- Client : Email :`lea@beautyconnect.local `/ Mdp : `Client123!`
- Pro : Email : `sarah@beautyconnect.local` / Mdp : `Pro12345!`

Personnalisable via les variables d'environnement `ADMIN_EMAIL` et
`ADMIN_PASSWORD` avant le premier demarrage.

### 4. Envoi de mail (confirmation de rendez-vous)

Par defaut, l'application tente d'envoyer les mails via un serveur SMTP local
(`localhost:1025`, compatible [MailHog](https://github.com/mailhog/MailHog)
ou [Mailtrap](https://mailtrap.io)). Si aucun serveur SMTP n'est disponible,
l'echec d'envoi est simplement journalise (`app.mail.fail-silently=true`) et
ne bloque pas la confirmation du rendez-vous.

Variables d'environnement disponibles : `MAIL_HOST`, `MAIL_PORT`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`,
`MAIL_FROM`.

## Tests

```bash
./gradlew test
```

Les tests s'executent avec le profil `test` (base H2 en memoire), ils ne
necessitent pas d'instance PostgreSQL.

## Structure du projet

```
src/main/java/com/beautyconnect/
  config/          Configuration Spring (securite, donnees initiales)
  controller/      Controleurs MVC (pages publiques, client, pro, admin)
  dto/             Formulaires (inscription, prestation, creneau, rendez-vous, avis...)
  exception/       Exceptions metier + gestionnaire global
  model/           Entites JPA (User, ProfessionalProfile, Prestation, TimeSlot, Appointment, Review, Report)
  repository/      Repositories Spring Data JPA
  security/        Integration Spring Security (UserDetails)
  service/         Logique metier

src/main/resources/
  application.properties   Configuration (BDD, mail, admin par defaut)
  templates/                Vues Thymeleaf (Bootstrap 5)
  static/                   CSS/JS

src/test/                  Tests (profil H2)
docker-compose.yml          PostgreSQL pour le developpement local
```

## Roles et parcours

| Role | Parcours principal |
|---|---|
| Client | Inscription -> recherche -> consultation profil -> prise de rendez-vous -> avis |
| Professionnel | Inscription -> attente de validation admin -> gestion profil/prestations/creneaux -> confirmation des rendez-vous |
| Administrateur | Validation des comptes professionnels, moderation des avis, traitement des signalements |

## Pistes d'evolution (hors base actuelle)

- Migration de `ddl-auto=update` vers une vraie strategie de migration (Flyway/Liquibase)
- Geolocalisation avancee (coordonnees GPS, calcul de distance, carte interactive)
- Upload de photos de profil / realisations (portfolio)
- Notifications par email plus completes (rappel avant rendez-vous, refus, etc.)
- Tests unitaires et d'integration complementaires (couverture des services et controleurs)
