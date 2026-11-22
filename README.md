# VLSM + NetTools na Wear OS ⌚

Dwie natywne aplikacje na zegarek (Galaxy Watch 4 Classic i nowsze):

- **VLSM** – kalkulator podsieci (opis poniżej),
- **NetTools** – ściąga komend Cisco IOS po polsku + narzędzia sieciowe ([opis](#nettools)).


Natywna aplikacja (Kotlin + Jetpack Compose for Wear OS) do liczenia sieci **VLSM** i innych
obliczeń IPv4 – zaprojektowana pod **Samsung Galaxy Watch 4 Classic** (okrągły ekran, obrotowy bezel).
Działa samodzielnie, bez telefonu i bez internetu.

| Menu | VLSM | Liczba hostów (bezel) | Wynik |
|---|---|---|---|
| ![](docs/01_home.png) | ![](docs/02_vlsm.png) | ![](docs/03_subnet_edit.png) | ![](docs/04_result.png) |
| **Klawiatura IP** | **Kalkulator IP** | **FLSM** | **Sumaryzacja** |
| ![](docs/06_keypad_ip.png) | ![](docs/08_ipcalc.png) | ![](docs/09_flsm.png) | ![](docs/10_summary.png) |

## Funkcje

- **Kalkulator VLSM** – sieć bazowa + lista podsieci (nazwa, liczba hostów):
  - przydział od największej podsieci (lub w kolejności wpisania, z poprawnym wyrównaniem),
  - dla każdej podsieci: adres sieci, prefiks, maska, zakres hostów, broadcast, zapas,
  - kolorowy pasek wykorzystania przestrzeni adresowej, % zajętości i efektywność,
  - lista **wolnych bloków** pozostałych po przydziale,
  - gdy się nie mieści: ile adresów brakuje i przycisk „Użyj /X” (powiększa sieć),
  - opcja **/31 i /32** dla łączy punkt-punkt (RFC 3021),
  - podgląd na żywo przed obliczeniem („Zajmie 152/256 adr.”).
- **Kalkulator IP** – sieć, maska, wildcard, pierwszy/ostatni host, broadcast, liczba hostów,
  klasa, typ adresu (prywatny, publiczny, CGNAT, loopback, link-local, multicast…), poprzednia
  i następna sieć, zapis binarny z granicą prefiksu, hex, strefa odwrotnego DNS; szybka zmiana prefiksu −/+.
- **Podział równy (FLSM)** – według liczby podsieci, liczby hostów albo nowego prefiksu.
- **Sumaryzacja tras** – trasa sumaryczna (supernet) + dokładna agregacja i liczba „nadmiarowych” adresów.
- **Ta sama sieć?** – czy dwa adresy są w jednej podsieci, wspólna nadsieć, ostrzeżenia
  (adres sieci / broadcast).
- **Ile hostów → prefiks** – liczba hostów bezelem, od razu prefiks, maska, rozmiar bloku i zapas.
- **Tabela masek** /0–/32 (dotknięcie otwiera kalkulator IP z danym prefiksem).
- **Zapisane projekty** – zapis, wczytywanie, zmiana nazwy, usuwanie. Cały stan zapisuje się automatycznie.

## Sterowanie

- **Bezel** przewija listy i zmienia liczbę hostów (szybki obrót = skok o 10/100), z wibracją przy każdym kroku.
- **Klawiatura** dopasowana do okrągłego ekranu: sama wstawia kropki (po 3 cyfrach, po „0”,
  gdy oktet przekroczyłby 255) i ukośnik po pełnym adresie. Przytrzymaj ⌫, aby wyczyścić.
  Akceptuje `10.0.0.0/8`, `10.0.0.0/255.0.0.0` lub sam adres (maska klasowa).
- **Przesunięcie w prawo** – powrót. Dotknięcie nazwy podsieci – zmiana nazwy (klawiatura lub głos).

## Instalacja na Galaxy Watch 4 Classic

Gotowe pliki APK: zakładka **Actions** → ostatni build → artefakty `vlsm-wear-apk` i `nettools-apk`
(albo zbuduj sam – patrz niżej).

1. Na zegarku: **Ustawienia → Informacje o zegarku → Oprogramowanie** → 7× dotknij
   „Wersja oprogramowania”, aby włączyć opcje programisty.
2. **Ustawienia → Opcje programisty**: włącz **Debugowanie ADB** i **Debugowanie bezprzewodowe**
   (zegarek i komputer w tej samej sieci Wi-Fi).
3. W „Debugowanie bezprzewodowe” wybierz **Paruj nowe urządzenie** i na komputerze:
   ```bash
   adb pair IP_ZEGARKA:PORT_PAROWANIA
   ```
   (wpisz kod z zegarka), a potem:
   ```bash
   adb connect IP_ZEGARKA:PORT
   ```
4. Zainstaluj:
   ```bash
   adb install -r app-release.apk
   adb install -r nettools-release.apk
   ```
5. Aplikacje **VLSM** i **NetTools** pojawią się na liście aplikacji zegarka.

## NetTools

| Menu | Kategorie | Komenda | Gotowa konfiguracja |
|---|---|---|---|
| ![](docs/nettools/01_home.png) | ![](docs/nettools/02_categories.png) | ![](docs/nettools/04_command.png) | ![](docs/nettools/05_template.png) |
| **Szukanie skrótami** | **Generator** | **Ping** | **Skaner LAN** |
| ![](docs/nettools/06_search.png) | ![](docs/nettools/07_generator.png) | ![](docs/nettools/09_ping.png) | ![](docs/nettools/12_lan.png) |

### Komendy Cisco IOS (offline)

Ponad 300 poleceń w 21 kategoriach: podstawy i tryby, show/diagnostyka, interfejsy, DHCP/DNS,
routing statyczny, OSPF, EIGRP, BGP, VLAN i trunk, routing między VLAN, STP, EtherChannel, ACL,
NAT/PAT, hasła i SSH, bezpieczeństwo L2 (port-security, DHCP snooping, DAI), HSRP/VRRP,
NTP/Syslog/SNMP/CDP/LLDP, IPv6, pliki i IOS oraz **gotowe konfiguracje** (SSH, router-on-a-stick,
SVI, DHCP, PAT, OSPF, bezpieczny port access, trunk, LACP, HSRP, odzyskiwanie hasła).

Każda komenda ma tryb (prompt, np. `(config-if)#`), opis po polsku, przykład i wskazówki.

- **Szukanie** klawiaturą lub głosem – rozumie skróty CLI (`sh ip int br`, `conf t`) i polskie opisy
  bez ogonków („trasa domyslna”).
- **Ulubione** – gwiazdka przy komendzie.
- **Generator komend** – wpisujesz sieć (np. `192.168.10.0/24`), dostajesz gotowe linie:
  `ip address`, OSPF/EIGRP z wildcard, ACL (z `host`/`any`), pulę DHCP, PAT, `ip route`, BGP, prefix-list.

### Narzędzia sieciowe

- **Moja sieć** – typ połączenia, SSID, sygnał, IP/maska, brama, DNS, IPv6, przełącznik **„Wymuś Wi-Fi”**.
- **Ping** – ICMP (systemowy ping z TTL) lub TCP (czas połączenia z portem), statystyki i wykres RTT.
- **Skaner portów** – popularne, sprzęt sieciowy (SSH, Telnet, SNMP, BGP, NETCONF…), WWW, 1–1024 lub jeden port.
- **DNS lookup** – rekordy A/AAAA i odwrotne PTR.
- **Skaner LAN** – wykrywa urządzenia w sieci Wi-Fi (do /24), pokazuje bramę, nazwy i otwarte porty;
  dotknięcie hosta → ping / porty / PTR.
- **Wake-on-LAN** – budzenie komputerów (MAC w każdym formacie, także Cisco `aabb.ccdd.eeff`).

> Zegarek połączony z telefonem korzysta z internetu telefonu (Bluetooth). Narzędzia LAN same
> włączają Wi-Fi i przypinają do niego aplikację; Wi-Fi musi być włączone w ustawieniach zegarka.

## Budowanie

Wymagania: JDK 17+, Android SDK (platforma 35).

```bash
./gradlew :app:assembleRelease       # VLSM:     app/build/outputs/apk/release/app-release.apk
./gradlew :nettools:assembleRelease  # NetTools: nettools/build/outputs/apk/release/nettools-release.apk
./gradlew testDebugUnitTest          # testy logiki + renderowanie wszystkich ekranów (Robolectric)
```

Zrzuty ekranów z testów trafiają do `app/build/screenshots/` i `nettools/build/screenshots/`.

## Struktura

```
common/     # wspólne: logika IPv4 (Ip, Cidr, VLSM, FLSM, sumaryzacja), motyw, klawiatura IP, bezel
app/        # aplikacja VLSM: ekrany, zapis projektów
nettools/   # aplikacja NetTools
├── ios/    # baza komend Cisco IOS, wyszukiwarka skrótów, generator komend
├── net/    # sondy TCP, skaner, ping, MAC/Wake-on-LAN, wybór sieci Wi-Fi
└── ui/     # ekrany
```

Min. Wear OS 3 (API 30) – Galaxy Watch 4 i nowsze.
