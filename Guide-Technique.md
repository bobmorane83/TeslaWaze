# Guide technique de TeslaWaze

> Version 0.1.1 — documentation du fonctionnement interne

Ce document décrit l'architecture, les mécanismes et la compilation de TeslaWaze. Pour
l'utilisation pas à pas, voir le [guide utilisateur](Guide.md).

---

## Sommaire

1. [Principe et contraintes](#1-principe-et-contraintes)
2. [Vue d'ensemble](#2-vue-densemble)
3. [Boucle de surveillance](#3-boucle-de-surveillance)
4. [Règles de pilotage de Waze](#4-règles-de-pilotage-de-waze)
5. [Lancement de Waze](#5-lancement-de-waze)
6. [Arrêt de Waze](#6-arrêt-de-waze)
7. [Extinction de l'écran](#7-extinction-de-lécran)
8. [Cycle de vie du service](#8-cycle-de-vie-du-service)
9. [Connexion, sécurité et stockage](#9-connexion-sécurité-et-stockage)
10. [Architecture du code](#10-architecture-du-code)
11. [Compilation et tests](#11-compilation-et-tests)
12. [Diagnostic](#12-diagnostic)
13. [Limites techniques](#13-limites-techniques)

---

## 1. Principe et contraintes

Objectif : Waze tourne **en arrière-plan**, avec le **guidage vocal coupé**, pour annoncer les
événements communautaires sur le trajet. Le **calcul d'itinéraire et le guidage restent ceux de
la Tesla**.

Contraintes Android prises en compte :

- une application en arrière-plan ne peut pas démarrer d'activité (Android 10+) → autorisation
  `SYSTEM_ALERT_WINDOW` ;
- une application ne peut pas `force-stop` une autre application → arrêt via l'action
  « Éteindre » de la notification Waze ou prise de premier plan ;
- Waze tourne en service de premier plan → `killBackgroundProcesses` est insuffisant seul ;
- pas de root, pas de permission de localisation, pas de serveur intermédiaire.

---

## 2. Vue d'ensemble

```
Application Tesla (cloud)
        │  API Fleet (HTTPS)
        ▼
DestinationService  ── décision ──►  Waze (arrière-plan, alertes)
  (service de premier plan)                 │
        │                                   ▼
        └──────────── arrêt ◄──── notification Waze « Éteindre »
```

L'activité (`MainActivity`) n'est qu'un tableau de bord : la surveillance vit dans un service de
premier plan (`DestinationService`), qui survit à l'appui sur « retour » et au redémarrage du
téléphone.

---

## 3. Boucle de surveillance

| Situation | Appel | Cadence |
|---|---|---|
| Sonde d'état | `GET /api/1/vehicles/{id}` — **ne réveille pas** | **30 s** si endormi/hors ligne |
| Données de conduite | `GET /api/1/vehicles/{id}/vehicle_data?endpoints=drive_state` — **réveille** | **60 s**, uniquement si `online` |
| Erreur réseau / jeton | nouvel essai | **30 s** |

Les trois intervalles (en ligne, endormi, erreur) sont **réglables** dans
**Réglages → Délais** (en ligne 10–120 s, endormi/erreur 10–60 s). Toute modification
s'applique au cycle suivant, sans redémarrer le service. Des valeurs trop courtes multiplient
les appels à l'API et peuvent provoquer des arrêts/relances intempestifs.

La destination et l'état de conduite sont lus dans `drive_state` :
`active_route_latitude`, `active_route_longitude`, `active_route_destination`,
`active_route_minutes_to_arrival`, `shift_state`, `speed`.

Principes :

- la sonde d'état est toujours appelée en premier : elle ne réveille pas la voiture ;
- `vehicle_data` n'est appelé **que si la sonde répond `online`**, une fois par cycle ;
- si l'ID véhicule est inconnu, la liste `GET /api/1/vehicles` est tentée ; avec un jeton legacy
  qui la refuse, l'ID doit être saisi dans les réglages.

Le client est `TeslaApi` : `HttpURLConnection` + `org.json`, aucun client HTTP externe. Les
appels sont `suspend` et s'exécutent sur `Dispatchers.IO`.

---

## 4. Règles de pilotage de Waze

La décision est prise par `WazePilot`, une machine à états sans dépendance Android (testée
unitairement).

| Événement | Action |
|---|---|
| Destination présente, véhicule non garé (ou garé **avant** tout départ) | **Lancer** Waze |
| Même destination qu'au lancement | Ne rien faire |
| Destination différente | **Relancer** Waze |
| Destination retirée | **Arrêter** (immédiat à l'arrêt, confirmé en roulant) |
| Véhicule garé (`P`) **après avoir roulé** | **Arrêter immédiatement** |
| Véhicule hors ligne / endormi | **Arrêter** après confirmation |
| Destination déjà arrêtée | Ne pas relancer tant que le véhicule ne repart pas |

Détails :

- **Deux lectures concordantes** (`STOP_CONFIRMATIONS = 2`) sont exigées **en roulant** avant
  tout arrêt : `active_route_*` devient brièvement nul pendant un recalcul. **À l'arrêt**
  (véhicule non en mouvement), l'arrêt est immédiat.
- Le véhicule est « en mouvement » si `shift_state` vaut `D`, `R` ou `N`, ou si la vitesse
  dépasse 0,5 mph. Rouler réarme l'arrêt et autorise une nouvelle relance.
- La clé de destination est arrondie à ~1 m (`%.5f,%.5f`), ce qui évite les relances inutiles.
- **Destinations factices ignorées** : certains firmwares renvoient « Tesla », des coordonnées
  nulles ou la position du véhicule sans ETA lorsque la navigation est inactive. Ces valeurs
  sont traitées comme « pas de destination » (sinon Waze ne s'arrêterait jamais).

---

## 5. Lancement de Waze

Deep link officiel (aucun patch de Waze) :

```
waze://?ll=<latitude>,<longitude>&navigate=yes&utm_source=teslawaze
```

- coordonnées formatées en `Locale.US` (point décimal), sinon le lien est ignoré ;
- l'intent cible explicitement `com.waze.FreeMapAppActivity` : c'est le seul moyen fiable quand
  Waze tourne déjà ; un intent implicite sert de repli ;
- Waze ignore parfois le lien pendant son démarrage à froid : TeslaWaze le renvoie **une fois
  après 4 secondes** ;
- si l'option d'écran est active, `lockNow()` est appelé environ 1 seconde après ce second
  envoi ;
- la destination est transmise pour que Waze connaisse le trajet ; le guidage vocal doit être
  coupé dans Waze, TeslaWaze ne modifie pas ses réglages internes.

---

## 6. Arrêt de Waze

Trois leviers, dans cet ordre :

1. **Action « Éteindre » de la notification Waze** — via `WazeNotificationListener`, qui parcourt
   les notifications actives de `com.waze` et déclenche l'action dont le titre correspond à
   `Éteindre`, `Fermer`, `Arrêter`, `Turn off`, `Close`, etc. Aucun contenu n'est lu. Si Waze
   publie encore une notification **1,5 s** après l'envoi, l'app passe au levier suivant
   (fermeture forcée).
2. **Prise du premier plan** — `StopWazeActivity` (écran noir, aucune vue) reste visible ~500 ms
   par essai, jusqu'à 8 essais (~4 s) : Waze passe en arrière-plan et publie sa notification,
   que l'on peut éteindre. Exige `SYSTEM_ALERT_WINDOW`.
3. **Dernier recours** — `killBackgroundProcesses("com.waze")` (permission normale
   `KILL_BACKGROUND_PROCESSES`), efficace seulement si Waze n'est pas en service de premier plan.

Sans accès aux notifications ni autorisation « Superposer », l'arrêt peut échouer : le statut
affiche alors « Arrêt de Waze impossible — autorisations manquantes ».

---

## 7. Extinction de l'écran

Sur un téléphone non rooté, `lockNow()` est la seule extinction disponible :
`PowerManager.goToSleep()` exige `DEVICE_POWER`.

- `ScreenOffAdmin` est un `DeviceAdminReceiver` avec la politique minimale `force-lock` ;
- `ScreenOff.turnOff()` appelle `lockNow()` si l'administrateur est actif ;
- Waze n'est pas interrompu : il continue ses alertes écran verrouillé, avec une consommation
  nettement plus faible.

---

## 8. Cycle de vie du service

| Situation | Comportement |
|---|---|
| Ouverture de l'application | surveillance (ré)activée |
| Appui sur « retour » | la surveillance continue |
| « Quitter » (écran ou notification) | `serviceEnabled = false`, service arrêté |
| Redémarrage du téléphone | `BootReceiver` relance si `serviceEnabled` **et** jeton présent |
| Service tué par le système | relance Android (`START_STICKY`) |

La notification permanente (`StatusNotification`, ID 42, canal `IMPORTANCE_LOW`) affiche l'état
et propose « Ouvrir » et « Quitter ».

---

## 9. Connexion, sécurité et stockage

- **Jeton Tesla chiffré** : AES/GCM, clé non exportable de l'Android Keystore
  (`TeslaTokenStore`).
- **Deux flux OAuth** : sans `client_id` configuré, `client_id=ownerapi` +
  `tesla://auth/callback` ; avec un `client_id` Fleet, l'URI de redirection déclarée. Le
  `client_id` utilisé est conservé avec le jeton pour le rafraîchissement.
- **Rafraîchissement** : si le jeton expire dans moins de 2 minutes, `TeslaSession` appelle
  `TeslaAuth.refresh()` ; sans refresh token, la session est effacée et une reconnexion est
  demandée.
- **Hôte Fleet** : déduit de l'audience (`aud`) du JWT (`fleet-api…`) ; repli sur l'hôte
  configuré.
- **HTTPS obligatoire** (`network_security_config`), exceptions limitées à `10.0.2.2` et
  `localhost` pour le développement.
- **Aucune permission de localisation** ; Waze gère le GPS.
- **Accès aux notifications** : uniquement les actions de la notification de Waze.
- **« Superposer »** : aucun élément dessiné, uniquement l'autorisation de démarrer une activité
  depuis l'arrière-plan.
- Aucune télémétrie, aucun serveur tiers.

### Application Fleet API personnelle (facultatif)

Pour utiliser votre propre application déclarée chez Tesla :

1. Créer une application sur [developer.tesla.com](https://developer.tesla.com).
2. Déclarer l'URI de redirection `com.teslawaze.app://oauth`.
3. Renseigner le `client_id` et cette URI dans **Réglages → Avancé**.
4. Utiliser « Se connecter ».

Sans `client_id`, le flux par défaut (`ownerapi` + `tesla://auth/callback`) est utilisé.

---

## 10. Architecture du code

Application mono-module `:app`, Kotlin + Jetpack Compose.

| Fichier | Rôle |
|---|---|
| `DestinationService.kt` | service de premier plan, boucle de surveillance, application des décisions |
| `WazePilot.kt` | machine à états lancement / arrêt (testée unitairement) |
| `TeslaApi.kt` | client minimal de l'API Fleet (HTTP + JSON) |
| `TeslaAuth.kt` | OAuth2 PKCE, rafraîchissement, `TeslaTokenStore` chiffré |
| `TeslaSession.kt` | jeton valide, ID véhicule, hôte Fleet déduit du JWT |
| `WazeLauncher.kt` | deep link Waze |
| `WazeStopper.kt` | stratégie d'arrêt (notification, premier plan, kill) |
| `WazeNotificationListener.kt` | déclenchement de l'action « Éteindre » |
| `StopWazeActivity.kt` | prise de premier plan brève pour rendre le kill effectif |
| `ScreenOffAdmin.kt` | administrateur de périphérique (`force-lock`) |
| `BootReceiver.kt` | relance après redémarrage |
| `StatusNotification.kt` | notification permanente (logo T) |
| `Permissions.kt` | autorisations spéciales (Superposer, accès notifications) |
| `MainActivity.kt` / `SettingsScreen.kt` | tableau de bord et réglages |
| `WazeSettings.kt` | réglages persistés (`SharedPreferences`) |

### Stack

- Kotlin **2.0.21**, AGP **8.7.3**, Gradle **8.9**, JDK **17**
- Jetpack Compose (BOM 2024.09.03), Material 3
- `compileSdk` 35, `targetSdk` 34, `minSdk` 26

---

## 11. Compilation et tests

### Prérequis

- JDK **17**
- Android SDK plateau **35** et Build-Tools correspondants
- (facultatif) Android Studio

### Configuration

```bash
git clone https://github.com/bobmorane83/TeslaWaze.git
cd TeslaWaze
```

`local.properties` (non versionné) :

```properties
sdk.dir=/chemin/vers/Android/Sdk
```

Valeurs publiques facultatives, via `secrets.properties` (gitignoré, copié depuis
`secrets.properties.example`) ou variables d'environnement :

```properties
TESLA_CLIENT_ID=
TESLA_REDIRECT_URI=com.teslawaze.app://oauth
```

Aucune clé secrète n'est nécessaire pour compiler.

### Compilation et installation

```bash
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou dans Android Studio : ouvrir le projet, synchroniser Gradle, puis **Run**.

### Tests

```bash
./gradlew testDebugUnitTest
```

`WazePilotTest` couvre les règles de décision : lancement, relance, arrêt confirmé, anti-rebond,
reprise après arrêt — sans téléphone ni véhicule.

---

## 12. Diagnostic

Journalisation avec le tag `TeslaWaze` :

```bash
adb logcat -s TeslaWaze
```

Principaux messages : décisions de la machine à états, appels Tesla, lancement/arrêt de Waze,
état des autorisations, identité `aud`/`iss` du JWT (aucune donnée sensible).

---

## 13. Limites techniques

- **Fermeture non officielle** : dépend de l'action de notification de Waze et d'une prise de
  premier plan ; une mise à jour de Waze peut modifier ces éléments.
- **Délai d'arrêt** : immédiat à l'arrêt (lecture suivante) ; en roulant, deux lectures
  concordantes (jusqu'à 2 minutes).
- **API Tesla** : jetons révocables, limitation de débit, évolutions possibles.
- **Services de premier plan** : certains constructeurs les tuent malgré tout ; voir la section
  fiabilité du guide utilisateur.
- **Audio** : les alertes suivent le volume multimédia et la sortie audio du téléphone.
- **Waze doit avoir une destination active** pour annoncer les événements du trajet.
- **Guidage vocal** : doit être désactivé manuellement dans Waze.

---

*Guide technique de la version 0.1.1 — voir aussi le [guide utilisateur](Guide.md) et le
[README](README.md).*
