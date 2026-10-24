# TeslaWaze

**Waze en arrière-plan pour les alertes d'événements, pendant que votre Tesla vous guide.**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Sans root](https://img.shields.io/badge/root-non%20requis-success)
![Sans GPS](https://img.shields.io/badge/localisation-non%20requise-informational)
[![Dernière version](https://img.shields.io/github/v/release/bobmorane83/TeslaWaze)](https://github.com/bobmorane83/TeslaWaze/releases/latest)

TeslaWaze lance Waze en arrière-plan pour vous prévenir des événements signalés par la
communauté (police, danger, bouchon…), **sans guidage vocal**. Le calculateur d'itinéraire de la
Tesla reste la référence : Waze reçoit la destination pour connaître le trajet, puis se contente
d'annoncer les événements. Quand le trajet est terminé, Waze se referme.

## Téléchargement

📦 **[Télécharger la dernière version (APK)](https://github.com/bobmorane83/TeslaWaze/releases/latest)**
— version **0.1.1**, APK de développement à installer manuellement.

> 📘 **Guide d'utilisation complet (première installation pas à pas) :
> [Guide.md](Guide.md)**
>
> 🔧 **Fonctionnement interne et compilation : [Guide-Technique.md](Guide-Technique.md)**

---

## Fonctionnalités

- ✅ **Waze en tâche de fond** : seules les alertes d'événements sont utilisées, pas le guidage
  vocal
- ✅ **La Tesla continue de guider** : son planificateur d'itinéraire reste la référence
- ✅ **Lancement automatique** de Waze dès qu'une destination apparaît sur le compte Tesla
- ✅ **Relance automatique** si la destination change
- ✅ **Watchdog Waze** (option) : toutes les 15 s, relance Waze s'il n'est plus actif, uniquement
  lorsqu'une destination Tesla est en cours
- ✅ **Fermeture automatique** de Waze : destination retirée, véhicule garé ou hors ligne
- ✅ **Délais de lecture réglables** dans les paramètres (en ligne 10–120 s, endormi/erreur 10–60 s)
- ✅ **Extinction de l'écran** après le lancement (option), pour économiser la batterie
- ✅ **Aucune permission de localisation** : Waze gère le GPS
- ✅ **Sans root** et **sans serveur intermédiaire**

## Démarrage rapide

1. **Installer** l'APK, puis ouvrir TeslaWaze.
2. **Suivre les autorisations** demandées : notifications, « Afficher par-dessus les autres
   applications », accès aux notifications.
3. **Connecter le compte Tesla** : réglages → **Se connecter** (l'application vous guide), ou
   « Coller un token » (`tesla_auth` / *Auth app for Tesla*).
4. **Configurer Waze** : couper le guidage vocal et laisser les alertes d'événements actives.
5. **Tester** : lancer une navigation dans la Tesla → Waze doit s'ouvrir sous 60 s.

Chaque étape est détaillée dans le **[guide d'utilisation](Guide.md#4-première-utilisation-une-seule-fois)**.

## Utilisation

- Programmer la destination dans la Tesla.
- Suivre l'itinéraire affiché par la Tesla ; Waze reste en arrière-plan et annonce les
  événements.
- À l'arrivée (véhicule garé ou navigation arrêtée), Waze se ferme dès la lecture suivante.
- Toucher l'engrenage en haut à droite ouvre les réglages ; « Quitter » arrête la surveillance
  et ferme Waze si TeslaWaze l'avait lancé.

## Bon à savoir

- Android **8.0** minimum ; pas de version iOS.
- Application **hors Play Store** : installation par APK.
- Waze doit être installé et avoir une destination active pour annoncer les événements.
- Le guidage vocal de Waze doit être coupé manuellement.
- Les alertes dépendent du volume multimédia et de la sortie audio du téléphone.
- Certains constructeurs limitent les applications en arrière-plan : voir la section fiabilité
  du guide.

## Contribuer

Les issues et pull requests sont bienvenues. Avant de proposer une modification :

1. Lancer `./gradlew testDebugUnitTest`.
2. Ne jamais inclure de jeton, de clé ou de fichier `secrets.properties`.

Les détails d'architecture et de compilation sont dans le
**[guide technique](Guide-Technique.md)**.

## Licence

Aucun fichier `LICENSE` n'est encore fourni. Sans licence explicite, la réutilisation reste
limitée : ouvrez une issue pour discuter de la licence souhaitée.

---

*Waze et Tesla sont des marques de leurs propriétaires respectifs. Ce projet n'est affilié ni à
Waze/Google ni à Tesla.*
