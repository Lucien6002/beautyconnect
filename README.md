# BeautyConnect

Application de réservation de prestations de beauté : coiffure, onglerie et soins. Les pages sont rendues par Spring MVC et Thymeleaf dans une seule application Spring Boot ; aucun serveur frontend séparé ni installation npm n'est nécessaire.

## Lancer en local

Prérequis : **JDK 21**, Docker avec Docker Compose et un navigateur. Le wrapper télécharge Gradle au premier lancement.

```bash
# À la racine du dépôt
cp -n .env.example .env
# Si .env existe déjà, le conserver et compléter uniquement les variables manquantes.
docker compose up -d db mailpit
bash ./gradlew bootRun
```

Ouvrir **http://localhost:8080**. Les messages d'activation et de réservation arrivent dans **http://localhost:8025** (Mailpit, aucun email externe en configuration locale). PostgreSQL écoute sur `localhost:5433`. Le profil par défaut est `dev` ; Flyway applique les migrations et Hibernate vérifie le schéma. Une base vide est créée automatiquement par PostgreSQL au premier démarrage du conteneur.

Pour arrêter : `Ctrl+C` dans le terminal Spring, puis `docker compose stop`. Cette commande conserve les données. Éviter `docker compose down -v` : elle supprimerait la base.

Le frontend utilise des polices, Bootstrap, des icônes et les tuiles de carte sur Internet. Le catalogue reste consultable si le routage ORS n'est pas configuré.

## Comptes de développement

Ces comptes connus sont créés uniquement en développement. Ne jamais utiliser ces mots de passe en production.

| Rôle | Email | Mot de passe |
|---|---|---|
| Administrateur | `admin@beautyconnect.local` | `Admin123!` |
| Client | `lea@beautyconnect.local` | `Client123!` |
| Professionnelle | `sarah@beautyconnect.local` | `Pro12345!` |

Les valeurs `ADMIN_EMAIL` et `ADMIN_PASSWORD`, si définies, remplacent celles de l'administrateur. Les initialiseurs ne réinitialisent pas les mots de passe des comptes existants.

## Remplir le catalogue pour une présentation

Dans `.env`, ajouter :

```dotenv
DEMO_FRANCE_ENABLED=true
DEMO_FRANCE_CITY_LIMIT=30
```

Relancer Spring. Jusqu'à **90 professionnels**, sur **30 communes**, avec 180 prestations actives et des scénarios de réservation, favoris et avis sont générés. Pour une petite base, choisir par exemple `DEMO_FRANCE_CITY_LIMIT=5`. Repasser ensuite `DEMO_FRANCE_ENABLED=false` : les données enregistrées restent disponibles.

| Scénario | Email | Mot de passe |
|---|---|---|
| Client avec historique, favori et avis | `client1@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Deux autres clients | `client2@demo.beautyconnect.invalid`, `client3@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Professionnel à Paris | `coiffure.75056@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Professionnel à Lyon | `onglerie.69123@demo.beautyconnect.invalid` | `DemoFrance123!` |

**Les établissements, comptes, prix, rendez-vous et avis de ce jeu sont fictifs.** Les noms des établissements n'ont pas de préfixe `[DÉMO]`, mais les emails techniques et les présentations identifient la simulation. Les coordonnées correspondent au centre des communes, pas à des salons réels. Certaines localisations restent privées ; certains comptes servent à tester l'attente de validation ou la suspension. Les photos des prestations doivent être ajoutées par un professionnel : aucun faux portfolio n'est généré.

L'initialiseur est réexécutable sans dupliquer les comptes ni écraser leurs modifications. Il retire aussi l'ancien préfixe `[DÉMO]` des seuls noms générés restés inchangés et remplace les prénoms techniques `Démo` restés intacts par des prénoms de présentation. Il ne renouvelle pas les créneaux d'un compte déjà créé : ajouter des disponibilités depuis l'espace professionnel si elles sont passées. Il est exclu dès que `prod` est actif.

## Ajouter des photos et activer Cloudinary

En local, cela fonctionne sans compte externe : **Mon profil** pour l'établissement, **Mon compte** pour l'avatar personnel et **Mes prestations → Photos** pour une galerie publique de cinq photos maximum par prestation. Le formulaire de création contient une photo principale et jusqu’à quatre photos secondaires, avec aperçu ; la prestation et ses photos sont enregistrées ensemble. La galerie permet ensuite de choisir une autre photo principale.

Les fichiers acceptés sont de vraies images **JPEG ou PNG**, au maximum **5 Mo et 20 mégapixels**. Le serveur les décode puis les réencode ; le nom original n'est pas utilisé comme chemin. Les photos de profil professionnel et de prestations sont publiques, comme les avatars servis par URL. N'envoyer que des images destinées à être publiées.

Les champs suivants sont déjà prévus dans `.env.example` et dans le `.env` local préparé pour cette branche :

```dotenv
IMAGE_STORAGE_PROVIDER=local
IMAGE_UPLOAD_DIR=uploads
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
CLOUDINARY_FOLDER=beautyconnect
CLOUDINARY_FOLDER_MODE=dynamic
```

Pour Cloudinary : renseigner les trois identifiants depuis les paramètres du compte, passer `IMAGE_STORAGE_PROVIDER=cloudinary`, puis **redémarrer Spring**. Le fichier `.env` est lu comme un fichier de propriétés : saisir les valeurs sans guillemets. Aucun upload preset n'est nécessaire : l'authentification se fait entre le serveur et Cloudinary. Le secret n'est jamais envoyé au navigateur. `.env` et `.env.local` sont ignorés par Git ; seul `.env.example` contient des valeurs vides.

Au premier envoi réussi, les images sont placées dans le dossier **`beautyconnect`** de la Media Library. `dynamic` convient aux environnements Cloudinary récents ; pour un ancien environnement utilisant les dossiers fixes, choisir `CLOUDINARY_FOLDER_MODE=fixed`. Le code utilise `asset_folder` ou `folder` selon ce mode ([référence officielle Cloudinary](https://cloudinary.com/documentation/image_upload_api_reference)).

Les anciens fichiers locaux ne sont pas transférés automatiquement. Conserver `uploads/` pour qu'ils restent visibles, y compris après activation de Cloudinary. Les nouveaux fichiers enregistrent leur fournisseur et leur identifiant de suppression. Garder les identifiants Cloudinary tant que des images distantes sont utilisées. Pour le stockage local déployé, monter un volume persistant accessible en écriture et le sauvegarder ; les disques éphémères d'un hébergeur ne conviennent pas.

Une suppression physique est déclenchée après validation de la transaction SQL. Si le fournisseur échoue, l'image n'est plus affichée mais un avertissement serveur identifie le nettoyage à reprendre ; aucune file de reprise automatique n'est encore implémentée. Les tests Cloudinary simulent l'API : une recette avec les vrais identifiants reste nécessaire.

## Parcours disponibles

- **Visiteur** : accueil, catalogue, recherche par ville/type/clientèle/nom, carte, vitrine et galerie.
- **Client** : inscription et activation par email, réservation immédiatement confirmée, annulation, historique, favoris, avis et signalements, modification du compte.
- **Professionnel** : profil et photo, catalogue avec édition/retrait/restauration, galerie, disponibilités, agenda et rendez-vous terminés. La suppression définitive est refusée si la prestation appartient à un rendez-vous.
- **Administrateur** : validation des professionnels, suspension, modération des avis et décisions de signalement avec auteur/date/motif.

La publication d'une localisation exige l'accord du professionnel. Sans `ORS_API_KEY`, les liens Google Maps restent disponibles. Avec une clé ORS côté serveur, la recherche propose des mesures à pied sur les candidats de la page et un itinéraire ; ce classement n'est pas une comparaison exhaustive de tous les professionnels de France.

Les avis libres et les avis rattachés à un rendez-vous terminé coexistent actuellement. Un avis libre n'est pas une preuve de prestation réalisée. La règle finale doit être validée par l'équipe.

## Tests et recette manuelle

```bash
# Tests H2 et API externes simulées, sans Docker obligatoire
bash ./gradlew test
# Construire le JAR
bash ./gradlew bootJar
# Rapport HTML : build/reports/tests/test/index.html
```

Pour tester aussi les migrations sur PostgreSQL local, Docker doit être démarré :

```bash
POSTGRES_TEST_URL=jdbc:postgresql://localhost:5433/beautyconnect \
POSTGRES_TEST_USER=beautyconnect POSTGRES_TEST_PASSWORD=beautyconnect \
bash ./gradlew test --rerun-tasks
```

Ce test utilise des schémas temporaires isolés et les supprime ; il ne vide pas les tables de l'application. Sans ces variables, le test PostgreSQL est ignoré. La CI les renseigne avec son propre service PostgreSQL.

La [recette manuelle par rôle](docs/RECETTE-MANUELLE.md) fournit les étapes et résultats attendus à cocher soi-même. Le [reste à faire de l'équipe](docs/ROADMAP-EQUIPE.md) sépare les corrections nécessaires des décisions produit et des validations externes. La [revue de l'intégration d'Ana](docs/REVUE-INTEGRATION-ANA.md) explique les corrections apportées à son travail.

## Structure et configuration

```text
src/main/java/com/beautyconnect/
  config/        sécurité, initialiseurs, ressources locales
  controller/    routes et préparation des vues
  dto/           formulaires et validation
  model/         entités JPA
  repository/    accès SQL via Spring Data
  service/       règles métier et stockage des photos
src/main/resources/
  templates/     pages Thymeleaf et fragments partagés
  static/        CSS, JavaScript et images de Joyce
  db/migration/  migrations Flyway versionnées
  demo/          coordonnées des communes utilisées en démonstration
src/test/        tests automatisés
docs/            recette, revue et tâches restantes
```

Spring Boot 4, Java 21, PostgreSQL 16, Spring Security par session, Thymeleaf, Bootstrap et Leaflet. Les changements de structure SQL doivent passer par une **nouvelle migration** ; ne pas modifier une migration déjà appliquée.

Les valeurs de `.env.local` prennent priorité sur `.env`. En cas de configuration inattendue, vérifier aussi les variables exportées par le terminal ou l'IDE. Pour une connexion PostgreSQL différente, adapter `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, ou utiliser `SPRING_DATASOURCE_URL` au format JDBC. En production, injecter les secrets dans l'environnement de l'hébergeur et ne pas copier les fichiers `.env` locaux dans l'image ni sur le serveur.

## Mise en ligne

Voir [DEPLOIEMENT-ULRICH.md](docs/DEPLOIEMENT-ULRICH.md). Utiliser `SPRING_PROFILES_ACTIVE=prod`, une base et des secrets distincts, une URL `APP_BASE_URL` HTTPS, un SMTP réel et un stockage persistant ou Cloudinary. La présence du Dockerfile et de `render.yaml` ne signifie pas que le service est déjà déployé. L'environnement réel doit passer la recette avant une présentation publique.
