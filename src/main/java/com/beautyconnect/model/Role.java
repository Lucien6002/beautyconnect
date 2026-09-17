package com.beautyconnect.model;

/**
 * Les 3 acteurs de la plateforme (cf. specifications fonctionnelles).
 *
 * Un "enum" (enumeration) en Java est un type qui ne peut prendre qu'un
 * nombre fixe de valeurs connues a l'avance. Ici, un utilisateur a
 * obligatoirement l'un de ces 3 roles, jamais une autre valeur : le
 * compilateur empeche toute erreur de frappe (contrairement a une simple
 * chaine de caracteres "CLIENT" tapee a la main un peu partout dans le code).
 */
public enum Role {
    CLIENT,
    PROFESSIONAL,
    ADMIN
}
