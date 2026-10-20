# Déploiement et recette du lot Ulrich

Le lot client comprend les réservations, l’activation récupérable, la recherche paginée, la carte et le routage piéton. Le déploiement utilise une image Docker Java 21 et PostgreSQL. La création du service Render et le choix du plan restent à faire avec l’équipe ; aucun compte externe n’est créé automatiquement.

## Développement local

```bash
docker compose up -d
bash gradlew bootRun
```

Le profil par défaut est `dev`. Il crée les comptes de démonstration du README. Mailpit est accessible sur http://localhost:8025. Pour l’utiliser, fournir `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false` et `MAIL_SMTP_STARTTLS=false`. Les variables du processus priment sur `.env` et `.env.local`. `.env.local` conserve les anciennes valeurs SMTP locales, hors Git ; ne jamais le publier.

`APP_BASE_URL` sert à construire les liens d’activation. Un compte désactivé après vérification de son e-mail ne peut pas se réactiver par renvoi d’activation. Le formulaire de renvoi sur `/connexion` ne révèle pas si une adresse existe ; délai minimum d’une minute entre les renvois.

## Base et historique des rendez-vous

Flyway remplace `ddl-auto=update` ; Hibernate valide le schéma au démarrage. `V1` crée une base neuve ou conserve les tables existantes. `V2` retire l’ancienne unicité de `appointments.time_slot_id`, ajoute l’occupation unique `active_time_slot_id` et conserve les anciens rendez-vous. Refus/annulation libèrent l’occupation ; un rendez-vous terminé conserve son créneau historique occupé. La réservation verrouille le créneau en transaction. Une nouvelle demande peut ensuite utiliser le même créneau sans supprimer l’historique.

Avant la première migration sur une base contenant des données, Emmanuelle doit vérifier la sauvegarde et sa restauration sur une base de recette. Ne jamais modifier une migration déjà appliquée : ajouter une nouvelle version. La bascule d’une base héritée crée une baseline Flyway en version 0. Les bases existantes qui ont un autre historique Flyway doivent être examinées avant application.

## Carte et itinéraires

Un professionnel choisit son point et sa publication dans `/pro/localisation`, accessible depuis `/pro/profil`. Il peut utiliser un point approximatif ; le trajet mène au point choisi. Les coordonnées existantes restent privées jusqu’à son accord. Seuls les professionnels validés, actifs et ayant autorisé la publication apparaissent sur la carte. Les adresses des profils ne sont montrées publiquement qu’avec cet accord.

Le GPS du client est facultatif. Sa position n’est pas enregistrée dans `User`, les logs d’accès sont désactivés et les pages de recherche utilisent `Cache-Control: no-store` et `Referrer-Policy: strict-origin`. Les coordonnées figurent dans la requête du navigateur : la configuration des logs du proxy/hébergeur doit également exclure les query strings. Elles sont transmises au fournisseur ORS pour calculer le trajet. Le cache ORS reste uniquement en mémoire, jusqu’à cinq minutes, avec 128 entrées maximum.

`ORS_API_KEY` est une variable côté serveur. Sans clé, en cas de quota, timeout ou réponse invalide, aucun chemin simulé n’est présenté comme un trajet réel. La liste indique les distances à vol d’oiseau et propose Google Maps. Avec ORS, les 12 candidats les plus proches à vol d’oiseau de la page, dans un rayon de 100 km, sont mesurés puis triés par distance piétonne. Ce classement concerne les candidats mesurés de la page, pas tous les professionnels de la base. Les routes impossibles restent dans la liste sans distance piétonne.

`GET /itineraire/professionnels/{id}?lat=...&lon=...` renvoie géométrie, kilomètres et minutes. Coordonnées contrôlées ; 10 demandes par minute par session ; appels fournisseur limités à 30 par minute et par instance par défaut. Ajuster `ORS_REQUESTS_PER_MINUTE` au quota réel de la clé. Réponses limitées à 1 Mo et géométries à 10 000 points ; timeout HTTP de cinq secondes. Un déploiement multi-instance exige un limiteur partagé pour le quota global.

Les boutons Google Maps fournissent un repli à pied et les transports. BeautyConnect ne calcule pas les lignes ni horaires de bus. Le fond OpenStreetMap n’utilise pas la clé ORS ; respecter [sa politique de tuiles](https://operations.osmfoundation.org/policies/tiles/). `MAP_TILE_URL` permet de changer de fournisseur ; adapter aussi l’attribution dans le JavaScript si le fournisseur l’exige.

Références : [ORS Matrix](https://giscience.github.io/openrouteservice/api-reference/endpoints/matrix/), [ORS Directions](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/), [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started).

## Tests

```bash
bash gradlew test
```

La suite H2 n’utilise ni SMTP réel ni ORS externe. Le test PostgreSQL s’active uniquement avec `POSTGRES_TEST_URL`, `POSTGRES_TEST_USER` et `POSTGRES_TEST_PASSWORD`. Il utilise des schémas temporaires isolés et les supprime à la fin. Le workflow GitHub exécute ces tests avec PostgreSQL 16. Les résultats sont dans `build/reports/tests/test/index.html`.

## Construire et tester le conteneur

```bash
docker build -t beautyconnect:ulrich .
```

Le conteneur démarre en `prod`, sans comptes de démonstration. Préparer un fichier d’environnement local ignoré par Git avec les variables ci-dessous, puis lancer une instance de recette connectée à sa propre base PostgreSQL :

```bash
docker run --rm --name beautyconnect-recette -p 8080:8080 --env-file .env.prod beautyconnect:ulrich
```

Sur Linux, une base accessible seulement sur l’hôte requiert une adresse joignable depuis le conteneur, par exemple `--add-host=host.docker.internal:host-gateway`. Pour une recette locale en HTTP uniquement, ajouter `-e SERVER_SERVLET_SESSION_COOKIE_SECURE=false` ; garder les cookies sécurisés en production HTTPS.

| Variable | Valeur à fournir |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC `jdbc:postgresql://HOST:PORT/BASE`, paramètres TLS selon l’hébergeur |
| `DB_USER`, `DB_PASSWORD` | Identifiants de la base |
| `APP_BASE_URL` | URL HTTPS publique, sans chemin ajouté |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Premier administrateur ; mot de passe unique de 12 caractères minimum |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Configuration SMTP réelle |
| `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_FROM` | Options et expéditeur du fournisseur SMTP |
| `ORS_API_KEY` | Clé de routage, facultative pour le repli |
| `PORT` | Fourni par Render ; 8080 par défaut |

## Mise en ligne Render

1. Fusionner les lots validés sur `main` et vérifier la CI.
2. Avec l’accord de l’équipe sur le plan, la région et le coût, créer PostgreSQL et un service Web Docker relié au dépôt et à `main`.
3. Fournir les variables précédentes dans Render. Adapter l’URL PostgreSQL au format JDBC ; `postgresql://...` seule n’est pas une URL JDBC.
4. Utiliser `/` comme chemin de contrôle de santé. L’image démarre avec `SPRING_PROFILES_ACTIVE=prod` ; le port vient de `PORT`.
5. Déployer et effectuer la recette. Vérifier les logs sans y copier de secrets ni de positions GPS.

Le Dockerfile est la configuration choisie ; aucun Blueprint `render.yaml` n’impose de plan ou de coût à l’équipe. Voir [Docker sur Render](https://render.com/docs/docker).

## Recette et retour arrière

Vérifier accueil, recherche par nom/ville/type/clientèle, pagination, carte sans GPS, refus GPS, professionnels sans coordonnées et consentement de publication. Vérifier un vrai trajet avec ORS, un fournisseur indisponible et les liens Google Maps. Vérifier inscription/activation, lien expiré et renvoi, réservation/confirmation/refus/annulation/nouvelle réservation, espace client et accès admin. Vérifier les images avec Anaelle et Emmanuelle ; leur stockage ne fait pas partie de ce lot.

Pour redéployer : choisir le commit validé dans Render et relancer le déploiement. Pour revenir à une version précédente : utiliser le déploiement correspondant, après vérification de sa compatibilité avec le schéma actuel. Une migration n’est pas annulée par un rollback de l’image. La version antérieure au changement de réservation suppose une unicité historique différente : ne pas la redéployer sur la nouvelle base sans plan de retour validé. Si une restauration est nécessaire, restaurer la sauvegarde dans une nouvelle base, contrôler les données puis basculer le service ; tenir compte des écritures intervenues depuis la sauvegarde.

## Démonstration multi-villes (développement uniquement)

Pour remplir la base locale avec un jeu fictif, lancer une fois :

```bash
DEMO_FRANCE_ENABLED=true bash gradlew bootRun
```

Puis utiliser le démarrage habituel. La désactivation du drapeau ne supprime pas les données déjà créées.
`DEMO_FRANCE_CITY_LIMIT` permet de limiter le jeu à 1–30 villes (30 par défaut).
Aucun appel réseau n'est effectué au démarrage : `demo/france-cities.json` contient les noms et centres de 30 communes récupérés via l'API officielle `geo.api.gouv.fr`, dont Saint-Denis (La Réunion) et Fort-de-France.

Le jeu comprend 90 professionnels fictifs sans préfixe dans leur nom, répartis entre coiffure, onglerie et soins ; 180 prestations actives et 90 retirées ; huit créneaux futurs par professionnel, plus des créneaux historiques pour les scénarios. Un profil est en attente de validation et un autre compte est désactivé. Les clientèles ciblées et les tarifs d'exemple varient.

Trois clients possèdent chacun un rendez-vous confirmé, un terminé, un annulé, un favori et un avis fictif lié au rendez-vous terminé. Les premiers scénarios concernent Paris, Lyon et Marseille. Les points publics représentent les centres des communes, jamais de vraies adresses de professionnels ; d'autres profils ont un point privé ou aucune coordonnée pour tester les replis. Aucun email n'est envoyé par cet initialiseur.

Comptes de test : `client1@demo.beautyconnect.invalid`, `client2@demo.beautyconnect.invalid`, `client3@demo.beautyconnect.invalid`. Pour un professionnel : `coiffure.75056@demo.beautyconnect.invalid` (Paris), ou `onglerie.69123@demo.beautyconnect.invalid` (Lyon). Mot de passe commun : `DemoFrance123!`.

`FranceDemoData` et `FranceDemoInitializer` sont exclus dès que le profil `prod` est actif. Utiliser une base de développement distincte de la production. Relancer l'initialisation ne recrée pas les comptes existants et ne remplace pas les modifications faites pendant la démonstration. Les créneaux sont datés lors de la première création du compte ; ils ne sont pas renouvelés automatiquement.

Ce jeu ne fournit pas de photos de réalisations : le véritable upload et le portfolio restent les lots Anaelle/Emmanuelle.

## Points ouverts au 20 octobre 2026

Le README et les anciennes feuilles de route décrivent parfois un état antérieur : Flyway, la carte, les favoris, l'agenda et le changement des informations personnelles sont déjà présents. Les réservations sont maintenant confirmées directement. Le portfolio ne doit pas être remplacé par des images de réalisations statiques.

| Sujet | Responsable prévu | État constaté dans cette branche |
|---|---|---|
| Recette des médias réels | Anaelle + Emmanuelle | Formulaires, galerie et adaptateur livrés ; renseigner Cloudinary et exécuter M1–M7 de la recette. |
| Chevauchements selon les durées | Manuel M3 | Les départs identiques sont refusés ; la compatibilité des intervalles selon la durée reste à traiter. |
| Validation serveur renforcée du profil | Anaelle A4 | Compléter les contrôles sur nom, ville et bio dans le formulaire de modification. |
| Rappels de rendez-vous | Évolution mail à coordonner | Confirmation et validation pro présentes ; rappels planifiés absents. |
| Mise en ligne et recette réelle | Ulrich + Emmanuelle | Docker et CI présents ; confirmer avec l'équipe Render, SMTP réel, trajet ORS réel et restauration des sauvegardes. |

Les images de Joyce sont utilisées pour la présentation de l'accueil et de la connexion. Elles ne représentent pas les réalisations des professionnels.
