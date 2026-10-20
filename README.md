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

Le diaporama d’accueil change d’image toutes les **8 secondes** ; il se met en pause au survol, pendant le focus clavier ou si l’onglet est masqué.

Après connexion, un client arrive à l’accueil pour chercher une prestation ; les professionnels et administrateurs arrivent dans leur tableau de bord.

La connexion expire après **2 heures d’inactivité en développement**, **30 minutes en production** (`SESSION_TIMEOUT` permet de changer cette durée). Le cookie de session porte un nom distinct par port pour éviter les collisions entre les versions sur 8080 et 8097. Un redémarrage Spring peut fermer la session ; il faut alors se reconnecter.

Pour arrêter : `Ctrl+C` dans le terminal Spring, puis `docker compose stop`. Cette commande conserve les données. Éviter `docker compose down -v` : elle supprimerait la base.

Le frontend utilise des polices, Bootstrap, des icônes et les tuiles de carte sur Internet. Le catalogue reste consultable si le routage ORS n'est pas configuré. La recherche affiche six établissements par page ; les pages suivantes conservent les filtres.

## Comptes de développement

Ces comptes connus sont créés uniquement en développement. Ne jamais utiliser ces mots de passe en production.

| Rôle | Email | Mot de passe |
|---|---|---|
| Administrateur | `admin@beautyconnect.local` | `Admin123!` |
| Client | `lea@beautyconnect.local` | `Client123!` |
| Professionnelle | `sarah@beautyconnect.local` | `Pro12345!` |

Les valeurs `ADMIN_EMAIL` et `ADMIN_PASSWORD`, si définies, remplacent celles de l'administrateur. Les initialiseurs ne réinitialisent pas les mots de passe des comptes existants.

## Catalogue de présentation : six établissements

Le jeu de présentation contient **six établissements au total**, répartis entre Paris, Lyon, Lille, Nantes, Bordeaux et Marseille. Chaque établissement appartient à un compte professionnel utilisable : sa fiche, ses prestations, ses disponibilités et ses photos se modifient dans l'espace professionnel.

Après récupération du code, sur une base **vide ou déjà remplie**, ajouter `DEMO_FRANCE_ENABLED=true` à `.env`, démarrer Spring avec `bash ./gradlew bootRun`, puis remettre la valeur à `false` pour les prochains démarrages. Le profil doit être `dev`. Flyway crée les tables manquantes ; l'initialiseur ajoute les six établissements, leurs prestations, les liens des photos partagées, des créneaux futurs et trois clients de recette. Aucun mail ni rendez-vous ni avis n'est créé.

Le démarrage lit [showcase-catalog.json](src/main/resources/demo/showcase-catalog.json), versionné avec le code : **Git ne transporte pas la base**, chaque poste importe ce catalogue dans sa propre base. Les images communes sont déjà publiées sur Cloudinary dans `beautyconnect/catalogue`. Le navigateur les affiche par leur URL publique : **aucune clé Cloudinary n'est nécessaire pour les voir**, même avec `IMAGE_STORAGE_PROVIDER=local`. Une connexion Internet et la disponibilité de ces ressources Cloudinary sont nécessaires.

Sur une base existante, l'import conserve les mots de passe, les comptes désactivés, la validation, les positions privées, les prix, les prestations retirées, les réservations et les photos personnelles. Une couverture ou une galerie vide peut être complétée au premier import. Les prestations sont rapprochées par propriétaire, catégorie et nom connu ; une prestation déjà renommée avant ce premier import peut donc coexister avec l'exemple ajouté. Aucun rapprochement automatique par simple ressemblance n'est fait.

La table `showcase_imports` mémorise les éléments traités. Relancer l'import ne rétablit pas les photos et créneaux supprimés ensuite, ni les prestations renommées ou supprimées, ni les profils supprimés dont le compte existe encore. Conserver cette table et les clés stables du catalogue. **L'import ne supprime pas les anciens établissements** : une base contenant déjà plus de six profils les conserve ; six est le total sur une base vide. L'ancien réglage `DEMO_FRANCE_CITY_LIMIT` n'est plus utilisé au démarrage.

| Établissement | Compte professionnel | Mot de passe de développement |
|---|---|---|
| Sarah — Paris | `sarah@beautyconnect.local` | `Pro12345!` |
| Chloé — Lyon | `chloe@beautyconnect.local` | `Pro12345!` |
| Coiffure — Lille | `coiffure.59350@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Onglerie — Nantes | `onglerie.44109@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Onglerie — Bordeaux | `onglerie.33063@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Maquillage — Marseille | `soin.13055@demo.beautyconnect.invalid` | `DemoFrance123!` |

Clients de recette : `client1@demo.beautyconnect.invalid`, `client2@demo.beautyconnect.invalid`, `client3@demo.beautyconnect.invalid` ; mot de passe commun `DemoFrance123!`. Léa et l'administrateur restent disponibles avec les identifiants ci-dessus.

**Les établissements, tarifs et prestations sont fictifs.** Les coordonnées indiquent le centre des communes, pas l'adresse de commerces réels. Les photos partagées sont importées comme données en base, restent remplaçables depuis les formulaires professionnels et ne sont pas codées dans les templates. Supprimer ou remplacer une photo partagée retire seulement son lien de la base concernée : le fichier commun n'est pas détruit chez Cloudinary, pour préserver l'affichage sur les autres postes. Les nouvelles photos personnelles suivent le fournisseur configuré et leur cycle habituel de suppression.

La base locale a été réduite depuis l'ancien catalogue de 90 profils supplémentaires en conservant Sarah et Chloé, les prestations personnalisées et les photos existantes. Une sauvegarde locale a été réalisée avant le nettoyage. À la demande de recette, **un seul rendez-vous fictif confirmé chez Sarah est conservé**, sur un créneau futur ; les anciens avis liés aux huit autres rendez-vous ont été retirés. Une base nouvellement initialisée ne crée aucun rendez-vous ni avis automatiquement : les scénarios se font manuellement.

Le jeu est exclu dès que le profil `prod` est actif. Pour conserver des essais manuels, laisser l'initialisation désactivée.

## Ajouter des photos et activer Cloudinary

En local, cela fonctionne sans compte externe : **Mon profil** pour l'établissement, **Mon compte** pour l'avatar personnel et **Mes prestations → Photos** pour une galerie publique de cinq photos maximum par prestation. Le formulaire de création contient une photo principale et jusqu’à quatre photos secondaires, avec aperçu ; la prestation et ses photos sont enregistrées ensemble. La galerie permet ensuite de choisir une autre photo principale.

Les photos principales s’affichent en entier dans les cadres de la recherche, de l’accueil et des fiches, sans couper les portraits ou les réalisations. Les fichiers WebP sont décodés puis réencodés en PNG avant stockage.

Les fichiers acceptés sont de vraies images **JPEG, PNG ou WebP**, au maximum **5 Mo et 20 mégapixels**. Le serveur les décode puis les réencode ; le nom original n'est pas utilisé comme chemin. Les photos de profil professionnel et de prestations sont publiques, comme les avatars servis par URL. N'envoyer que des images destinées à être publiées.

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

Si les identifiants sont valides mais que l’envoi renvoie HTTP 403 avec `missing permissions (actions=["create"])`, Cloudinary refuse la création d’actifs dans cet environnement. Vérifier dans Settings → API Keys que la clé est active et que les trois identifiants appartiennent au même environnement. Ce refus ne prouve pas à lui seul que la clé est la cause : vérifier les droits de l’environnement avec son administrateur ou le support Cloudinary si le problème persiste. Il n’existe pas nécessairement un bouton « create » dans la console. Renseigner les clés sans activer `IMAGE_STORAGE_PROVIDER=cloudinary` conserve le stockage local.

Au premier envoi réussi, les images sont placées dans le dossier **`beautyconnect`** de la Media Library. `dynamic` convient aux environnements Cloudinary récents ; pour un ancien environnement utilisant les dossiers fixes, choisir `CLOUDINARY_FOLDER_MODE=fixed`. Le code utilise `asset_folder` ou `folder` selon ce mode ([référence officielle Cloudinary](https://cloudinary.com/documentation/image_upload_api_reference)).

Dans **Prestations → Modifier**, vous pouvez remplacer la principale, choisir une photo existante comme couverture, ajouter des secondaires et cocher les suppressions. Les photos et les informations sont enregistrées ensemble ; si un fichier est refusé, les anciennes valeurs sont conservées. La limite reste de cinq photos.

Les anciens fichiers locaux ne sont pas transférés automatiquement. Conserver `uploads/` pour qu'ils restent visibles, y compris après activation de Cloudinary. Les nouveaux fichiers enregistrent leur fournisseur et leur identifiant de suppression. Garder les identifiants Cloudinary pour gérer les photos personnelles distantes ; la simple lecture des photos publiques du catalogue ne les nécessite pas. Pour le stockage local déployé, monter un volume persistant accessible en écriture et le sauvegarder ; les disques éphémères d'un hébergeur ne conviennent pas.

Pour une photo personnelle, une suppression physique est déclenchée après validation de la transaction SQL. Si le fournisseur échoue, l'image n'est plus affichée mais un avertissement serveur identifie le nettoyage à reprendre ; aucune file de reprise automatique n'est encore implémentée. La suite automatisée simule Cloudinary pour ne pas utiliser les secrets pendant les tests. La recette locale du 20 octobre 2026 a confirmé les envois, le remplacement et la suppression sur Cloudinary via les formulaires, ainsi que l’annulation des modifications sur image invalide. La recette manuelle complète de l’équipe reste à exécuter.

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

Spring Boot 4, Java 21, PostgreSQL 16, Spring Security par session, Thymeleaf, Bootstrap et Leaflet.

**Compatibilité des V8 :** `main` utilisait V8 pour la table d’images de Teddy ; une ancienne version de cette branche utilisait V8 pour les identifiants de stockage. L’application reconnaît les scripts V8 et V9 déjà enregistrés et conserve leurs noms, contenus et checksums, y compris la V9 intermédiaire de développement. Une base vide utilise la V8 de Teddy ; la V9 commune ajoute les éléments manquants et la V10 récupère les anciennes images. Aucun `repair` ni changement de l’historique existant n’est nécessaire. Lancer les migrations via l’application ou les tests du projet, qui chargent cette compatibilité ; un appel Flyway externe avec sa configuration par défaut ne sélectionnera pas la bonne V8.

Les changements de structure SQL doivent passer par une **nouvelle migration** ; ne pas modifier une migration déjà appliquée.

Les valeurs de `.env.local` prennent priorité sur `.env`. En cas de configuration inattendue, vérifier aussi les variables exportées par le terminal ou l'IDE. Pour une connexion PostgreSQL différente, adapter `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, ou utiliser `SPRING_DATASOURCE_URL` au format JDBC. En production, injecter les secrets dans l'environnement de l'hébergeur et ne pas copier les fichiers `.env` locaux dans l'image ni sur le serveur.

## Mise en ligne

Voir [DEPLOIEMENT-ULRICH.md](docs/DEPLOIEMENT-ULRICH.md). Utiliser `SPRING_PROFILES_ACTIVE=prod`, une base et des secrets distincts, une URL `APP_BASE_URL` HTTPS, un SMTP réel et un stockage persistant ou Cloudinary. La présence du Dockerfile et de `render.yaml` ne signifie pas que le service est déjà déployé. L'environnement réel doit passer la recette avant une présentation publique.
