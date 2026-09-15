# Mapbox Turn-by-Turn Navigation — Design Spec (Fáze 3)
**Date:** 2026-09-15
**Status:** Approved

---

## 1. Overview

Přidat turn-by-turn navigaci počítanou přímo v CarLauncheru přes **Mapbox Navigation SDK**, jako druhou, nezávislou cestu k navigaci vedle dnešního notification-scraping (Google Maps/Mapy.cz/Organic Maps). Celá zkušenost — vyhledání cíle, trasa, pokyny, hlas — žije přímo v panelu **Mapa** jako overlay nad `MapWidget`, ve stylu referenční appky Autozen (search bar trvale nahoře přes mapu). Panel **Navigace** (notifikace) a zelené tlačítko "Navigovat" (přepíná na panel Navigace) zůstávají beze změny — dvě nezávislé, přepínatelné cesty.

Fáze 3 zahrnuje **celou zkušenost najednou** (na rozdíl od Fáze 2, kde jsme postupovali po krocích): vyhledávání libovolného cíle, výpočet trasy, vizuální turn-by-turn UI i hlasové pokyny. Použijí se Mapboxí **hotové UI komponenty** (drop-in) nad Navigation SDK, ne vlastní ruční implementace.

---

## 2. Rozsah

**Přidává se:**
- `DestinationSearchBar` — trvalý overlay nahoře na `MapWidget`, Mapbox Search Box SDK pro vyhledávání míst
- Výpočet trasy + turn-by-turn guidance přes Mapbox Navigation SDK (`com.mapbox.navigationcore:android`)
- Hotové Mapbox UI komponenty: `MapboxManeuverView` (pokyn), `MapboxTripProgressView` (ETA/vzdálenost), `MapboxRouteLineApi`/`MapboxRouteLineView` (trasa na mapě), `MapboxVoiceInstructionsPlayer` (hlas)
- Nová oprávnění v manifestu: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`

**Nemění se (mimo rozsah):**
- `NavAreaWidget`, `NavWidget`, `NavRepository`, `MediaListenerService` — panel Navigace beze změny
- Zelené tlačítko "Navigovat" v `MapWidget` — pořád přepíná na panel Navigace (`MapNavPanel`), nezávisle na nové navigaci
- `MapNavPanel` přepínací logika (Fáze 1) — beze změny
- Žádná perzistence trasy přes restart appky/zařízení
- `RouteSnapHelper` — zůstává používaný jen pro mimo-navigační auto-follow (viz §3)

---

## 3. Architektura a flow

**`DestinationSearchBar`** — overlay v `MapWidget`'s `Box`, styl CarColors (lupa + "Hledat", jako Autozen). Tap otevře výsledky z Mapbox Search Box SDK, biased k aktuální poloze.

**Flow:**
1. Tap na search bar → našeptávač míst (Search Box SDK)
2. Výběr cíle → `MapboxNavigation.requestRoutes(...)`, trasa se vykreslí přes `MapboxRouteLineApi`/`MapboxRouteLineView`
3. Start navigace → `startTripSession()`; kamera se přepne na Navigation SDK vlastní sledovací systém (`NavigationCamera`/`ViewportDataSource` — predikce zatáček, náklon/zoom podle rychlosti); zobrazí se `MapboxManeuverView` + `MapboxTripProgressView` jako overlaye; spustí se `MapboxVoiceInstructionsPlayer`
4. Tlačítko "Ukončit" (stejný vzor jako `NavWidget`) → `stopTripSession()`, návrat k volnému pohledu s search barem

**Kamera a puck během aktivní navigace:**
- Kamera: dokud běží trip session, `MapWidget`'s vlastní `easeTo`/`isFollowing` logika se **vynechává** — kameru řídí Navigation SDK. Po `stopTripSession()` se vrací dnešní chování.
- Puck (`AppLocationProvider`): stejný mechanismus (`LocationProvider`/`LocationConsumer`), ale zdroj dat se přepíná — mimo navigaci krmen z `VehicleDisplayLocation` (Kalman-filtrovaná, `RouteSnapHelper`-snapnutá) jako dnes; během aktivní navigace krmen z Navigation SDK vlastní enhanced/map-matched polohy (`LocationObserver`), přesněji přichycené k trase.

**Trip session lifecycle — mimo `MapWidget`'s `remember` scope:** `MapboxNavigation`/trip session žije napojený na Activity lifecycle (`MapboxNavigationApp`), ne na `MapWidget`'s vlastní kompozici. Důvod: `MapWidget` se při přepnutí na panel Navigace kompletně disposuje (`MapView.onDestroy()` — oprava leaku z Fáze 2). Aktivní navigace (hlas, foreground service, trasa) tak přežije přepnutí panelu; po návratu na Mapu se vizuální stav (trasa, kamera, banner) obnoví ze stavu trip session.

**Rerouting** — řeší Navigation SDK sám (výchozí `RerouteController`), žádná vlastní implementace.

---

## 4. Nastavení a nové závislosti

- `com.mapbox.navigationcore:android` — verze podle minor.patch shodného s Maps SDK (`11.30.1` → zkusit `3.30.1`); přesně ověřit při implementaci, Mapboxí dokumentace k pravidlu párování verzí byla nejednoznačná
- Mapbox Search SDK — přesný artefakt/verze ověřit při implementaci
- **Žádný nový token** — stejný `MAPBOX_ACCESS_TOKEN` (nastavený už v `CarLauncherApp`) funguje pro Maps SDK, Navigation SDK i Search SDK
- Nová oprávnění v `AndroidManifest.xml`: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (Android 13+) — Navigation SDK běží trip session jako foreground service s trvalou notifikací

---

## 5. Okrajové případy

- Žádný výsledek hledání / nedostupná trasa → chybová zpráva v search UI, žádný pád
- Zamítnuté `POST_NOTIFICATIONS` → trip session pokračuje (jen bez viditelné systémové notifikace)
- Ukončení appky/restart zařízení během navigace → trip session se neobnoví (žádná perzistence, mimo rozsah)
- Přepnutí na panel Navigace během aktivní Mapbox navigace → trip session přežije (viz §3), vizuál se obnoví po návratu

---

## 6. Testování

Bez test frameworku (stejná konvence jako Fáze 1/2) — ověření: `./gradlew compileDebugKotlin`/`assembleDebug` + manuální QA na fyzickém tabletu:
1. Tap na search bar, vyhledání reálného místa, výběr z výsledků
2. Trasa se vykreslí na mapě
3. Start navigace — banner s pokynem, ETA/vzdálenost, hlas, kamera sleduje trasu
4. Přepnutí na panel Navigace a zpět — trip session/hlas pokračuje, vizuál se po návratu obnoví
5. Tlačítko "Ukončit" — návrat k volnému pohledu s search barem
6. Zelené tlačítko "Navigovat" a panel Navigace (notifikace) fungují beze změny, nezávisle na nové navigaci

---

## 7. Mimo rozsah

- Perzistence trasy/navigace přes restart appky nebo zařízení
- Vlastní (ne-Mapbox) UI nad Navigation SDK core API — použijeme hotové komponenty
- Jakékoli změny v `NavAreaWidget`, `NavWidget`, `NavRepository`, `MediaListenerService`, `MapNavPanel`
