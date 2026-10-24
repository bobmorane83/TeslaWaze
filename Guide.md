# Guide d'utilisation de TeslaWaze

> Version 0.1.1 — Android 8.0 ou plus récent

TeslaWaze fait une chose simple : il lance **Waze en arrière-plan** pour vous prévenir des
**événements signalés par la communauté** (police, danger, bouchon…). **Le guidage reste celui
de votre Tesla** : Waze ne vous guide pas et ne parle pas à sa place.

Ce guide explique tout ce qu'il faut faire, sans connaissances techniques.

---

## Sommaire

1. [Ce que fait l'application](#1-ce-que-fait-lapplication)
2. [Avant de commencer](#2-avant-de-commencer)
3. [Installer l'application](#3-installer-lapplication)
4. [Première utilisation (une seule fois)](#4-première-utilisation-une-seule-fois)
5. [Utilisation au quotidien](#5-utilisation-au-quotidien)
6. [Les réglages](#6-les-réglages)
7. [Messages affichés](#7-messages-affichés)
8. [Problèmes courants](#8-problèmes-courants)
9. [Bon à savoir](#9-bon-à-savoir)
10. [Aller plus loin](#10-aller-plus-loin)

---

## 1. Ce que fait l'application

Pendant que vous conduisez, votre Tesla calcule et affiche l'itinéraire. TeslaWaze utilise votre
téléphone uniquement pour **faire parler Waze des événements** rencontrés sur le trajet.

Concrètement :

- vous programmez la destination dans la Tesla ;
- TeslaWaze ouvre Waze sur cette destination, puis le met en arrière-plan ;
- **la Tesla continue de vous guider** ; Waze annonce seulement les événements ;
- à l'arrivée (véhicule garé ou navigation arrêtée), Waze se ferme automatiquement ;
- en option, l'écran du téléphone se verrouille après le lancement pour économiser la batterie.

> TeslaWaze n'est **pas** un GPS. Il ne remplace pas le planificateur de la Tesla et n'affiche
> aucun itinéraire.

---

## 2. Avant de commencer

| Élément | Ce qu'il faut |
|---|---|
| Téléphone | Android **8.0** ou plus récent |
| Waze | installé sur le téléphone |
| Compte Tesla | une connexion à votre compte Tesla — voir §4.5 |
| Waze configuré | **guidage vocal coupé**, alertes d'événements actives — voir §4.8 |

Aucune voiture n'est nécessaire pour la configuration, mais le test final oui.

---

## 3. Installer l'application

TeslaWaze n'est pas disponible sur le Play Store. Il s'installe à partir d'un fichier APK :

1. Télécharger la dernière version depuis la page
   [Releases](https://github.com/bobmorane83/TeslaWaze/releases/latest), ou copier le fichier
   d'installation (`app-debug.apk`) sur le téléphone.
2. L'ouvrir depuis l'explorateur de fichiers.
3. Si Android le demande, autoriser l'installation depuis cette source.
4. Toucher **Installer**, puis **Ouvrir**.

---

## 4. Première utilisation (une seule fois)

Suivez les étapes dans l'ordre. Si vous fermez une demande avec « Plus tard », vous pourrez la
retrouver plus tard en touchant l'engrenage en haut à droite de l'application.

### 4.1 Ouvrir l'application

Au premier lancement, TeslaWaze démarre sa surveillance et vous présente les demandes
d'autorisation. L'écran principal affiche l'état de la surveillance.

### 4.2 Autoriser les notifications

**Pourquoi** : afficher le logo **T** dans la barre du téléphone et proposer les boutons
« Ouvrir » et « Quitter ».

**Comment** : accepter la demande « Autoriser TeslaWaze à envoyer des notifications ? ».

### 4.3 Autoriser « Afficher par-dessus les autres applications »

**Pourquoi** : Android interdit à une application en arrière-plan d'ouvrir Waze. Cette
autorisation lève ce blocage. **Rien n'est affiché par-dessus vos autres applications** :
l'autorisation sert uniquement à permettre ce démarrage.

**Comment** :

1. Dans la fenêtre « Autorisation requise », toucher **Autoriser**.
2. Activer le commutateur **TeslaWaze** dans l'écran qui s'ouvre.
3. Revenir à l'application.

Vérification : les réglages indiquent « Autorisation « Superposer » : accordée ».

### 4.4 Autoriser l'accès aux notifications

**Pourquoi** : c'est ce qui permet à TeslaWaze d'appuyer sur le bouton **« Éteindre »** de la
notification de Waze, seule façon de le fermer proprement. **Aucun contenu n'est lu.**

**Comment** :

1. Toucher **Autoriser** dans la fenêtre.
2. Activer **TeslaWaze** dans « Accès aux notifications ».
3. Revenir à l'application.

Vérification : les réglages indiquent « Accès aux notifications : accordé ».

### 4.5 Connecter votre compte Tesla

TeslaWaze a besoin de lire la destination programmée dans votre Tesla. Deux méthodes existent ;
la première est la plus simple.

#### Méthode 1 — connexion guidée par l'application (recommandée)

1. Toucher l'**engrenage** en haut à droite pour ouvrir les réglages.
2. Toucher **Se connecter**.
3. Se connecter avec votre compte Tesla sur la page qui s'ouvre.

L'application vous guide de bout en bout : aucun autre réglage n'est nécessaire.

Votre connexion est conservée et renouvelée automatiquement. Vous ne referez cette étape qu'en
cas de déconnexion ou de révocation.

#### Méthode 2 — coller un jeton

Un **jeton** est un code d'accès temporaire généré par un outil de connexion Tesla.

1. Générez un jeton avec un outil reconnu :
   - **tesla_auth** (utilisé par TeslaMate) ;
   - ou l'application mobile **Auth app for Tesla**.
2. Dans TeslaWaze, toucher l'**engrenage** en haut à droite pour ouvrir les réglages.
3. Toucher **Coller un token**.
4. Coller les deux codes fournis par l'outil, puis **Enregistrer**.

Votre connexion reste sur votre téléphone et n'est jamais partagée avec un autre service.

Pour les cas particuliers (application Tesla personnelle, réglages avancés), voir le
[guide technique](Guide-Technique.md#9-connexion-sécurité-et-stockage).

### 4.6 Si l'application le demande : identifier votre véhicule

Il arrive que TeslaWaze ne puisse pas retrouver votre voiture tout seul. L'application affiche
alors « ID véhicule manquant ».

1. Toucher l'**engrenage** en haut à droite → **Avancé**.
2. Renseigner l'**ID véhicule** : un numéro fourni par votre compte Tesla ou par l'outil qui a
   servi à vous connecter.
3. Fermer les réglages : la valeur est enregistrée automatiquement.

### 4.7 Autoriser le verrouillage de l'écran (optionnel)

**Pourquoi** : verrouiller l'écran du téléphone après le lancement de Waze, pour économiser la
batterie. Waze continue ses alertes.

**Comment** : accepter la fenêtre qui s'affiche une seule fois. Android peut utiliser le mot
« administrateur » dans cette fenêtre : cette autorisation sert uniquement à verrouiller
l'écran.

Vous pouvez désactiver cette fonction à tout moment dans les réglages (« Éteindre l'écran après
le lancement »).

### 4.8 Configurer Waze (important)

TeslaWaze ne modifie pas les réglages internes de Waze. Pour que Waze n'ajoute **que les
alertes** et ne double pas le guidage de la Tesla :

1. Ouvrir **Waze** → **Paramètres** → **Voix et son**.
2. **Couper le guidage vocal** (désactiver la voix de navigation).
3. Laisser les **alertes d'événements** actives.
4. Monter le **volume multimédia** du téléphone (ou vérifier la sortie Bluetooth).

Les libellés peuvent varier selon la version de Waze.

### 4.9 Fiabilité selon le téléphone (recommandé)

Certains téléphones (Xiaomi, Samsung, Huawei, Oppo…) arrêtent les applications en arrière-plan.

1. **Paramètres → Applications → TeslaWaze → Batterie** → choisir **« Non restreinte »** (ou
   « Sans restriction »).
2. Si votre téléphone le propose, autoriser le **démarrage automatique** de TeslaWaze.

### 4.10 Tester

1. Vérifier que Waze est installé et que l'application affiche « Waze installé — surveillance
   active ».
2. Vérifier que le **guidage vocal de Waze est coupé** (§4.8).
3. Lancer une navigation dans la Tesla.
4. Sous **60 secondes**, Waze doit s'ouvrir (et l'écran se verrouiller si l'option est active).
   Le guidage reste celui de la Tesla.
5. Vérifier que le volume des alertes est audible (volume multimédia / Bluetooth).
6. Arrêter la navigation, ou garer le véhicule (`P`).
7. **À l'arrêt**, Waze doit se fermer dès la lecture suivante (au plus ~1 minute).

### 4.11 Récapitulatif

- [ ] Installer l'APK et ouvrir TeslaWaze
- [ ] Autoriser les notifications
- [ ] Autoriser « Afficher par-dessus les autres applications »
- [ ] Autoriser l'accès aux notifications
- [ ] Connecter le compte Tesla (connexion guidée, ou jeton collé)
- [ ] Renseigner l'ID véhicule si l'application le demande
- [ ] Autoriser le verrouillage de l'écran (si extinction d'écran souhaitée)
- [ ] Configurer Waze : guidage vocal coupé, alertes actives
- [ ] Désactiver l'optimisation de batterie pour TeslaWaze
- [ ] Faire le test de bout en bout

---

## 5. Utilisation au quotidien

1. **Ouvrir TeslaWaze une fois** après l'installation, ou après avoir utilisé « Quitter ».
   L'application peut ensuite rester en arrière-plan.
2. **Programmer la destination** dans la Tesla (écran du véhicule ou application Tesla).
3. **Suivre l'itinéraire de la Tesla** : Waze s'ouvre seul, passe en arrière-plan et annonce les
   événements.
4. **Régler le son** : les alertes utilisent le volume multimédia du téléphone (ou le Bluetooth).
   Si l'extinction d'écran est désactivée, appuyer sur **Accueil** pour mettre Waze en
   arrière-plan sans le fermer.
5. **À l'arrivée** : garer le véhicule en `P`, ou effacer la navigation. Waze se ferme dès
   la lecture suivante (au plus ~1 minute) si le véhicule est arrêté.
6. **Toucher l'engrenage** en haut à droite ouvre les réglages ; **« Quitter »** arrête la surveillance
   jusqu'à la prochaine ouverture de l'application. Si TeslaWaze avait lancé Waze, il le ferme
   également.

La notification permanente (logo **T**) indique ce que fait l'application et propose
« Ouvrir » et « Quitter ».

---

## 6. Les réglages

Toucher l'**engrenage** en haut à droite ouvre les réglages.

| Réglage | À quoi il sert |
|---|---|
| **Superposer** | indique si l'autorisation nécessaire est accordée ; bouton pour la demander |
| **Accès aux notifications** | idem pour l'autorisation qui permet de fermer Waze |
| **Éteindre l'écran après le lancement** | verrouille l'écran après l'ouverture de Waze (défaut : activé) |
| **Relancer Waze toutes les 15 s s'il n'est plus actif** | vérifie que la navigation Waze tourne encore ; si Waze a été fermé, renvoie la destination Tesla (défaut : désactivé) |
| **Délais** | intervalles de lecture de l'API Tesla (en ligne 10–120 s, endormi/erreur 10–60 s ; défauts 60 / 30 / 30 s) |
| **Compte Tesla** | état de la connexion, boutons **Se connecter**, **Coller un token**, **Se déconnecter** |
| **Avancé** | réglages réservés : ne pas modifier sans consigne (identification du véhicule, cas particuliers) |

Le **watchdog Waze** n'agit que lorsqu'une **destination Tesla est active**. Sans destination
dans la voiture, il ne lance rien. Waze ne permet pas de lire sa destination interne : le
watchdog vérifie que sa notification de navigation est toujours présente, et renvoie la
destination Tesla si Waze a été fermé.

En usage normal, seuls la connexion Tesla et l'option d'écran sont à toucher. Les champs
« Avancé » ne servent qu'en cas de besoin particulier.

Les trois **délais** se règlent par curseurs (en ligne : 10 à 120 s ; endormi/erreur : 10 à
60 s). **Les réduire fortement multiplie les appels à l'API Tesla (risque de limitation) et
peut provoquer des arrêts ou relances intempestifs.** L'intervalle « en ligne » sert aussi de
délai de confirmation d'arrêt : plus il est court, plus Waze se ferme vite.

---

## 7. Messages affichés

| Message | Ce que cela signifie | Que faire |
|---|---|---|
| `Connexion Tesla requise` | aucun jeton n'est enregistré | connecter le compte Tesla (§4.5) |
| `Connecté — expire dans N h` | tout va bien | rien |
| `Connexion Tesla expirée — rouvrez l'app` | l'accès a été révoqué | reconnecter le compte |
| `ID véhicule manquant (Réglages → Avancé)` | le véhicule n'a pas pu être identifié | renseigner l'ID véhicule (§4.6) |
| `Véhicule endormi/offline — en attente` | la voiture ne répond pas | normal ; ouvrir l'application Tesla pour la réveiller |
| `En ligne — pas de destination de navigation` | aucune navigation en cours | programmer une destination |
| `Navigation vers …` | Waze a été lancé | rien |
| `Arrêt en cours — …` | vérification avant fermeture (véhicule en mouvement) | attendre la lecture suivante |
| `Véhicule arrêté — en attente d'un nouveau départ` | trajet terminé | repartir pour relancer Waze |
| `Waze arrêté (destination retirée / véhicule garé / véhicule hors ligne)` | fermeture normale | rien |
| `Arrêt de Waze impossible — autorisations manquantes` | une autorisation manque | accorder « Superposer » et l'accès aux notifications |
| `Waze introuvable` | Waze n'est pas installé | installer Waze |
| `Tesla : …` | problème réseau ou service Tesla | vérifier la connexion Internet, réessayer |

---

## 8. Problèmes courants

| Problème | Solution |
|---|---|
| Waze ne s'ouvre pas | vérifier que l'application affiche « Waze installé — surveillance active » ; accorder « Superposer » ; vérifier l'ID véhicule ; réveiller la voiture dans l'application Tesla |
| Waze ne se ferme pas | accorder l'accès aux notifications ; accorder « Superposer » ; attendre jusqu'à 1 minute |
| Waze parle en même temps que la Tesla | couper le guidage vocal dans Waze (§4.8) |
| Aucune alerte audible | activer les alertes dans Waze ; augmenter le volume multimédia ; vérifier le Bluetooth |
| La surveillance s'arrête seule | mettre TeslaWaze en batterie « Non restreinte » et autoriser le démarrage automatique |
| Rien ne se passe après un redémarrage | rouvrir TeslaWaze (ou vérifier que « Quitter » n'a pas été utilisé) |
| Waze se ferme alors que le trajet continue | attendre : la fermeture n'a lieu qu'après deux vérifications concordantes |

Pour un diagnostic approfondi, voir le [guide technique](Guide-Technique.md).

---

## 9. Bon à savoir

- Android **8.0** minimum ; pas de version iOS.
- TeslaWaze n'est **pas sur le Play Store** : l'installation se fait par APK.
- Waze doit avoir une destination active pour connaître le trajet et annoncer les événements.
- La fermeture est **immédiate à l'arrêt** ; en roulant, deux vérifications (jusqu'à 2 min)
  évitent un faux arrêt.
- Les alertes dépendent du **volume multimédia** et de la sortie audio du téléphone.
- Une mise à jour de Waze peut modifier la façon dont il se ferme.
- Aucune permission de localisation n'est demandée : Waze gère le GPS.
- Votre connexion Tesla reste sur le téléphone ; aucune donnée n'est envoyée à un service tiers.

---

## 10. Aller plus loin

- **Fonctionnement interne, architecture, compilation** :
  [Guide technique](Guide-Technique.md)
- **Présentation du projet** : [README](README.md)

---

*Guide utilisateur de la version 0.1.1.*
