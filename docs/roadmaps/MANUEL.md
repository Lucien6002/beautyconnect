# Manuel — agenda professionnel et décisions sur les rendez-vous

> Parcours recommandé : lire [l’audit](../AUDIT-TECHNIQUE.md), puis [le guide commun Git/design/Render](../ROADMAP-EQUIPE.md), puis suivre les étapes ci-dessous. Les fonctions nouvelles sont proposées ; vérifier le code avant de les implémenter.


**Objectif :** un professionnel voit ses disponibilités et répond correctement aux demandes client. **Branche :** `feature/manuel-agenda-pro`. **Charge :** 7 à 8 jours, difficulté 4/5. Lire [`AUDIT-TECHNIQUE.md`](../AUDIT-TECHNIQUE.md), surtout `TimeSlot`, `Appointment`, `ProfessionalDashboardController` et `AppointmentService`.

## Comprendre le code

`ProfessionalDashboardController.timeSlots/addTimeSlot/removeTimeSlot` appelle `ProfessionalService.getAllTimeSlots/addTimeSlot/removeTimeSlot` et `TimeSlotRepository`. `ProfessionalDashboardController.appointments/confirmAppointment/refuseAppointment/completeAppointment` appelle `AppointmentService`. Les vues sont `templates/professional/slots.html` et `appointments.html`. `TimeSlot.startDateTime` n'a pas de fin ; la durée est dans `Prestation.durationMinutes`. `ProfessionalService.removeTimeSlot` supprime sans vérifier `available`, ce qui peut casser la FK de `Appointment.timeSlot`.

## Tâches par petites PR

| Étape | Durée | Action et preuve de fin |
|---|---:|---|
| M1 | 1 j | Première PR isolée : extraire les routes `/pro/creneaux` et `/pro/rendez-vous` dans un nouveau `ProfessionalSchedulingController`, sans modifier URL, méthodes HTTP ni autorisations. Prévenir Anaelle dès la fusion. |
| M2 | 1 j | Interdire dans `ProfessionalService.removeTimeSlot` la suppression de tout créneau référencé par un RDV, même refusé/annulé, tant que la règle de conservation n'est pas décidée ; retourner une erreur métier lisible. |
| M3 | 1,5 j | Ajouter contrôle de doublons et chevauchements : définir avec Ulrich la durée maximale à réserver et la relation prestation/créneau, puis appliquer une règle testable pour les créneaux proches. Ne pas supposer qu'un départ à 10 h et un autre à 10 h 15 sont compatibles. |
| M4 | 1 j | Améliorer la page `slots.html` : ajout, vue chronologique, disponibilité, action interdite clairement expliquée, états vides, mobile selon Joyce. |
| M5 | 1 j | Améliorer `appointments.html` : demandes en attente prioritaires, actions seulement selon les transitions convenues avec Ulrich, date/prestation/client lisibles, messages de décision. |
| M6 | 1,5 j | Tests de création/suppression/chevauchement, accès propriétaire et réponse pro ; test MVC des vues avec `spring.jpa.open-in-view=false` pour repérer les relations `LAZY`. |

**Fichiers en écriture :** nouveau `controller/ProfessionalSchedulingController.java`, `repository/TimeSlotRepository.java`, `dto/TimeSlotForm.java`, `templates/professional/slots.html`, `appointments.html`, tests. `ProfessionalDashboardController.java` uniquement lors de M1, puis Anaelle le possède pour profil/catalogue. `ProfessionalService.java` est partagé avec Anaelle : convenir d'une PR courte pour M2/M3 ou extraire `SchedulingService`. **Lecture seule :** `AppointmentService.java` (Ulrich), `custom.css` (Joyce), modèle/migration (Emmanuelle).

## Ce que tu dois apprendre

`@RequestMapping` au niveau classe, injection par constructeur, `@Future` dans `TimeSlotForm`, FK et suppression JPA, transaction, état de RDV et autorisation par propriétaire. Explique pourquoi l'affichage `th:if` dans `appointments.html` ne remplace pas une validation dans `AppointmentService`.

## Critères de terminé

- Routes pro inchangées après extraction ; aucun conflit avec le travail d'Anaelle.
- Un créneau réservé ou référencé ne peut pas être supprimé de façon dangereuse ; les doublons/chevauchements définis sont refusés.
- Page d'agenda mobile et compréhensible ; décisions pro seulement dans les états autorisés ; tests H2/MVC verts.

## Prompt à donner à ton IA

> Tu aides Manuel sur BeautyConnect. Lis ce fichier, `docs/AUDIT-TECHNIQUE.md`, `ProfessionalDashboardController`, `ProfessionalService`, `AppointmentService`, `TimeSlot`, `Appointment`, leurs repositories et templates. Fais-moi dessiner le flux d'ajout d'un créneau et explique la FK `Appointment.timeSlot`. Pour M[NUMÉRO], indique la règle métier, les méthodes existantes, le changement minimal et les tests qui détectent une régression. Commence par l'extraction du contrôleur en PR indépendante. Ne modifie pas les transitions dans `AppointmentService` sans accord avec Ulrich. Termine par ma mini leçon, les cas limites vérifiés et les commandes Git/PR.

## Publier ton travail sur GitHub

Après publication de la documentation sur `main`, créer ta branche depuis `main`. Vérifier `git status` avant de changer de branche : la modification locale actuelle de `application.properties` appartient à Ulrich et ne doit pas être incluse par accident.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/manuel-agenda-pro
# coder une tâche, puis vérifier et sélectionner seulement ses modifications (`git add -p` pour les fichiers suivis ; `git add chemin/du/nouveau/fichier` pour un nouveau fichier)
git status --short
git add -p
git commit -m "feat(manuel): decrire la tache"
git push -u origin feature/manuel-agenda-pro
```

Ouvrir une Pull Request vers `main`, avec tâche numérotée, règles métier, captures si UI, tests exécutés, ce que tu as appris et fichiers partagés touchés. Demander une revue à un autre membre ; fusionner seulement après CI verte. Si `main` avance, `git fetch origin` puis `git merge origin/main` dans ta branche et refaire les tests. Pour le cycle complet et Render, voir [le guide commun](../ROADMAP-EQUIPE.md).
