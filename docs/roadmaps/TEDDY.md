# Teddy — administration, avis vérifiés et signalements

> Parcours recommandé : lire [l’audit](../AUDIT-TECHNIQUE.md), puis [le guide commun Git/design/Render](../ROADMAP-EQUIPE.md), puis suivre les étapes ci-dessous. Les fonctions nouvelles sont proposées ; vérifier le code avant de les implémenter.


**Objectif :** rendre la confiance réelle : seules les prestations effectuées donnent lieu à avis ; l'admin peut prendre une décision traçable sur les profils et signalements. **Branche :** `feature/teddy-admin-moderation`. **Charge :** 7 à 8 jours, difficulté 4/5. Lire [`AUDIT-TECHNIQUE.md`](../AUDIT-TECHNIQUE.md), puis `AdminController`, `AdminService`, `ReviewService`, `ReportService`.

## Comprendre le code

`ReviewController.addReview` → `ReviewService.addReview` → `ReviewRepository.save`. Aujourd'hui, `ReviewForm.appointmentId` existe mais `ReviewService` ne l'utilise pas et n'exige aucun RDV terminé. `ReportController.reportReview/reportProfile` → `ReportService.create` stocke `targetType/targetId` sans FK ni contrôle de cible. `AdminController.treatReport` change seulement le statut ; `AdminService.validateProfessional` rend le profil visible en recherche. Les quatre métriques de `AdminService.getMetrics` sont des comptes/profils, pas des réservations.

## Tâches par petites PR

| Étape | Durée | Action et preuve de fin |
|---|---:|---|
| T1 | 1,5 j | Avis vérifié : demander un `appointmentId`, charger le RDV terminé du client pour le même pro, empêcher le second avis sur le même RDV, enregistrer `Review.appointment`. S'accorder avec Ulrich sur l'état `TERMINE`. |
| T2 | 1 j | Tests d'avis : client sans RDV, RDV en attente/refusé, autre client, mauvais pro, doublon ; note 1–5, commentaire borné. |
| T3 | 1 j | Valider `Report.targetId` selon `targetType` avant insertion ; empêcher le spam exact répété selon règle convenue ; garder motif, auteur et date. |
| T4 | 1,5 j | Modération : sur signalement, montrer la cible et le motif dans `admin/reports.html`, permettre décision explicite (traiter/masquer avis/désactiver profil/rejeter), journaliser qui et quand si modèle validé avec Emmanuelle. Ne pas masquer automatiquement sans action décidée. |
| T5 | 1 j | Améliorer `admin/dashboard.html`, `professionals.html`, `reviews.html`, `reports.html` selon Joyce : états vides, badges, confirmation des actions, pagination si volume ; revoir les accès aux relations `LAZY`. |
| T6 | 1 j | Remplacer les quatre chargements entiers de `AdminService.getMetrics` par `count...` là où utile ; tests rôle ADMIN vs CLIENT/PRO et contrôle de propriétaire. |

**Fichiers en écriture :** `controller/AdminController.java`, `ReviewController.java`, `ReportController.java`; `service/AdminService.java`, `ReviewService.java`, `ReportService.java`; `repository/ReviewRepository.java`, `ReportRepository.java`; `dto/ReviewForm.java`, `ReportForm.java`; `templates/admin/*`; tests. **Partagés à lire/annoncer :** `AppointmentRepository.java` (Ulrich), `ProfessionalProfile.java` (Anaelle/Emmanuelle), `SecurityConfig.java` (Ulrich/Emmanuelle), `custom.css` (Joyce).

## Ce que tu dois apprendre

Rôles Spring Security et différence entre accès UI et serveur ; `@Transactional` pour décision de modération ; relations facultatives `Review.appointment` ; contrainte unique applicative + SQL ; requêtes `count` ; tests d'autorisation MVC. Expliquer pourquoi un ID de cible stocké comme `Long` dans `Report` ne garantit pas que la cible existe.

## Critères de terminé

- Un avis public a une preuve de RDV terminé et un seul avis par RDV ; tests de fraude passent.
- Un signalement pointe vers une vraie cible et la décision admin est compréhensible ; les pages admin fonctionnent avec OSIV désactivé.
- Les rôles non admin ne peuvent pas appeler les routes admin ; PR avec démonstration des cas de modération.

## Prompt à donner à ton IA

> Tu aides Teddy sur BeautyConnect. Lis ce fichier, l'audit, `AdminController`, `ReviewService`, `ReportService`, `Review`, `Report` et les templates admin. Explique-moi le parcours d'un avis jusqu'à sa modération et demande-moi quelle preuve rend un avis « vérifié ». Pour T[NUMÉRO], liste les règles, les fichiers/méthodes réels, les cas d'abus et un test qui échoue avant la correction. Modifie seulement ma zone ; coordonne `AppointmentRepository` avec Ulrich et la migration avec Emmanuelle. Termine par ce que j'ai appris, une démo des rôles et les étapes Git/PR.

## Publier ton travail sur GitHub

Après publication de la documentation sur `main`, créer ta branche depuis `main`. Vérifier `git status` avant de changer de branche : la modification locale actuelle de `application.properties` appartient à Ulrich et ne doit pas être incluse par accident.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/teddy-admin-moderation
# coder une tâche, puis vérifier et sélectionner seulement ses modifications (`git add -p` pour les fichiers suivis ; `git add chemin/du/nouveau/fichier` pour un nouveau fichier)
git status --short
git add -p
git commit -m "feat(teddy): decrire la tache"
git push -u origin feature/teddy-admin-moderation
```

Ouvrir une Pull Request vers `main`, avec tâche numérotée, règles métier, captures si UI, tests exécutés, ce que tu as appris et fichiers partagés touchés. Demander une revue à un autre membre ; fusionner seulement après CI verte. Si `main` avance, `git fetch origin` puis `git merge origin/main` dans ta branche et refaire les tests. Pour le cycle complet et Render, voir [le guide commun](../ROADMAP-EQUIPE.md).
