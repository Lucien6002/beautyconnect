# Joyce — design général et expérience du site

> Parcours recommandé : lire [l’audit](../AUDIT-TECHNIQUE.md), puis [le guide commun Git/design/Render](../ROADMAP-EQUIPE.md), puis suivre les étapes ci-dessous. Les fonctions nouvelles sont proposées ; vérifier le code avant de les implémenter.


**Objectif :** faire de BeautyConnect un site lisible, crédible et agréable sur téléphone comme sur ordinateur. **Branche :** `feature/joyce-design-system`. **Charge :** 7 à 8 jours, difficulté 3/5. Lire d'abord [`AUDIT-TECHNIQUE.md`](../AUDIT-TECHNIQUE.md) et [le guide commun](../ROADMAP-EQUIPE.md).

## Comprendre le code avant de dessiner

1. Ouvrir `src/main/java/com/beautyconnect/controller/HomeController.java` : les attributs `searchCriteria`, `genders`, `types` nourrissent `templates/index.html`.
2. Ouvrir `templates/fragments/head.html`, `navbar.html`, `alerts.html`, `footer.html`, puis `static/css/custom.css` : ce sont les éléments réutilisés sur les pages.
3. Ouvrir `templates/auth/login.html`, `register-client.html`, `register-professional.html` et `templates/search/results.html`. Relever `th:action`, `th:field`, `sec:authorize` avant tout changement ; ces attributs sont fonctionnels.
4. Dessiner les trois parcours mobiles : chercher, lire une fiche, réserver. Une personne doit voir le prix, la durée et la prochaine disponibilité sans chercher dans plusieurs écrans.

## Tâches dans l'ordre

| Étape | Durée | Action et preuve de fin |
|---|---:|---|
| J1 | 1 j | Créer une petite planche de style dans `docs/design/` : palette/tokens du document équipe, typographies, boutons, états, largeur de carte et formulaire, exemples de messages. Faire valider les noms `--bc-*` par tous. |
| J2 | 1,5 j | Implémenter les tokens dans `static/css/custom.css` et harmoniser `fragments/head.html`, `navbar.html`, `footer.html`, `alerts.html`. Vérifier contraste, focus clavier, menu mobile et flash messages. |
| J3 | 1 j | Refaire `templates/index.html` : titre clair, recherche mise en avant, catégories, explication du parcours, appel pro. Aucun chiffre inventé ni photo présentée comme un vrai prestataire si elle est illustrative. |
| J4 | 1,5 j | Harmoniser les trois templates `auth/*` : labels, aides, erreurs proches des champs, états d'activation et redirection après inscription. Conserver `th:object`, `th:field`, `th:errors`, URL des formulaires et CSRF. |
| J5 | 1 j | Définir et documenter la carte résultat, fiche pro, vignette portfolio, badge « validé », prix, créneau ; fournir exemples HTML/CSS à Anaelle et Ulrich, sans modifier leurs templates en même temps. |
| J6 | 1 j | Revue à 360/390/768/1440 px des pages livrées par tous, navigation clavier, états sans résultat/sans image/sans créneau, corrections ciblées dans les fichiers communs. |

**Fichiers en écriture :** `src/main/resources/static/css/custom.css`, `src/main/resources/static/js/main.js` si nécessaire, `src/main/resources/templates/fragments/*`, `src/main/resources/templates/index.html`, `src/main/resources/templates/auth/*`, `docs/design/*`. **Lecture seule :** contrôleurs/services, `templates/search/*`, `professional/*`, `client/*`, `admin/*`. Les propriétaires de ces pages appliquent les composants ; Joyce leur transmet une revue et une petite PR seulement si accord.

## Ce que tu dois apprendre

- MVC : pourquoi `HomeController.home` ajoute des valeurs au `Model` avant `index.html`.
- Thymeleaf : `th:replace`, `th:action`, `th:object`, `th:field`, `th:errors`; sécurité UI `sec:authorize` dans `fragments/navbar.html`.
- Bootstrap 5 : grille responsive et formulaire ; CSS variables et ordre de chargement (`fragments/head.html` charge Bootstrap puis `custom.css`).
- Accessibilité : contraste, focus, labels, titre de page, texte alternatif, états d'erreur ; ne jamais dépendre uniquement de la couleur.

## Critères de terminé

- Les six parcours clés sont lisibles sur mobile : accueil, recherche, profil, inscription, tableau client, tableau pro/admin.
- Une même action conserve le même libellé ; les formulaires gardent leurs noms/champs et fonctionnent.
- Tous les propriétaires ont la planche de tokens ; aucune vue métier n'utilise une couleur arbitraire non documentée.
- Captures avant/après et vérification manuelle clavier + mobile dans la PR ; `./gradlew test` dans un environnement où Gradle est disponible.

## Prompt à donner à ton IA

> Tu aides Joyce sur BeautyConnect. Lis d'abord `docs/AUDIT-TECHNIQUE.md`, `docs/ROADMAP-EQUIPE.md`, ce fichier et les fichiers réels `HomeController.java`, `templates/fragments/*`, `templates/index.html`, `templates/auth/*`, `static/css/custom.css`. Explique-moi en 5 phrases le flux contrôleur → Model → Thymeleaf et les attributs `th:*` que je ne dois pas casser. Pour la tâche J[NUMÉRO], propose deux options visuelles adaptées aux étudiants qui cherchent un prestataire local, puis implémente seulement l'option retenue dans mes fichiers. Montre les différences, explique chaque choix CSS, vérifie mobile, clavier et erreurs. Ne modifie pas les pages possédées par les autres sans coordination. Ne crée pas de faux avis ou chiffres. Termine par ce que j'ai appris et les commandes Git/PR à exécuter.

## Publier ton travail sur GitHub

Après publication de la documentation sur `main`, créer ta branche depuis `main`. Vérifier `git status` avant de changer de branche : la modification locale actuelle de `application.properties` appartient à Ulrich et ne doit pas être incluse par accident.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/joyce-design-system
# coder une tâche, puis vérifier et sélectionner seulement ses modifications (`git add -p` pour les fichiers suivis ; `git add chemin/du/nouveau/fichier` pour un nouveau fichier)
git status --short
git add -p
git commit -m "feat(joyce): decrire la tache"
git push -u origin feature/joyce-design-system
```

Ouvrir une Pull Request vers `main`, avec tâche numérotée, règles métier, captures si UI, tests exécutés, ce que tu as appris et fichiers partagés touchés. Demander une revue à un autre membre ; fusionner seulement après CI verte. Si `main` avance, `git fetch origin` puis `git merge origin/main` dans ta branche et refaire les tests. Pour le cycle complet et Render, voir [le guide commun](../ROADMAP-EQUIPE.md).
