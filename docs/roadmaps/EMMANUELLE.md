# Emmanuelle — qualité, images hébergées et recette

> Parcours recommandé : lire [l’audit](../AUDIT-TECHNIQUE.md), puis [le guide commun Git/design/Render](../ROADMAP-EQUIPE.md), puis suivre les étapes ci-dessous. Les fonctions nouvelles sont proposées ; vérifier le code avant de les implémenter.


**Objectif :** rendre le projet testable par tous, protéger ses données, gérer les images et conduire une recette indépendante avant le déploiement d'Ulrich. **Branche :** `feature/emmanuelle-qualite-media`. **Charge :** 9 à 11 jours, difficulté 4/5. Cette attribution est **proposée** car la note initiale laissait ton rôle vide. Lire [`AUDIT-TECHNIQUE.md`](../AUDIT-TECHNIQUE.md), `build.gradle`, les deux `application*.properties`, `docker-compose.yml`, `DataInitializer.java` et `BeautyConnectApplicationTests.java`.

## Comprendre le code et les blocages

Le test actuel `contextLoads` est seul ; H2 est configuré, mais aucune CI n'existe. La config locale `application.properties` est déjà modifiée et contient des identifiants SMTP en clair ; **ne pas les recopier dans la documentation, l'IA ou une PR**. `DataInitializer.run` crée un admin et deux comptes de démo avec mots de passe connus. Le README annonce un SMTP local qui ne correspond pas à la modification locale. `AuthTokenService` code `localhost:8080`. `spring.jpa.hibernate.ddl-auto=update` n'est pas une stratégie de migration. Aucun `Dockerfile`, `render.yaml`, stockage d'image ou profil prod n'est présent.

## Tâches par petites PR

| Étape | Durée | Action et preuve de fin |
|---|---:|---|
| E1 | 1 j | Ajouter `.github/workflows/ci.yml` : Java 21, wrapper Gradle, `./gradlew test`, rapport d'échec. Faire tourner H2 sans PostgreSQL ni SMTP réseau ; demander à chaque membre d'ajouter ses tests. |
| E2 | 1 j | Séparer configuration dev/test/prod : `application-prod.properties`, variables DB/SMTP/`APP_BASE_URL`, `server.port=${PORT:8080}`. Gérer la modification locale de config avec Ulrich sans l'écraser ; aucune valeur sensible dans Git. |
| E3 | 1 j | Limiter `DataInitializer` au profil dev, retirer comptes/mots de passe de démo de prod et obliger un secret admin initial non trivial ou une procédure d'amorçage contrôlée. Tester qu'aucun compte de démo n'apparaît en prod. |
| E4 | 1,5 j | Ajouter Flyway ou Liquibase après inventaire du schéma réel ; migration initiale et évolutions index/coordonnées/images, puis `ddl-auto=validate` en prod. Tester création de base vide et montée depuis une base de démo copiée. Ne pas supposer qu'une base existante est vide. |
| E5 | 1 j | Définir `ImageStorageService` (`upload`, `delete`, retour `secureUrl/publicId`) et une fausse implémentation pour tests. Choisir avec l'équipe Cloudinary Free, vérifier quotas et créer le compte. Variables `CLOUDINARY_*` côté serveur ; aucun secret en HTML/JS. |
| E6 | 1,5 j | Implémenter l'adaptateur Cloudinary côté serveur : upload image validée, URL HTTPS, suppression distante par `publicId`, gestion de panne et nettoyage ; contrats avec Anaelle pour portfolio/profil. Tests sans appel Cloudinary réel. Activer le backup Cloudinary avant les premières images si la restauration est requise, puis tester la restauration d'une image de démo. |
| E7 | 1,5 j | Construire une suite d'intégration qui exécute les parcours client, pro et admin sur H2 avec mail et stockage image simulés : activation, CRUD, réservation, avis, modération. Publier une matrice « parcours / test / résultat » dans la PR. |
| E8 | 1,5 j | Tester les échecs et la récupération : image trop grande ou non-image, fournisseur indisponible, upload réussi mais sauvegarde SQL échouée, restauration d'une image de démo depuis backup Cloudinary, procédure de sauvegarde/restauration PostgreSQL documentée. |
| E9 | 1 j | Recette indépendante des PR fusionnées : exécuter le parcours de six rôles/actions sur un environnement de test, relever erreurs de chargement `LAZY`, requêtes N+1 évidentes et liens cassés ; transmettre les défauts aux propriétaires avant que Ulrich lance Render. |

**Fichiers en écriture :** `.github/workflows/*`, `build.gradle` pour dépendances validées, `src/main/resources/application-prod.properties`, `src/main/resources/db/migration/*`, nouveau package `service/storage/*`, tests techniques et document de recette. **Partagés à modifier avec accord :** `application.properties` (modification locale d'Ulrich), `DataInitializer.java` (Ulrich), `ProfessionalProfile.java`/entités images (Anaelle), `SecurityConfig.java` (Ulrich). **Lecture seule :** `Dockerfile`/`render.yaml` et logique métier d'Ulrich, templates des autres.

## Ce que tu dois apprendre

Gradle wrapper et profils Spring ; différence H2/PostgreSQL ; migrations et version de schéma ; secrets d'environnement ; stockage objet vs fichier local ; contrat d'interface et faux service en tests ; tests d'intégration, sauvegarde/restauration et lecture des requêtes SQL. Expliquer pourquoi une clé Cloudinary ne va jamais dans `static/js/main.js` et pourquoi `ddl-auto=update` est fragile lors d'un vrai déploiement.

## Critères de terminé

- CI verte sans base PostgreSQL ni SMTP réels ; tests métier ajoutés par chaque propriétaire.
- Production sans comptes démo, secret ou mot de passe par défaut ; la base démarre via migration et validation de schéma.
- Une image pro est envoyée, relue par HTTPS et supprimée avec propriétaire contrôlé ; test hors réseau.
- Matrice de recette complète, erreurs de fournisseur couvertes, images et base restaurables à partir d'une sauvegarde de démonstration ; rapport de défauts transmis à Ulrich avant Render.

## Prompt à donner à ton IA

> Tu aides Emmanuelle sur BeautyConnect. Lis ce fichier, l'audit, `build.gradle`, `application.properties`, `application-test.properties`, `DataInitializer` et le test actuel. Résume l'ordre de démarrage Spring, la différence entre profil dev/test/prod et les dépendances externes. Pour E[NUMÉRO], explique le concept avant le code, vérifie la documentation officielle de Cloudinary pour les images, propose la modification la plus petite et une preuve de fonctionnement. Prépare des tests d'intégration qui détectent les pannes avant la recette d'Ulrich, et enseigne-moi comment restaurer une image et une base de démonstration. N'affiche, ne répète et ne commit aucun secret ; la configuration locale est déjà modifiée par quelqu'un d'autre. Termine par un mini cours, les tests et la procédure Git/PR.

## Publier ton travail sur GitHub

Après publication de la documentation sur `main`, créer ta branche depuis `main`. Vérifier `git status` avant de changer de branche : la modification locale actuelle de `application.properties` appartient à Ulrich et ne doit pas être incluse par accident.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/emmanuelle-qualite-media
# coder une tâche, puis vérifier et sélectionner seulement ses modifications (`git add -p` pour les fichiers suivis ; `git add chemin/du/nouveau/fichier` pour un nouveau fichier)
git status --short
git add -p
git commit -m "feat(emmanuelle): decrire la tache"
git push -u origin feature/emmanuelle-qualite-media
```

Ouvrir une Pull Request vers `main`, avec tâche numérotée, règles métier, captures si UI, tests exécutés, ce que tu as appris et fichiers partagés touchés. Demander une revue à un autre membre ; fusionner seulement après CI verte. Si `main` avance, `git fetch origin` puis `git merge origin/main` dans ta branche et refaire les tests. Pour le cycle complet et Render, voir [le guide commun](../ROADMAP-EQUIPE.md).
