# BeautyConnect — travail restant

État revu le **20 octobre 2026** sur `feature/design-espaces-comptes`, après intégration du `main` contenant Ana et les décisions de modération de Teddy. Ce document contient uniquement les points encore ouverts. Les anciens audits décrivent leur date de rédaction ; ils ne constituent plus une liste de tâches actuelle.

## Avant une présentation complète

| Priorité | Reste à faire | Responsable proposé | Preuve attendue |
|---|---|---|---|
| Haute | Exécuter la [recette manuelle par rôle](RECETTE-MANUELLE.md) et corriger les anomalies relevées. | Tous ; coordination Emmanuelle | Fiche remplie, navigateur/commit et captures des défauts. |
| Haute | Valider la règle des avis : avis libres autorisés ou uniquement après rendez-vous terminé. Les deux existent actuellement. | Teddy + équipe | Règle décidée, interface explicite et tests alignés. |
| Moyenne | Valider les formulaires de profil côté serveur : longueurs, valeurs vides et cohérence des champs ; décider si un changement sensible impose une revalidation. | Anaelle + Teddy | Saisies invalides refusées et politique documentée. |
| Moyenne | Décider et implémenter le contrôle des chevauchements de réservations tenant compte des durées ; le doublon exact est déjà bloqué. | Manuel + Ulrich | Deux heures différentes qui se chevauchent ne créent pas de surbooking. |

## Avant une mise en ligne réelle

| Reste à faire | Responsable proposé | Preuve attendue |
|---|---|---|
| Choisir l'environnement d'hébergement, configurer les secrets, HTTPS, SMTP réel et déployer. | Ulrich | URL publique et parcours activation/réservation vérifiés hors Mailpit. |
| Vérifier Matrix/Directions avec une vraie clé ORS et des quotas adaptés. | Ulrich | Mesures réelles et replis testés ; classement limité aux candidats annoncé. |
| Tester sauvegarde et restauration PostgreSQL ; définir la conservation des médias et le nettoyage des suppressions distantes échouées. | Emmanuelle | Restauration dans une base séparée et procédure reproductible. |
| Fixer la règle de conservation/suppression des photos personnelles à la suppression d'un compte. | Emmanuelle + Teddy | Nettoyage des médias personnels et anonymisation cohérents. |
| Revoir les pages de mentions légales/confidentialité, destinataires des données et coordonnées de contact selon le contexte de publication retenu. | Équipe | Textes correspondant au fonctionnement réel. |
| Vérifier droits d'utilisation des images et préparer un catalogue de présentation cohérent. | Joyce + Anaelle | Images autorisées ; données simulées annoncées dans la présentation. |

## Améliorations facultatives

- Décider si le passage à « Terminé » doit attendre la fin effective du rendez-vous ; actuellement le statut et le propriétaire sont contrôlés, pas la date.
- Ajouter des rappels de rendez-vous si l’équipe les retient ; les confirmations existent déjà.
- Réordonner librement toutes les photos dans une galerie ; le choix d’une photo principale est déjà disponible.
- Faire naviguer l'agenda entre plusieurs semaines : actuellement semaine courante.
- Évaluer la pagination des grandes listes admin et les performances sur un volume réel.
- Figer tarif et durée dans le rendez-vous si l'équipe veut préserver les conditions de réservation après modification du catalogue. Actuellement ils proviennent de la prestation référencée.

## Répartition et suivi

[Joyce](roadmaps/JOYCE.md) · [Ulrich](roadmaps/ULRICH.md) · [Anaelle](roadmaps/ANAELLE.md) · [Manuel](roadmaps/MANUEL.md) · [Teddy](roadmaps/TEDDY.md) · [Emmanuelle](roadmaps/EMMANUELLE.md).

Travailler dans une branche dédiée, intégrer `origin/main`, exécuter les tests et ouvrir une PR revue par l'équipe. La branche design intègre les contributions ; elle ne remplace pas leur attribution. Les fonctions terminées sont décrites dans le [README](../README.md) ; les corrections liées aux photos sont détaillées dans la [revue d'Ana](REVUE-INTEGRATION-ANA.md).
