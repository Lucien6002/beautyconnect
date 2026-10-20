# Recette manuelle BeautyConnect

À exécuter soi-même sur une **base de développement**. Une case vide signifie « non testé ». Cocher seulement après avoir comparé le résultat à l'attendu. Copier ce fichier pour chaque campagne et garder les anomalies avec une capture et l'URL.

**Date :** … · **Branche/commit :** … · **Testeur :** … · **Navigateur :** … · **Écran :** …

## Préparer la session

- [ ] Suivre le lancement du [README](../README.md). L'accueil répond ; Mailpit s'ouvre sur `http://localhost:8025`.
- [ ] Sur la base locale préparée, laisser le jeu France désactivé. Sur un nouveau poste, initialiser le catalogue selon le README, même si la base contient déjà des comptes. Une base vide reçoit six établissements dans six villes ; une base existante conserve aussi ses autres profils.
- [ ] Préparer une photo JPEG, une PNG, une WebP, un fichier texte renommé `.jpg`, un fichier dépassant 5 Mo. Utiliser des images destinées à être publiques.
- [ ] Utiliser trois profils de navigateur séparés pour client, professionnel et admin, ou se déconnecter entre les rôles. Deux onglets d'une même session partagent le même compte.
- [ ] Créer un compte client jetable pour le changement de mot de passe et la suppression du compte ; conserver les comptes communs pour les autres membres.

Comptes de démarrage :

| Usage | Identifiant | Mot de passe |
|---|---|---|
| Client du rendez-vous exemple chez Sarah | `client1@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Autre client | `client2@demo.beautyconnect.invalid` | `DemoFrance123!` |
| Pro Paris | `sarah@beautyconnect.local` | `Pro12345!` |
| Autre pro Lyon | `chloe@beautyconnect.local` | `Pro12345!` |
| Admin local | `admin@beautyconnect.local` | `Admin123!` |

Ils nécessitent le profil `dev` ; ceux du domaine `.invalid` nécessitent le jeu France. Si les comptes ont été modifiés, utiliser leurs identifiants actuels. Ne jamais réutiliser ces secrets en production.

## Import du catalogue sur un autre poste

À faire sur une base de développement destinée à la recette. Conserver une sauvegarde si elle contient du travail à préserver.

- [ ] Récupérer le code, garder son `.env` local, activer `DEMO_FRANCE_ENABLED=true`, démarrer Spring. L'import se termine sans erreur Flyway.
- [ ] Sans clés Cloudinary et avec le stockage local, ouvrir les six vitrines : couvertures et photos de prestations s'affichent avec Internet. Aucun nouveau rendez-vous ni avis n'a été ajouté.
- [ ] Sur une base déjà remplie, vérifier qu'un mot de passe personnalisé, une position privée, un prix modifié et une photo personnelle sont conservés. Les anciens établissements supplémentaires doivent rester présents.
- [ ] Renommer une prestation de présentation, modifier son prix, supprimer une de ses photos et sa couverture, retirer une prestation. Redémarrer avec l'import encore actif : aucune modification n'est annulée ni photo rétablie.
- [ ] Remplacer une photo partagée sur ce poste, puis consulter la photo d'origine sur un autre poste : elle reste visible. L'import ne synchronise pas les modifications des utilisateurs entre leurs bases.
- [ ] Remettre `DEMO_FRANCE_ENABLED=false` et conserver la table `showcase_imports`. Pour uploader de nouvelles photos vers Cloudinary, configurer ses clés et le fournisseur selon le README.

## Parcours conseillé pour la recette de ce soir

Point de départ de la base locale : **six établissements**, **un seul rendez-vous confirmé chez Sarah**, aucun ancien avis lié aux rendez-vous retirés. Ce rendez-vous est un exemple ; créer des rendez-vous supplémentaires pour les scénarios ci-dessous. Ne pas supposer qu'un compte nouvellement initialisé possède cet exemple.

1. Ouvrir trois navigateurs ou profils séparés : client, professionnel, administrateur. Tester l'accueil, les six fiches, les six points de carte et les galeries Cloudinary.
2. Avec Sarah, créer une prestation temporaire « Recette — à retirer » avec principale et secondaires, puis un créneau futur. Avec le client 2, réserver ce créneau. Vérifier le même rendez-vous côté client, côté Sarah et le mail dans Mailpit.
3. Annuler depuis le client. Vérifier le statut côté Sarah et que le créneau redevient réservable. Le réserver à nouveau pour vérifier sa réutilisation.
4. Pour tester un conflit, ouvrir deux comptes clients distincts et essayer le même créneau : une seule réservation doit réussir.
5. Pour le parcours terminé → avis, suivre P15 : créer un rendez-vous de recette, le terminer depuis Sarah (le contrôle de date n’est pas encore imposé), puis déposer l'avis depuis le compte du client associé. Vérifier ensuite sa modération par l'admin.
6. Avec un autre compte professionnel, vérifier qu'il ne peut modifier ni les photos ni les réservations de Sarah. Tester aussi l'inscription d'un pro temporaire, son activation puis sa validation par l'admin.
7. Nettoyer uniquement les objets « Recette — à retirer ». Une prestation déjà utilisée par un rendez-vous doit être retirée du catalogue plutôt que supprimée définitivement. Conserver les preuves et noter les anomalies dans le tableau final.

Les six identifiants professionnels figurent dans le [README](../README.md#catalogue-de-présentation--six-établissements). Pour les cas localisation privée, fiche vide, suspension ou attente de validation, utiliser un compte temporaire : les six comptes de présentation restent publiés pour le parcours principal.

## V — Visiteur

| Fait | Action | Résultat attendu |
|---|---|---|
| [ ] V1 | Ouvrir l'accueil, attendre puis utiliser suivant/précédent/pause sur les images. | Images de Joyce affichées, commandes utilisables ; pause arrête la rotation. |
| [ ] V2 | Cliquer Coiffure, Onglerie puis Soins depuis l'accueil. | Recherche avec la catégorie sélectionnée ; retour possible vers l'accueil. |
| [ ] V3 | Chercher Paris, puis une ville inexistante ; effacer les filtres. | Résultats pertinents, état vide compréhensible, liste restaurée après effacement. |
| [ ] V4 | Combiner ville, nom, type et clientèle ; ouvrir une vitrine puis « Retour aux résultats ». | Filtres conservés. La fiche correspond au professionnel choisi. |
| [ ] V5 | Parcourir les pages de résultats si plusieurs pages existent. | Liens de pagination fonctionnels et filtres conservés. |
| [ ] V6 | Sur la carte, cliquer un marqueur puis « Voir sur la carte » dans la liste. | Le numéro et le professionnel correspondent ; la carte se recentre. |
| [ ] V7 | Chercher un pro à localisation privée, puis un sans coordonnées. | Profil accessible mais aucun point privé ni adresse privée exposés par la carte. |
| [ ] V8 | Ouvrir une vitrine possédant des photos ajoutées par le pro. | Photo d'établissement, galerie réelle ; clic ouvre la photo, sans remplacer la fiche dans le même onglet. |
| [ ] V9 | Sur une fiche sans photo ni créneau, regarder les états vides. | Aucun faux exemple de réalisation ; message lisible et réservation indisponible. |
| [ ] V10 | Ouvrir `/client/tableau-bord`, `/pro/prestations` et `/admin/tableau-bord` sans connexion. | Connexion demandée, aucune donnée privée visible. |

## C — Client

| Fait | Action | Résultat attendu |
|---|---|---|
| [ ] C1 | S'inscrire avec une nouvelle adresse, ouvrir le message Mailpit et le lien d'activation. | Compte activé ; connexion possible. Avant activation, l'accès connecté est refusé. |
| [ ] C2 | Réutiliser le lien ; demander un renvoi depuis la page prévue. | Réponse contrôlée, aucune page 500 ; activation du compte inchangée. |
| [ ] C3 | Tester un mauvais mot de passe puis le bon. | Erreur compréhensible puis retour à l’accueil en étant connecté ; le compte et les rendez-vous restent accessibles par la navigation. |
| [ ] C4 | Utiliser la navigation latérale pour passer entre carnet, favoris, rendez-vous et compte. | Navigation visible, rubrique courante indiquée, identité Client visible. |
| [ ] C5 | Ajouter puis retirer un favori sur une fiche ; consulter les favoris. | État cohérent sur la fiche et dans la liste, aucun doublon. |
| [ ] C6 | Choisir une prestation puis un créneau futur, réserver. | Prestation sélectionnée correcte, confirmation immédiate, rendez-vous dans le carnet et mail Mailpit. |
| [ ] C7 | Avec deux clients dans deux navigateurs, réserver simultanément le même créneau. | Un seul rendez-vous actif accepté ; l'autre reçoit un refus contrôlé. |
| [ ] C8 | Annuler le rendez-vous depuis le client. | Statut annulé, créneau de nouveau disponible ; double annulation sans effet indésirable. |
| [ ] C9 | Préparer P15 avec le professionnel pour obtenir un rendez-vous terminé sans avis, puis déposer une note et un commentaire depuis ce rendez-vous. | Avis rattaché au bon rendez-vous ; second avis sur le même rendez-vous refusé. |
| [ ] C10 | Depuis une fiche publique, utiliser « Laisser un avis » sans choisir de rendez-vous. | L'avis libre est actuellement accepté une seule fois par client et professionnel ; ne pas le présenter comme un avis vérifié. |
| [ ] C11 | Signaler un avis ou profil avec un motif, puis répéter le même signalement. | Premier signalement enregistré ; doublon en attente refusé, aucun masquage automatique. |
| [ ] C12 | Modifier prénom, nom, téléphone et photo personnelle. | Valeurs conservées après rechargement, aperçu avant envoi et photo sauvegardée. |
| [ ] C13 | Sur le compte jetable, changer le mot de passe avec un ancien mot de passe faux puis correct. | Premier essai refusé, second accepté ; reconnexion avec le nouveau secret. |
| [ ] C14 | Sur le compte jetable, demander la suppression avec mauvais puis bon mot de passe. | Refus puis suppression/anonymisation, déconnexion et réservations futures annulées selon la règle du compte. |
| [ ] C15 | Tenter `/pro/prestations` et `/admin/tableau-bord`. | Accès refusé au client. |

## P — Professionnel

| Fait | Action | Résultat attendu |
|---|---|---|
| [ ] P1 | S'inscrire comme pro et activer l'email ; chercher son établissement avant validation admin. | Espace pro accessible, établissement non publié tant que l'admin n'a pas validé. |
| [ ] P2 | Après validation admin, renseigner nom, présentation, ville et clientèle. | Informations sauvegardées et visibles sur la vitrine publique. |
| [ ] P3 | Ajouter une prestation nommée « Recette — à retirer », prix, durée, description, photo principale et plusieurs photos secondaires dans le même formulaire. | Création réussie en une fois ; principale affichée en premier et secondaires présentes dans la galerie publique. |
| [ ] P4 | Modifier cette prestation ; soumettre aussi nom vide, prix négatif et durée inférieure à 5 min. | Valeurs valides sauvegardées ; champs invalides signalés sans perte des autres saisies. |
| [ ] P4a | Dans Modifier, remplacer la principale, supprimer une secondaire et en ajouter une ; enregistrer. | Galerie et fiche publique à jour, cinq photos maximum, anciennes images remplacées supprimées du stockage. |
| [ ] P4b | Modifier le nom et envoyer une nouvelle principale valide avec une secondaire invalide. | Refus expliqué ; ancien nom et anciennes photos conservés, aucun nouveau fichier orphelin. |
| [ ] P5 | Cliquer Photos, ajouter des JPEG/PNG jusqu'à cinq, essayer une sixième. | Couverture changée sur la vitrine, aperçu correct, limite de cinq respectée. |
| [ ] P6 | Envoyer le faux JPEG, puis une image dépassant 5 Mo. | Fichiers refusés ; aucun ajout en galerie ; message indique le format ou la limite. |
| [ ] P7 | Supprimer une photo, ajouter une autre ; recharger la galerie puis la fiche publique. | Bonne photo supprimée, nouvelle photo présente, place disponible récupérée. |
| [ ] P8 | Remplacer puis supprimer la photo principale de l'établissement, dont un essai avec un fichier WebP. | Image mise à jour dans la recherche et sur la vitrine ; état sans photo propre après suppression. |
| [ ] P9 | Retirer la prestation, consulter les prestations supprimées, restaurer. | Disparition du catalogue public puis réapparition ; historique des rendez-vous conservé. |
| [ ] P10 | Retirer puis supprimer définitivement la prestation de recette sans rendez-vous. | Confirmation explicite ; prestation et galerie disparaissent. |
| [ ] P11 | Tenter la suppression définitive d'une prestation déjà utilisée dans un rendez-vous. | Refus avec explication ; historique préservé. |
| [ ] P12 | Avec un autre pro, ouvrir une URL de modification ou galerie du premier pro. | Accès refusé ou ressource introuvable ; aucune mutation possible. |
| [ ] P13 | Ajouter un créneau futur puis le même jour à la même heure. | Premier ajouté, doublon refusé ; créneau visible dans les disponibilités publiques. |
| [ ] P14 | Réserver ce créneau côté client, vérifier agenda et rendez-vous côté pro. | Même date, heure et prestation ; suppression du créneau réservé refusée. |
| [ ] P15 | Sur la base de test, refaire C6 chez le pro connecté sans annuler cette fois, puis cliquer Terminer sur ce rendez-vous confirmé. | Statut terminé ; le serveur vérifie que le rendez-vous appartient au pro et était confirmé. Compteurs et revenu du mois cohérents avec la date du créneau. |
| [ ] P16 | Poser/déplacer un point de localisation sans cocher la publication ; enregistrer. | Coordonnées conservées pour le pro, absence de point public. |
| [ ] P17 | Cocher la publication et enregistrer, puis la retirer. | Point apparaît puis disparaît de la carte publique. |
| [ ] P18 | Tenter `/admin/tableau-bord`. | Accès admin refusé. |

Limites à observer : « Terminer » ne vérifie pas encore que la date du rendez-vous est passée ; la simulation P15 peut donc se faire immédiatement. Les créneaux ont une heure de début ; l'application ne garantit pas encore l'absence de chevauchement entre deux heures différentes selon la durée des prestations. Ne pas considérer ce cas comme déjà résolu.

## A — Administrateur

| Fait | Action | Résultat attendu |
|---|---|---|
| [ ] A1 | Se connecter, ouvrir le tableau de bord et les rubriques du menu. | Identité Administration, métriques et listes lisibles, liens fonctionnels. |
| [ ] A2 | Examiner le nouveau pro de P1 et le valider ; rechercher son établissement en visiteur. | Publication après validation ; pas de publication avant. |
| [ ] A3 | Suspendre un compte de recette actuellement connecté dans un autre navigateur. | Compte exclu des résultats, accès connecté interrompu/refusé. |
| [ ] A4 | Masquer puis réafficher un avis de recette. | Visibilité et moyenne publique ajustées ; autres avis conservés. |
| [ ] A5 | Depuis un signalement, prendre une décision explicite et renseigner le motif demandé. | Cible exacte affichée ; auteur admin, date, motif et décision conservés. |
| [ ] A6 | Créer deux signalements d'utilisateurs distincts sur la même cible puis masquer/suspendre depuis l'un. | Les signalements en attente sur cette cible sont clôturés selon la décision ; pas de modification d'une autre cible. |
| [ ] A7 | Rejeter un signalement sans masquer la cible ; recharger. | Signalement traité, cible toujours visible, décision traçable. |

## M — Cloudinary réel, après configuration

Ces cases restent vides si les identifiants réels ne sont pas renseignés. Les tests automatisés simulés ne valident pas ce paragraphe.

Recette du 20 octobre 2026 : le refus initial a été corrigé côté Cloudinary. Authentification, import réel des photos dans `beautyconnect`, remplacement et suppression via les formulaires professionnels confirmés. Un fichier secondaire invalide annule les modifications et conserve les anciennes informations et photos. Les six fiches publiques, les six marqueurs et le rendu mobile ont été vérifiés. Les cases ci-dessous servent à votre propre recette ; ne les cocher qu’après exécution. Aucun secret ne doit être ajouté à ce fichier.

- [ ] M1 — Renseigner les clés dans `.env`, `IMAGE_STORAGE_PROVIDER=cloudinary`, `CLOUDINARY_FOLDER=beautyconnect` et le mode correct ; redémarrer sans erreur.
- [ ] M2 — Ajouter une photo d'établissement puis une photo de prestation. Vérifier dans la Media Library le dossier `beautyconnect`, les deux fichiers et les URLs HTTPS dans l'application.
- [ ] M3 — Depuis un autre navigateur, ouvrir les photos publiques : pas de connexion requise à Cloudinary.
- [ ] M4 — Supprimer une photo de recette, vérifier qu'elle disparaît de l'application et de la Media Library. Le cache CDN peut nécessiter un délai d'invalidation.
- [ ] M5 — Remplacer une photo : nouvelle visible, ancien actif supprimé dans la Media Library après commit.
- [ ] M6 — Vérifier qu'une ancienne image locale reste visible tant que le dossier `uploads/` est conservé.
- [ ] M7 — Sur un environnement de test, couper/configurer un accès Cloudinary invalide : refus d'envoi contrôlé, aucun secret dans la page d'erreur. Restaurer immédiatement la configuration correcte.

## G — Navigation, carte et accessibilité

- [ ] G1 — Refaire accueil, connexion, recherche, vitrine, catalogue pro et tableau admin en 390 px puis 1440 px : pas de débordement horizontal global, textes et actions lisibles. Les tables peuvent défiler dans leur propre conteneur.
- [ ] G2 — Sur 320 px et 768 px, ouvrir/fermer le menu principal ; vérifier que les rubriques de l'espace restent accessibles dans la navigation adaptée au mobile.
- [ ] G3 — Parcourir au clavier : lien « Aller au contenu », champs, boutons, menu et modales ; focus visible, fermeture des modales et retour au déclencheur.
- [ ] G4 — Activer « réduire les animations » dans le système/navigateur : transitions discrètes, diaporama initialement en pause.
- [ ] G5 — Refuser la géolocalisation puis rechercher par ville : liste utilisable. Accepter ensuite sur localhost/HTTPS : position ponctuelle et mesures indiquées sans enregistrement dans le compte.
- [ ] G6 — Sans clé ORS, vérifier l'explication du repli et les liens Google Maps/transports ; aucun itinéraire inventé.
- [ ] G7 — Avec une vraie clé ORS, choisir un pro localisé : itinéraire à pied et durée affichés ; erreur réseau gérée. À noter « non testé » sans clé.
- [ ] G8 — Se déconnecter, faire Retour navigateur et recharger une page privée : les actions et données privées ne sont plus accessibles.

## Noter les résultats

| ID | Résultat : OK / KO / non testé | Observation et étapes pour reproduire | Capture / responsable |
|---|---|---|---|
| … | … | … | … |

**Bloquants avant présentation :** …

**Cas non exécutés et raison :** …

**Décision de l'équipe après recette :** …
