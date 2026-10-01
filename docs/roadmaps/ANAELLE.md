# Anaelle — profil professionnel, prestations et données de localisation

> Parcours recommandé : lire [l’audit](../AUDIT-TECHNIQUE.md), puis [le guide commun Git/design/Render](../ROADMAP-EQUIPE.md), puis suivre les étapes ci-dessous. Les fonctions nouvelles sont proposées ; vérifier le code avant de les implémenter.


**Objectif :** permettre au professionnel de créer une vitrine complète et de gérer réellement son catalogue. **Branche :** `feature/anaelle-catalogue-profil`. **Charge :** 8 à 9 jours, difficulté 4/5. La carte et la recherche visible sont confiées à **Ulrich** ; tu fournis les données de localisation et la page publique. Lire [`AUDIT-TECHNIQUE.md`](../AUDIT-TECHNIQUE.md) et [le guide commun](../ROADMAP-EQUIPE.md).

## Comprendre le code

Suivre `AuthController.registerProfessional` → `UserService.registerProfessional` → `ProfessionalProfileRepository` → `ProfessionalDashboardController.editProfileForm` → `ProfessionalService.updateProfile` → `templates/professional/profile-edit.html`. Pour le catalogue : `ProfessionalDashboardController.prestations/addPrestation/removePrestation` → `ProfessionalService` → `PrestationRepository` → `templates/professional/prestations.html`. Pour la fiche : `ProfessionalPublicController.viewProfile` → `ProfessionalService.getActivePrestations` → `templates/professional/profile.html`. Le code actuel n'a que **ajouter, lire, désactiver** ; édition et réactivation manquent. `Prestation.active=false` protège l'historique des RDV.

## Tâches dans l'ordre

| Étape | Durée | Action et preuve de fin |
|---|---:|---|
| A1 | 1 j | Après la petite PR de Manuel, séparer si utile les routes profil/catalogue dans `ProfessionalCatalogueController` pour réduire `ProfessionalDashboardController`. Conserver les URL existantes. |
| A2 | 1,5 j | CRUD complet de `Prestation` : créer, lister actives/inactives, ouvrir formulaire d'édition, modifier nom/type/prix/durée/description, désactiver et réactiver. Routes proposées `GET/POST /pro/prestations/{id}/modifier` et `POST /pro/prestations/{id}/reactiver`. Vérifier propriétaire dans le service sur chaque mutation. |
| A3 | 1 j | Valider le catalogue : longueurs texte, prix et durée, doublons éventuels selon décision métier ; empêcher la réservation d'une prestation inactive avec Ulrich. Ne jamais supprimer physiquement une prestation citée dans un RDV. |
| A4 | 1 j | Profil éditable avec validations serveur, nom/ville non vides, bio bornée, clientèle cible ; distinction profil privé non validé/public validé ; demande de revalidation si champs sensibles changent, selon règle décidée avec Teddy. |
| A5 | 1–1,5 j | Fournir les coordonnées à la carte d'Ulrich : consentement de publication, latitude/longitude nullable et précision (ville/quartier vs adresse exacte), géocodage à la sauvegarde seulement ou saisie contrôlée pour la démo. Emmanuelle crée la migration ; ne pas appeler Nominatim à chaque GET. |
| A6 | 1,5 j | Portfolio : avec l'interface `ImageStorageService` fournie par Emmanuelle, permettre au pro d'ajouter, ordonner, remplacer, supprimer des réalisations ; lier si pertinent à une prestation ; afficher image réelle ou état sans image sur `professional/profile.html`. Auth et propriétaire obligatoires. |
| A7 | 1 j | Tests de propriétaire, validation, CRUD, visibilité public, portfolio simulé sans Cloudinary ; aligner les templates sur les tokens de Joyce. |

**Fichiers en écriture :** `ProfessionalService.java`, `ProfessionalPublicController.java`, nouveau contrôleur catalogue si besoin, `PrestationRepository.java`, `PrestationForm.java`, `templates/professional/profile.html`, `profile-edit.html`, `prestations.html`, entité/DTO portfolio convenus, tests. **Partagés à modifier après accord :** `ProfessionalProfile.java` et migration (Emmanuelle), `ProfessionalDashboardController.java` (Manuel), `ProfessionalProfileRepository.java` (Ulrich), `build.gradle` (Emmanuelle). **Lecture :** `AppointmentService.java` (Ulrich), `custom.css` (Joyce), `ImageStorageService` (Emmanuelle).

## Ce que tu dois apprendre

Relations JPA et clé étrangère ; suppression logique vs physique ; `@Valid` et DTO ; autorisation par propriétaire dans le service ; pagination/chargement pour une fiche ; chargement d'image `multipart/form-data`, métadonnées en base et URL HTTPS ; protection de l'adresse privée. Expliquer pourquoi `profilePhotoUrl` seul ne permet pas de supprimer proprement une image hébergée.

## Critères de terminé

- Un pro peut créer, lire, modifier, désactiver et réactiver une prestation sans casser l'historique ; un autre pro ne peut rien changer.
- Le public ne voit que les profils validés et les prestations actives ; fiches sans image/coordonnée restent propres.
- Les coordonnées ne sont publiées qu'avec le niveau de précision autorisé ; Ulrich peut construire ses marqueurs depuis une donnée testée.
- Images remplaçables/supprimables par leur propriétaire, taille/type contrôlés, tests sans réseau ; aucune clé Cloudinary dans Git.

## Prompt à donner à ton IA

> Tu aides Anaelle sur BeautyConnect. Lis ce fichier, l'audit et les vrais fichiers `ProfessionalService`, `ProfessionalPublicController`, `Prestation`, `ProfessionalProfile`, `PrestationRepository`, `templates/professional/*`. Dessine pour moi le flux formulaire → contrôleur → service → repository et fais-moi expliquer `@ManyToOne`, `@Transactional` et la suppression logique. Pour la tâche A[NUMÉRO], donne les routes existantes et celles à ajouter, les risques de propriétaire/FK, le code à changer seulement dans mes fichiers, puis un test qui échouerait avant le correctif. Respecte les contrats d'Ulrich (carte), Emmanuelle (images/migration) et Joyce (tokens). Termine par ce que j'ai appris, une démo manuelle et les étapes Git/PR.

## Publier ton travail sur GitHub

Après publication de la documentation sur `main`, créer ta branche depuis `main`. Vérifier `git status` avant de changer de branche : la modification locale actuelle de `application.properties` appartient à Ulrich et ne doit pas être incluse par accident.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/anaelle-catalogue-profil
# coder une tâche, puis vérifier et sélectionner seulement ses modifications (`git add -p` pour les fichiers suivis ; `git add chemin/du/nouveau/fichier` pour un nouveau fichier)
git status --short
git add -p
git commit -m "feat(anaelle): decrire la tache"
git push -u origin feature/anaelle-catalogue-profil
```

Ouvrir une Pull Request vers `main`, avec tâche numérotée, règles métier, captures si UI, tests exécutés, ce que tu as appris et fichiers partagés touchés. Demander une revue à un autre membre ; fusionner seulement après CI verte. Si `main` avance, `git fetch origin` puis `git merge origin/main` dans ta branche et refaire les tests. Pour le cycle complet et Render, voir [le guide commun](../ROADMAP-EQUIPE.md).
