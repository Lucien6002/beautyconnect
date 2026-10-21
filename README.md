# BeautyConnect

Application de réservation de prestations de beauté : coiffure, onglerie et soins. Les pages sont rendues par Spring MVC et Thymeleaf dans une seule application Spring Boot ; aucun serveur frontend séparé ni installation npm n'est nécessaire.

## Lancer et évaluer l’application

### 1. Prérequis

| Outil | Utilisation | Vérification |
|---|---|---|
| Git | Récupérer le projet | `git --version` |
| JDK **21** | Compiler et lancer Spring Boot | `java -version` et `javac -version` |
| Docker avec Docker Compose | Lancer PostgreSQL et Mailpit | `docker --version` puis `docker compose version` |
| Navigateur et Internet | Consulter les pages, les photos publiques et la carte | Chrome, Firefox ou Edge récent |

Démarrer Docker avant de continuer. Gradle est fourni par le wrapper du dépôt ; il n’est pas nécessaire d’installer Gradle ou npm. Le premier lancement télécharge les dépendances et peut prendre plusieurs minutes. Les commandes ci-dessous s’exécutent dans un terminal, depuis la racine du projet, où se trouvent `gradlew`, `build.gradle` et `docker-compose.yml`.

### 2. Récupérer le code et préparer la configuration

Pour une première installation sous Linux, macOS ou un terminal Bash :

```bash
git clone https://github.com/Lucien6002/beautyconnect.git
cd beautyconnect
cp .env.example .env
```

Sous Windows PowerShell, les deux premières commandes sont identiques ; remplacer la copie par :

```powershell
Copy-Item .env.example .env
```

**Si le projet et un `.env` existent déjà**, conserver ce fichier. Depuis la racine, vérifier le travail local avant de récupérer `main` :

```bash
git status
git fetch origin
git switch main
git pull --ff-only
```

Les modifications Git locales doivent être conservées ou intégrées avant de changer de branche. Si Git refuse le changement de branche ou la mise à jour, résoudre ce point sans écraser le travail local. Ne pas remplacer la configuration locale par celle d’un autre membre.

Le `.env.example` contient une configuration locale utilisable avec le Docker Compose fourni :

```dotenv
DB_HOST=localhost
DB_PORT=5433
DB_NAME=beautyconnect
DB_USER=beautyconnect
DB_PASSWORD=beautyconnect
APP_BASE_URL=http://localhost:8080
MAIL_HOST=localhost
MAIL_PORT=1025
IMAGE_STORAGE_PROVIDER=local
DEMO_FRANCE_ENABLED=true
```

Ce bloc est un extrait : copier le fichier `.env.example` complet. Aucune clé Cloudinary ni ORS n’est obligatoire pour cette évaluation. Saisir les valeurs du `.env` sans guillemets ; Spring le lit comme un fichier de propriétés. `.env` et `.env.local` sont ignorés par Git. Les valeurs de `.env.local` sont prioritaires sur celles de `.env` ; vérifier aussi les variables du terminal ou de l’IDE en cas de différence inattendue.

### 3. Démarrer la base et la boîte mail locale

```bash
docker compose up -d db mailpit
docker compose ps
```

Les services `db` et `mailpit` doivent être démarrés ; attendre que `db` soit sain (`healthy`) avant de lancer Spring. Le volume Docker conserve les données entre deux démarrages.

| Service | Adresse depuis votre ordinateur | Fonction |
|---|---|---|
| PostgreSQL | `localhost:5433`, base/utilisateur/mot de passe `beautyconnect` | Données de l’application |
| Mailpit, interface web | http://localhost:8025 | Lire les mails d’activation et de réservation |
| Mailpit, SMTP | `localhost:1025` | Réception des mails envoyés par Spring |

Il n’est pas nécessaire d’installer PostgreSQL séparément ni de créer les tables à la main. Le conteneur crée la base au premier démarrage ; Flyway crée ou met à jour les tables lors du lancement de Spring. Mailpit intercepte les messages localement : aucun destinataire réel n’est contacté avec cette configuration.

### 4. Lancer Spring Boot

Depuis la racine du dépôt :

```bash
bash ./gradlew bootRun
```

Sous Windows PowerShell :

```powershell
.\gradlew.bat bootRun
```

Laisser ce terminal ouvert. Le profil par défaut est **`dev`**. Avec le catalogue actif (réglage par défaut en `dev`), le journal affiche le démarrage de Spring, puis une ligne commençant par :

```text
Présentation France : 6 villes, ... comptes créés
```

Attendre cette ligne avant d’ouvrir l’application. Sur une base vide, six profils professionnels de présentation sont créés. Sur une base déjà préparée, `0 comptes créés` est normal : les éléments déjà importés sont conservés. Le détail de l’import se trouve dans la section « Catalogue de présentation : six établissements ».

Ouvrir **http://localhost:8080**, puis **http://localhost:8025** pour les mails. Le frontend est servi par Spring : aucun deuxième serveur frontend n’est à lancer.

### 5. Arrêter et reprendre une session

Arrêter Spring avec `Ctrl+C` dans son terminal, puis arrêter les conteneurs si nécessaire :

```bash
docker compose stop
```

Pour reprendre, exécuter à nouveau `docker compose up -d db mailpit`, puis `bash ./gradlew bootRun` (ou la commande Windows). Les données sont conservées. **Ne pas utiliser `docker compose down -v` pour un simple arrêt : cette commande supprime le volume de la base.**

Une modification du `.env` ou une mise à jour du code nécessite un redémarrage de Spring. Un simple rafraîchissement du navigateur ne relance pas l’initialiseur. PostgreSQL et Mailpit peuvent rester démarrés.

### 6. Vérifier rapidement le fonctionnement

1. Ouvrir `/recherche` : sur une base neuve, six établissements sont proposés dans six villes, avec couvertures et photos de prestations.
2. Se connecter comme client : l’application renvoie à l’accueil pour rechercher une prestation.
3. Se connecter comme Sarah : l’espace professionnel permet de modifier les prestations, les photos et les disponibilités.
4. Se connecter comme administrateur : les pages de validation et de modération sont accessibles.
5. Créer une réservation de test selon le parcours d’évaluation décrit plus bas ; le même rendez-vous doit apparaître chez le client et le professionnel, avec un mail dans Mailpit.

Le fond de connexion est une version WebP optimisée de l’image de Joyce (environ 100 Ko). Le diaporama d’accueil change d’image toutes les **8 secondes** ; il se met en pause au survol, pendant le focus clavier ou si l’onglet est masqué.

La session expire après **2 heures d’inactivité en développement**, **30 minutes en production** ; `SESSION_TIMEOUT` permet d’adapter cette durée. Un redémarrage de Spring peut fermer la session. Deux onglets du même navigateur partagent le compte connecté : utiliser des profils de navigateur séparés pour comparer les rôles. Le cookie de session est distinct pour chaque port d’application.

### Variante : lancer le JAR

Pour exécuter le JAR construit localement, garder PostgreSQL et Mailpit démarrés et lancer, depuis la racine contenant le `.env` :

```bash
bash ./gradlew bootJar
java -jar build/libs/beautyconnect-0.1.0-SNAPSHOT.jar
```

Sous Windows, remplacer la première commande par `.\gradlew.bat bootJar`.

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

Après les étapes de configuration et de démarrage Docker ci-dessus, lancer Spring avec `bash ./gradlew bootRun` : le catalogue est **importé automatiquement à chaque démarrage en développement**, sur une base vide ou déjà remplie. Aucun argument de lancement particulier n’est nécessaire. Si votre ancien `.env` ou `.env.local` contient `DEMO_FRANCE_ENABLED=false`, retirer cette ligne ou la passer à `true` une fois : une désactivation explicite reste prioritaire. Le profil doit être `dev`. Flyway crée les tables manquantes ; l'initialiseur ajoute les six établissements, leurs prestations, les liens des photos partagées, des créneaux futurs et trois clients de recette. Aucun mail ni rendez-vous ni avis n'est créé.

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

Une installation neuve ne contient **aucun rendez-vous ni avis de présentation** : le professeur crée ses propres réservations et évaluations. Les éventuels rendez-vous d’une autre base locale ne sont pas transmis par Git.

Le jeu est exclu dès que le profil `prod` est actif, même si le drapeau vaut `true`. Les essais manuels restent conservés lors des réexécutions grâce à `showcase_imports`. Pour empêcher tout ajout du catalogue dans une base de développement, définir explicitement `DEMO_FRANCE_ENABLED=false`. Ne pas lancer simultanément deux imports sur une même base : la vérification puis la création d’un élément n’est pas verrouillée entre plusieurs instances. Les différents postes de l’équipe peuvent chacun démarrer sur leur propre base.

## Ajouter des photos et activer Cloudinary

En local, cela fonctionne sans compte externe : **Mon profil** pour l'établissement, **Mon compte** pour l'avatar personnel et **Mes prestations → Photos** pour une galerie publique de cinq photos maximum par prestation. Le formulaire de création contient une photo principale et jusqu’à quatre photos secondaires, avec aperçu ; la prestation et ses photos sont enregistrées ensemble. La galerie permet ensuite de choisir une autre photo principale.

Les photos principales s’affichent en entier dans les cadres de la recherche, de l’accueil et des fiches, sans couper les portraits ou les réalisations. Les fichiers WebP sont décodés puis réencodés en PNG avant stockage.

Les fichiers acceptés sont de vraies images **JPEG, PNG ou WebP**, au maximum **5 Mo et 20 mégapixels**. Le serveur les décode puis les réencode ; le nom original n'est pas utilisé comme chemin. Les photos de profil professionnel et de prestations sont publiques, comme les avatars servis par URL. N'envoyer que des images destinées à être publiées.

Les champs suivants sont prévus dans `.env.example`, puis copiés dans votre `.env` lors de l’installation :

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

La publication d'une localisation exige l'accord du professionnel. « Utiliser ma position » demande une position récente en haute précision ; la précision réelle dépend du navigateur et de l’appareil. Sur localhost ou HTTPS, autoriser la localisation dans le navigateur et le système. Le professionnel voit la précision annoncée et peut corriger le point avant de publier. La localisation reste utilisable si le fond de carte ne charge pas. Sans `ORS_API_KEY`, les liens Google Maps restent disponibles. Avec une position client, les établissements correspondant aux filtres sont triés à vol d’oiseau avant pagination, en plaçant les coordonnées privées ou manquantes en dernier. La carte se centre sur la position reçue ; le bouton « Afficher tous les points sur la carte » permet de revoir les points. Le rayon de 100 km limite le calcul piéton, sans masquer les établissements plus éloignés. Avec une clé ORS côté serveur, la recherche propose des mesures à pied sur les candidats de la page et un itinéraire ; ce classement piéton n'est pas une comparaison exhaustive de tous les professionnels de France.

Le message facultatif saisi lors d’une réservation est affiché comme « Message du client » chez le professionnel ; ce n’est pas une évaluation. Après passage du rendez-vous à « Terminé », le client peut choisir « Noter ce rendez-vous » dans son historique pour donner une note sur 5 et un commentaire facultatif. Un avis déjà envoyé n’est plus proposé à nouveau, même s’il a été masqué. La modération masque l’avis public et l’exclut de la moyenne, tout en conservant sa trace en base.

Les avis libres et les avis rattachés à un rendez-vous terminé coexistent actuellement. Un avis libre n'est pas une preuve de prestation réalisée. La règle finale doit être validée par l'équipe.

## Parcours d’évaluation pour le professeur

Utiliser trois profils de navigateur distincts ou se déconnecter entre chaque rôle. Les comptes du tableau ci-dessus sont prêts à l’emploi ; l’inscription d’un nouveau compte permet aussi de tester l’activation par email.

| Étape | Action | Résultat attendu |
|---|---|---|
| Visiteur | Ouvrir l’accueil, rechercher Paris, consulter Sarah, ses prestations et ses photos. | Fiche publique accessible, prix et photos visibles, carte cohérente avec les coordonnées publiques. |
| Professionnel | Avec Sarah, créer une prestation « Évaluation — test » avec un prix, une durée, une principale et deux photos secondaires. Modifier ensuite le prix et une photo. | Informations et galerie conservées après rechargement ; image invalide refusée. |
| Disponibilité | Ajouter un créneau futur depuis l’espace de Sarah. | Le créneau apparaît sur sa vitrine et peut être réservé. |
| Réservation | Avec Léa ou le client 1, réserver cette prestation et ce créneau, avec un message facultatif. | Confirmation immédiate, rendez-vous visible des deux côtés, message visible chez Sarah et mail dans Mailpit. |
| Annulation | Annuler depuis le compte client. | Statut annulé chez le client et chez Sarah ; le créneau peut être réservé à nouveau. |
| Conflit | Avec deux clients distincts, tenter de réserver le même créneau. | Une seule réservation active peut occuper ce créneau exact. |
| Avis vérifié | Réserver un créneau de test, puis marquer le rendez-vous « Terminé » chez Sarah. Dans l’historique client, choisir « Noter ce rendez-vous », sélectionner une note et écrire un commentaire. | Avis lié au rendez-vous terminé et visible sur la fiche avec son indicateur de vérification. |
| Modération | Avec l’administrateur, ouvrir les avis puis masquer cet avis de test. | Avis conservé dans la modération mais retiré de la fiche publique et du calcul de la moyenne. |
| Retrait | Chez Sarah, retirer la prestation de test, annuler une première fois dans la fenêtre de confirmation puis confirmer. | L’annulation conserve la prestation ; la confirmation la déplace dans les prestations retirées. Elle peut être restaurée. |
| Redémarrage | Relancer Spring après avoir modifié une prestation et supprimé une photo de présentation. | Les modifications et suppressions ne sont pas annulées par l’import automatique. |

Pour la suppression définitive, utiliser une prestation temporaire qui n’a **aucun rendez-vous associé**. Une prestation utilisée dans un rendez-vous reste protégée pour conserver l’historique ; la retirer du catalogue est possible. Nettoyer uniquement les éléments créés pour l’évaluation.

Pour tester l’inscription, choisir une nouvelle adresse, puis ouvrir le mail reçu dans Mailpit et cliquer sur le lien d’activation. Un professionnel inscrit doit ensuite être validé par l’administrateur avant d’apparaître publiquement. Sur une installation déjà utilisée, les mots de passe des comptes de présentation peuvent avoir été modifiés : ils ne sont pas réinitialisés au démarrage.

## Dépannage du lancement et du catalogue

| Symptôme | Vérification / action |
|---|---|
| `docker compose` échoue ou le démon Docker est inaccessible | Démarrer Docker ou Docker Desktop, puis vérifier `docker compose ps`. |
| Spring ne se connecte pas à PostgreSQL | Vérifier que `db` est sain, que le port hôte est `5433` et que le `.env` vise la bonne base. Le Compose fourni fixe les identifiants à `beautyconnect` ; modifier le `.env` seul ne change pas ceux du conteneur existant. |
| Java ou la compilation ne démarre pas | Vérifier `java -version` et le JDK sélectionné par `JAVA_HOME` ou l’IDE : le projet demande Java 21. Le wrapper doit pouvoir télécharger ses dépendances sur Internet. |
| Le port `8080` est occupé | Arrêter l’autre application ou définir `PORT=8090` et `APP_BASE_URL=http://localhost:8090` dans `.env`, puis relancer. Adapter aussi l’adresse ouverte dans le navigateur. |
| Aucun établissement supplémentaire après mise à jour | Vérifier le profil `dev`, l’absence d’un ancien `DEMO_FRANCE_ENABLED=false` dans `.env` ou `.env.local`, puis arrêter et relancer Spring. Rechercher `Présentation France` dans le terminal. |
| Le journal indique `0 comptes créés` | Les comptes existaient déjà. Consulter `/recherche` ; le compteur ne signifie pas que l’import a échoué. Une base ancienne peut conserver plus de six établissements. |
| Une photo supprimée après l’import ne revient pas | C’est le comportement prévu : `showcase_imports` conserve la trace du traitement. Remplacer la photo depuis le formulaire professionnel si souhaité. |
| Les images publiques ne s’affichent pas | Vérifier l’accès Internet et l’URL de l’image dans un nouvel onglet. Les fichiers doivent toujours exister sur Cloudinary. Les clés ne sont pas requises pour lire ces URL publiques. Une photo personnelle déjà présente n’est pas écrasée par le catalogue. |
| Un ancien lien `/uploads/...` est cassé | Restaurer le fichier local correspondant dans `uploads/` depuis sa sauvegarde. Git et l’import du catalogue ne restaurent pas les photos personnelles locales. |
| Un nouvel envoi vers Cloudinary échoue | Pour évaluer sans compte Cloudinary, utiliser `IMAGE_STORAGE_PROVIDER=local`. Pour un envoi distant, configurer les trois identifiants et le fournisseur, puis redémarrer Spring. |
| Les mails ne sont pas dans une boîte réelle | En local, consulter Mailpit sur `http://localhost:8025`. Si aucun message n’y arrive, vérifier le service `mailpit` et `MAIL_HOST=localhost`, `MAIL_PORT=1025`. |
| « Ma position » est refusée ou imprécise | Utiliser localhost ou HTTPS, autoriser la localisation dans le navigateur et le système. La précision dépend de l’appareil ; le professionnel peut corriger son point sur la carte. |
| Aucun professionnel à moins de 100 km | Vérifier le point reçu et les filtres. Le catalogue de six villes ne couvre pas chaque commune française ; les professionnels plus éloignés restent consultables. |

Si le démarrage échoue encore, conserver le premier message d’erreur et sa cause dans le terminal, la version Java et le commit testé. Ne pas supprimer la base ou modifier l’historique Flyway pour contourner l’erreur.

## Tests automatisés


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

Équivalent sous Windows PowerShell :

```powershell
$env:POSTGRES_TEST_URL = 'jdbc:postgresql://localhost:5433/beautyconnect'
$env:POSTGRES_TEST_USER = 'beautyconnect'
$env:POSTGRES_TEST_PASSWORD = 'beautyconnect'
.\gradlew.bat test --rerun-tasks
```

Ce test utilise des schémas temporaires isolés et les supprime ; il ne vide pas les tables de l'application. Sans ces variables, le test PostgreSQL est ignoré. La CI les renseigne avec son propre service PostgreSQL.

La recette d’évaluation ci-dessus décrit les vérifications manuelles : une suite automatisée réussie ne remplace pas le contrôle des pages sur le poste du professeur.

## Limites connues de cette version

- Le doublon sur un créneau exact est bloqué ; le contrôle des chevauchements entre heures différentes tenant compte des durées reste à compléter.
- Le passage à « Terminé » contrôle le statut et le professionnel propriétaire, mais pas encore la fin effective du rendez-vous. Pour l’évaluation, utiliser des rendez-vous de test.
- Les avis libres sans rendez-vous et les avis vérifiés coexistent. Un avis libre ne prouve pas qu’une prestation a été réalisée.
- Les tarifs et durées sont lus dans la prestation référencée ; ils ne sont pas encore figés dans une copie des conditions à la réservation.
- Le classement piéton dépend d’ORS et porte sur les candidats annoncés ; une erreur externe utilise le repli à vol d’oiseau et les liens Google Maps.
- Les sauvegardes, les droits d’utilisation des images et les règles de conservation des données doivent être validés avant une mise en ligne réelle.


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
  demo/          communes et catalogue partagé des six établissements
src/test/        tests automatisés
```

Spring Boot 4, Java 21, PostgreSQL 16, Spring Security par session, Thymeleaf, Bootstrap et Leaflet.

**Compatibilité des V8 :** `main` utilisait V8 pour la table d’images de Teddy ; une ancienne version de cette branche utilisait V8 pour les identifiants de stockage. L’application reconnaît les scripts V8 et V9 déjà enregistrés et conserve leurs noms, contenus et checksums, y compris la V9 intermédiaire de développement. Une base vide utilise la V8 de Teddy ; la V9 commune ajoute les éléments manquants et la V10 récupère les anciennes images. Aucun `repair` ni changement de l’historique existant n’est nécessaire. Lancer les migrations via l’application ou les tests du projet, qui chargent cette compatibilité ; un appel Flyway externe avec sa configuration par défaut ne sélectionnera pas la bonne V8.

Les changements de structure SQL doivent passer par une **nouvelle migration** ; ne pas modifier une migration déjà appliquée.

Les valeurs de `.env.local` prennent priorité sur `.env`. En cas de configuration inattendue, vérifier aussi les variables exportées par le terminal ou l'IDE. Pour une connexion PostgreSQL différente, adapter `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, ou utiliser `SPRING_DATASOURCE_URL` au format JDBC. En production, injecter les secrets dans l'environnement de l'hébergeur et ne pas copier les fichiers `.env` locaux dans l'image ni sur le serveur.

## Mise en ligne

Le lancement décrit dans ce README est prévu pour l’évaluation locale. Pour un hébergement réel, utiliser `SPRING_PROFILES_ACTIVE=prod`, une base et des secrets distincts, une URL `APP_BASE_URL` HTTPS, un SMTP réel et un stockage persistant ou Cloudinary. La présence du Dockerfile et de `render.yaml` ne signifie pas que le service est déjà déployé. L'environnement réel doit passer la recette avant une présentation publique.


Le profil `prod` exclut les comptes de développement et le catalogue fictif : ne pas l’activer pour l’évaluation des six établissements. Le déploiement réel nécessite les sauvegardes de la base et des médias, HTTPS et un SMTP opérationnel.
