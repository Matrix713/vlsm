package pl.vlsmcalc.nettools.ios

import pl.vlsmcalc.nettools.ios.Mode.ANY
import pl.vlsmcalc.nettools.ios.Mode.CONF
import pl.vlsmcalc.nettools.ios.Mode.DHCP
import pl.vlsmcalc.nettools.ios.Mode.EXEC
import pl.vlsmcalc.nettools.ios.Mode.EXT_ACL
import pl.vlsmcalc.nettools.ios.Mode.IF
import pl.vlsmcalc.nettools.ios.Mode.LINE
import pl.vlsmcalc.nettools.ios.Mode.ROMMON
import pl.vlsmcalc.nettools.ios.Mode.ROUTER
import pl.vlsmcalc.nettools.ios.Mode.SUBIF
import pl.vlsmcalc.nettools.ios.Mode.USER
import pl.vlsmcalc.nettools.ios.Mode.VLAN

/** Baza poleceń Cisco IOS / IOS-XE (offline). */
object IosDatabase {

    val categories: List<Category> = listOf(
        Category("basics", "Podstawy i tryby", "enable, conf t, zapis, skróty"),
        Category("show", "Show i diagnostyka", "show, ping, traceroute, debug"),
        Category("interfaces", "Interfejsy", "ip address, shutdown, speed, range"),
        Category("dhcp", "DHCP i DNS", "pule, relay, name-server"),
        Category("static", "Routing statyczny", "ip route, trasa domyślna, floating"),
        Category("ospf", "OSPF", "router ospf, area, DR/BDR, koszty"),
        Category("eigrp", "EIGRP", "AS, auto-summary, variance"),
        Category("bgp", "BGP", "neighbor, network, summary"),
        Category("vlan", "VLAN i trunk", "access, trunk, native, VTP"),
        Category("intervlan", "Routing między VLAN", "router-on-a-stick, SVI"),
        Category("stp", "STP", "root, portfast, BPDU guard"),
        Category("etherchannel", "EtherChannel", "LACP, PAgP, port-channel"),
        Category("acl", "ACL", "standardowe, rozszerzone, nazwane"),
        Category("nat", "NAT / PAT", "static, pool, overload"),
        Category("security", "Hasła i SSH", "secret, vty, SSH, login block"),
        Category("l2sec", "Bezpieczeństwo L2", "port-security, DHCP snooping, DAI"),
        Category("fhrp", "HSRP / VRRP", "brama wirtualna, priorytet, preempt"),
        Category("services", "NTP, Syslog, SNMP, CDP", "usługi zarządzania"),
        Category("ipv6", "IPv6", "adresy, SLAAC, OSPFv3, DHCPv6"),
        Category("files", "Pliki i IOS", "flash, TFTP, boot, odzyskiwanie hasła"),
        Category("configs", "Gotowe konfiguracje", "szablony do przepisania"),
    )

    val commands: List<IosCommand> = buildList {
        cat("basics") {
            c("enable", USER, "Tryb uprzywilejowany", "Przejście z trybu użytkownika (>) do trybu EXEC uprzywilejowanego (#).", "en")
            c("disable", EXEC, "Powrót do trybu użytkownika", "Wyjście z trybu # do trybu >.")
            c("configure terminal", EXEC, "Konfiguracja globalna", "Wejście do trybu konfiguracji globalnej.", "conf t")
            c("exit", ANY, "Poziom wyżej", "Wyjście o jeden poziom trybu konfiguracji (np. z config-if do config).")
            c("end", ANY, "Powrót do #", "Powrót z dowolnego trybu konfiguracji do EXEC uprzywilejowanego.", tip = "Skrót klawiszowy: Ctrl+Z")
            c("hostname <nazwa>", CONF, "Nazwa urządzenia", "Ustawia nazwę widoczną w prompcie.", "hostname R1")
            c("do <polecenie EXEC>", CONF, "Polecenie EXEC w konfiguracji", "Wykonuje polecenie trybu # (np. show) bez wychodzenia z konfiguracji.", "do show ip interface brief")
            c("no <polecenie>", ANY, "Negacja / usunięcie", "Usuwa wpis konfiguracji lub odwraca polecenie.", "no shutdown")
            c("default interface <interfejs>", CONF, "Reset interfejsu", "Przywraca domyślną konfigurację interfejsu.", "default interface g0/1")
            c("copy running-config startup-config", EXEC, "Zapis konfiguracji", "Zapisuje bieżącą konfigurację do NVRAM (startup-config).", "copy run start", "Krócej: wr (write memory)")
            c("write memory", EXEC, "Zapis konfiguracji (wr)", "Starszy, krótszy odpowiednik copy run start.", "wr")
            c("show running-config", EXEC, "Bieżąca konfiguracja", "Wyświetla konfigurację w RAM.", "sh run | section interface", "Filtry: | include, | exclude, | begin, | section")
            c("show <...> | include <tekst>", EXEC, "Filtrowanie wyniku", "Pokazuje tylko linie zawierające tekst. Także: exclude, begin, section, count.", "show run | include ip route")
            c("reload", EXEC, "Restart", "Restartuje urządzenie. Niezapisane zmiany zostaną utracone.", "reload in 10", "„reload in <min>” – restart z opóźnieniem (zabezpieczenie przy zdalnej pracy), anuluj: reload cancel")
            c("banner motd #<tekst>#", CONF, "Baner MOTD", "Komunikat wyświetlany przy połączeniu. # to dowolny znak ograniczający.", "banner motd # Tylko autoryzowany dostep #")
            c("no ip domain-lookup", CONF, "Wyłącz DNS dla literówek", "Błędnie wpisane polecenie nie jest traktowane jak nazwa hosta (brak czekania).")
            c("logging synchronous", LINE, "Komunikaty nie przerywają", "Logi nie wcinają się w wpisywane polecenie.", "line con 0\n logging synchronous")
            c("exec-timeout <min> <sek>", LINE, "Czas bezczynności", "Rozłącza sesję po czasie bezczynności. 0 0 = nigdy.", "exec-timeout 10 0")
            c("terminal length 0", EXEC, "Bez --More--", "Wyłącza stronicowanie wyniku w bieżącej sesji.")
            c("alias exec <skrót> <polecenie>", CONF, "Własny skrót", "Tworzy alias polecenia EXEC.", "alias exec sib show ip interface brief")
            c("clock set <hh:mm:ss> <dzień> <miesiąc> <rok>", EXEC, "Ustaw zegar", "Ręczne ustawienie daty i godziny.", "clock set 14:30:00 22 Nov 2026")
            c("?", ANY, "Pomoc kontekstowa", "Lista dostępnych poleceń lub parametrów. „sh ?” lub „sh i?”.", tip = "Tab dopełnia polecenie, polecenia można skracać (sh ip int br).")
        }
        cat("show") {
            c("show ip interface brief", EXEC, "Przegląd interfejsów", "Adresy IP i stan (status/protocol) wszystkich interfejsów.", "sh ip int br")
            c("show interfaces <interfejs>", EXEC, "Szczegóły interfejsu", "Stan, MAC, MTU, BW, prędkość, duplex, błędy, kolizje.", "sh int g0/0")
            c("show interfaces status", EXEC, "Porty przełącznika", "Stan, VLAN, duplex i prędkość portów (switch).")
            c("show interfaces description", EXEC, "Opisy interfejsów", "Stan i opisy wszystkich interfejsów.")
            c("show ip route", EXEC, "Tablica routingu", "Trasy IPv4 z kodami źródeł (C, L, S, O, D, B...).", "sh ip route 10.0.0.0", "Konkretna sieć: show ip route <ip> – pokazuje trasę użytą dla adresu")
            c("show ip protocols", EXEC, "Protokoły routingu", "Aktywne protokoły, sieci, sąsiedzi, AD, pasywne interfejsy.")
            c("show version", EXEC, "Wersja i sprzęt", "Wersja IOS, uptime, plik obrazu, pamięć, config-register.")
            c("show arp", EXEC, "Tablica ARP", "Mapowania IP → MAC.", "show ip arp 192.168.1.10")
            c("show mac address-table", EXEC, "Tablica MAC", "Adresy MAC przypisane do portów i VLAN (switch).", "show mac address-table interface fa0/1", "Również: dynamic, address <mac>, vlan <id>")
            c("show cdp neighbors detail", EXEC, "Sąsiedzi CDP", "Sąsiednie urządzenia Cisco z IP, modelem i portem.")
            c("show logging", EXEC, "Logi", "Bufor logów i ustawienia logowania.")
            c("show processes cpu sorted", EXEC, "Obciążenie CPU", "Procesy posortowane według użycia CPU.", "show processes cpu history")
            c("show interfaces <interfejs> counters errors", EXEC, "Błędy portu", "Liczniki błędów (CRC, FCS, runts) na przełączniku.")
            c("show controllers <serial>", EXEC, "DCE/DTE", "Typ kabla na interfejsie szeregowym i clock rate.", "show controllers s0/0/0")
            c("show users", EXEC, "Zalogowani", "Aktywne sesje (con, vty).")
            c("show clock", EXEC, "Data i godzina", "Bieżący czas urządzenia.")
            c("ping <ip>", EXEC, "Ping", "Test łączności ICMP. ! = odpowiedź, . = timeout, U = unreachable.", "ping 8.8.8.8 source g0/1 repeat 100")
            c("ping", EXEC, "Ping rozszerzony", "Bez parametrów – tryb interaktywny (źródło, rozmiar, DF, powtórzenia).")
            c("traceroute <ip>", EXEC, "Traceroute", "Trasa pakietu hop po hopie.", "traceroute 8.8.8.8 source lo0")
            c("debug ip <protokół>", EXEC, "Debug", "Wyświetla zdarzenia w czasie rzeczywistym. Uwaga na obciążenie CPU!", "debug ip ospf adj")
            c("undebug all", EXEC, "Wyłącz debug", "Wyłącza wszystkie debugi.", "u all")
            c("terminal monitor", EXEC, "Logi w sesji SSH", "Wyświetla logi i debug w sesji vty (SSH/telnet).")
        }
        cat("interfaces") {
            c("interface <typ><numer>", CONF, "Wejście do interfejsu", "Tryb konfiguracji interfejsu.", "interface g0/0")
            c("interface range <zakres>", CONF, "Wiele interfejsów", "Konfiguruje kilka portów naraz.", "interface range fa0/1 - 24, g0/1", "Spacje wokół myślnika są wymagane w starszych IOS")
            c("description <tekst>", IF, "Opis", "Opis interfejsu (widoczny w show).", "description Uplink do R2")
            c("ip address <ip> <maska>", IF, "Adres IPv4", "Przypisuje adres i maskę.", "ip address 192.168.1.1 255.255.255.0")
            c("ip address <ip> <maska> secondary", IF, "Adres dodatkowy", "Dodatkowy adres na tym samym interfejsie.")
            c("ip address dhcp", IF, "Adres z DHCP", "Interfejs pobiera adres z serwera DHCP.")
            c("no shutdown", IF, "Włącz interfejs", "Interfejsy routera są domyślnie wyłączone (administratively down).")
            c("shutdown", IF, "Wyłącz interfejs", "Administracyjnie wyłącza interfejs. shut + no shut resetuje err-disabled.")
            c("speed <10|100|1000|auto>", IF, "Prędkość", "Ustawia prędkość portu.", "speed 100")
            c("duplex <full|half|auto>", IF, "Duplex", "Tryb dupleksu. Niezgodność duplex = kolizje i błędy.", "duplex full")
            c("mdix auto", IF, "Auto MDI-X", "Automatyczne dopasowanie kabla prosty/krosowany.")
            c("clock rate <bps>", IF, "Taktowanie DCE", "Tylko po stronie DCE łącza szeregowego.", "clock rate 64000")
            c("bandwidth <kb/s>", IF, "Pasmo (dla metryk)", "Wpływa na metryki OSPF/EIGRP i QoS – nie zmienia rzeczywistej prędkości.", "bandwidth 10000")
            c("ip mtu <bajty>", IF, "MTU IP", "MTU pakietów IP na interfejsie.", "ip mtu 1400")
            c("interface loopback <n>", CONF, "Loopback", "Logiczny interfejs zawsze w stanie up (router-id, zarządzanie).", "interface loopback 0")
            c("no switchport", IF, "Port routowany (L3)", "Zamienia port przełącznika L3 w port routowany.")
            c("errdisable recovery cause <powód>", CONF, "Auto-odzyskiwanie err-disabled", "Automatycznie włącza porty po błędzie.", "errdisable recovery cause psecure-violation", "Interwał: errdisable recovery interval <s>")
            c("show interfaces <if> switchport", EXEC, "Tryb portu L2", "Tryb access/trunk, VLAN, native VLAN.")
        }
        cat("dhcp") {
            c("ip dhcp excluded-address <od> [do]", CONF, "Wykluczone adresy", "Adresy, których DHCP nie przydzieli (bramy, serwery). Wpisz przed pulą.", "ip dhcp excluded-address 192.168.1.1 192.168.1.10")
            c("ip dhcp pool <nazwa>", CONF, "Pula DHCP", "Tworzy pulę i przechodzi do jej konfiguracji.", "ip dhcp pool LAN")
            c("network <sieć> <maska>", DHCP, "Sieć puli", "Zakres adresów do przydzielania.", "network 192.168.1.0 255.255.255.0")
            c("default-router <ip>", DHCP, "Brama domyślna", "Brama przekazywana klientom.", "default-router 192.168.1.1")
            c("dns-server <ip> [ip2]", DHCP, "Serwery DNS", "DNS przekazywane klientom.", "dns-server 8.8.8.8 1.1.1.1")
            c("domain-name <domena>", DHCP, "Domena", "Sufiks domeny dla klientów.", "domain-name firma.local")
            c("lease <dni> [godz] [min]", DHCP, "Czas dzierżawy", "Domyślnie 1 dzień.", "lease 7")
            c("ip helper-address <serwer DHCP>", IF, "DHCP relay", "Przekazuje broadcasty DHCP do serwera w innej sieci. Na interfejsie po stronie klientów.", "ip helper-address 10.0.0.5")
            c("show ip dhcp binding", EXEC, "Przydzielone adresy", "Dzierżawy: IP, MAC, czas wygaśnięcia.")
            c("show ip dhcp pool", EXEC, "Stan pul", "Wykorzystanie pul DHCP.")
            c("show ip dhcp conflict", EXEC, "Konflikty", "Adresy wykryte jako zajęte.")
            c("clear ip dhcp binding *", EXEC, "Wyczyść dzierżawy", "Usuwa wszystkie dzierżawy.")
            c("service dhcp", CONF, "Włącz usługę DHCP", "Domyślnie włączona; no service dhcp wyłącza serwer i relay.")
            c("ip name-server <ip>", CONF, "Serwer DNS routera", "DNS używany przez samo urządzenie.", "ip name-server 8.8.8.8")
            c("ip domain-lookup", CONF, "Włącz rozwiązywanie nazw", "Pozwala używać nazw w ping/telnet.")
            c("ip host <nazwa> <ip>", CONF, "Statyczny wpis DNS", "Lokalna mapa nazwa → IP.", "ip host R2 10.0.0.2")
        }
        cat("static") {
            c("ip route <sieć> <maska> <next-hop>", CONF, "Trasa statyczna", "Trasa przez adres następnego skoku.", "ip route 10.2.0.0 255.255.0.0 192.168.12.2")
            c("ip route <sieć> <maska> <interfejs>", CONF, "Trasa przez interfejs", "Trasa bezpośrednio podłączona – zalecana tylko dla łączy punkt-punkt.", "ip route 10.2.0.0 255.255.0.0 s0/0/0")
            c("ip route <sieć> <maska> <interfejs> <next-hop>", CONF, "Trasa w pełni określona", "Interfejs + next-hop: zalecane na Ethernecie.", "ip route 10.2.0.0 255.255.0.0 g0/1 192.168.12.2")
            c("ip route 0.0.0.0 0.0.0.0 <next-hop>", CONF, "Trasa domyślna", "Gateway of last resort.", "ip route 0.0.0.0 0.0.0.0 203.0.113.1")
            c("ip route <sieć> <maska> <next-hop> <AD>", CONF, "Trasa zapasowa (floating)", "Wyższy dystans administracyjny – używana, gdy główna zniknie.", "ip route 0.0.0.0 0.0.0.0 10.9.9.1 200")
            c("ip route <sieć> <maska> null0", CONF, "Blackhole / sumaryczna", "Odrzuca ruch (np. dla trasy sumarycznej, zapobiega pętlom).")
            c("ip routing", CONF, "Routing na przełączniku L3", "Włącza routing IPv4 (multilayer switch).")
            c("show ip route static", EXEC, "Trasy statyczne", "Tylko trasy S.")
            c("ip route ... track <obiekt>", CONF, "Trasa z IP SLA", "Trasa aktywna tylko gdy obiekt śledzenia jest up.", "ip route 0.0.0.0 0.0.0.0 203.0.113.1 track 1", "Najpierw: ip sla 1 / icmp-echo 8.8.8.8 / ip sla schedule 1 start-time now life forever / track 1 ip sla 1 reachability")
        }
        cat("ospf") {
            c("router ospf <process-id>", CONF, "Proces OSPF", "Uruchamia OSPF. ID procesu jest lokalne.", "router ospf 1")
            c("router-id <a.b.c.d>", ROUTER, "Router ID", "Ręczny identyfikator routera. Zmiana wymaga clear ip ospf process.", "router-id 1.1.1.1")
            c("network <sieć> <wildcard> area <nr>", ROUTER, "Włącz OSPF w sieci", "Interfejsy pasujące do sieci/wildcard dołączają do obszaru.", "network 192.168.1.0 0.0.0.255 area 0", "Wildcard = 255.255.255.255 − maska. Dokładny interfejs: <ip> 0.0.0.0")
            c("ip ospf <process-id> area <nr>", IF, "OSPF na interfejsie", "Alternatywa dla network – włącza OSPF bezpośrednio na interfejsie.", "ip ospf 1 area 0")
            c("passive-interface <interfejs>", ROUTER, "Interfejs pasywny", "Ogłasza sieć, ale nie wysyła hello (np. w stronę LAN).", "passive-interface g0/1")
            c("passive-interface default", ROUTER, "Wszystkie pasywne", "Wszystkie interfejsy pasywne; wyjątki: no passive-interface <if>.")
            c("ip ospf cost <koszt>", IF, "Koszt interfejsu", "Ręczny koszt (1–65535).", "ip ospf cost 10")
            c("auto-cost reference-bandwidth <Mb/s>", ROUTER, "Pasmo referencyjne", "Domyślnie 100 Mb/s – zwiększ, by odróżnić łącza 1G/10G. Ustaw identycznie na wszystkich routerach.", "auto-cost reference-bandwidth 10000")
            c("ip ospf priority <0-255>", IF, "Priorytet DR", "Wyższy wygrywa wybór DR. 0 = nigdy DR/BDR.", "ip ospf priority 0")
            c("ip ospf network point-to-point", IF, "Sieć punkt-punkt", "Bez wyboru DR/BDR na łączu Ethernet między dwoma routerami.")
            c("ip ospf hello-interval <s>", IF, "Hello", "Musi być zgodny z sąsiadem (domyślnie 10 s).", "ip ospf hello-interval 5")
            c("ip ospf dead-interval <s>", IF, "Dead", "Musi być zgodny z sąsiadem (domyślnie 4× hello).", "ip ospf dead-interval 20")
            c("default-information originate", ROUTER, "Rozgłoś trasę domyślną", "Ogłasza 0.0.0.0/0 do OSPF (musi istnieć w tablicy, chyba że dodasz always).")
            c("area <nr> range <sieć> <maska>", ROUTER, "Sumaryzacja na ABR", "Sumaryzuje sieci obszaru między obszarami.", "area 1 range 10.1.0.0 255.255.0.0")
            c("ip ospf message-digest-key <id> md5 <klucz>", IF, "Uwierzytelnianie MD5", "Razem z: ip ospf authentication message-digest.", "ip ospf message-digest-key 1 md5 Tajne123")
            c("clear ip ospf process", EXEC, "Restart OSPF", "Resetuje sąsiedztwa (np. po zmianie router-id).")
            c("show ip ospf neighbor", EXEC, "Sąsiedzi OSPF", "Stan sąsiedztwa (FULL, 2WAY), DR/BDR.")
            c("show ip ospf interface brief", EXEC, "Interfejsy OSPF", "Obszar, koszt, stan, liczba sąsiadów.")
            c("show ip ospf database", EXEC, "LSDB", "Baza stanu łącza (LSA).")
            c("show ip route ospf", EXEC, "Trasy OSPF", "Tylko trasy O, O IA, O E2.")
        }
        cat("eigrp") {
            c("router eigrp <AS>", CONF, "Proces EIGRP", "Numer AS musi być zgodny u sąsiadów.", "router eigrp 100")
            c("network <sieć> [wildcard]", ROUTER, "Włącz EIGRP w sieci", "Bez wildcard – sieć klasowa.", "network 192.168.1.0 0.0.0.255")
            c("no auto-summary", ROUTER, "Wyłącz auto-sumaryzację", "Konieczne przy sieciach nieciągłych (domyślnie wyłączone od IOS 15).")
            c("eigrp router-id <a.b.c.d>", ROUTER, "Router ID", "Ręczny identyfikator EIGRP.", "eigrp router-id 1.1.1.1")
            c("passive-interface <interfejs>", ROUTER, "Interfejs pasywny", "Bez hello na interfejsie.", "passive-interface g0/1")
            c("variance <mnożnik>", ROUTER, "Nierówne równoważenie", "Równoważenie obciążenia na trasach o gorszej metryce (feasible successors).", "variance 2")
            c("ip summary-address eigrp <AS> <sieć> <maska>", IF, "Sumaryzacja", "Ogłasza trasę sumaryczną na interfejsie.", "ip summary-address eigrp 100 10.0.0.0 255.255.252.0")
            c("redistribute static", ROUTER, "Redystrybucja statycznych", "Ogłasza trasy statyczne w EIGRP.")
            c("show ip eigrp neighbors", EXEC, "Sąsiedzi EIGRP", "Lista sąsiadów, hold time, uptime.")
            c("show ip eigrp topology", EXEC, "Tabela topologii", "Successor i feasible successor (FD/RD).")
            c("show ip route eigrp", EXEC, "Trasy EIGRP", "Trasy D i D EX.")
        }
        cat("bgp") {
            c("router bgp <AS>", CONF, "Proces BGP", "Uruchamia BGP dla własnego AS.", "router bgp 65001")
            c("bgp router-id <a.b.c.d>", ROUTER, "Router ID", "Identyfikator BGP.", "bgp router-id 1.1.1.1")
            c("neighbor <ip> remote-as <AS>", ROUTER, "Sąsiad", "Ten sam AS = iBGP, inny = eBGP.", "neighbor 203.0.113.1 remote-as 65002")
            c("network <sieć> mask <maska>", ROUTER, "Ogłoś sieć", "Sieć musi istnieć w tablicy routingu z dokładnie tą maską.", "network 198.51.100.0 mask 255.255.255.0", "Brak trasy? dodaj ip route <sieć> <maska> null0")
            c("neighbor <ip> update-source loopback0", ROUTER, "Źródło sesji", "Sesja z adresu loopback (typowe dla iBGP).")
            c("neighbor <ip> ebgp-multihop <ttl>", ROUTER, "eBGP multihop", "Sąsiad eBGP nie jest bezpośrednio podłączony.", "neighbor 10.0.0.2 ebgp-multihop 2")
            c("neighbor <ip> next-hop-self", ROUTER, "Next-hop-self", "iBGP: ustawia siebie jako next-hop dla tras z eBGP.")
            c("show ip bgp summary", EXEC, "Stan sesji", "Sąsiedzi, stan (Established = liczba prefiksów).")
            c("show ip bgp", EXEC, "Tablica BGP", "Prefiksy, next-hop, atrybuty, najlepsza ścieżka (>).")
            c("clear ip bgp * soft", EXEC, "Miękki reset", "Ponowne przetworzenie polityk bez zrywania sesji.")
        }
        cat("vlan") {
            c("vlan <id>", CONF, "Utwórz VLAN", "Tworzy VLAN i wchodzi do jego konfiguracji.", "vlan 10")
            c("name <nazwa>", VLAN, "Nazwa VLAN", "Opisowa nazwa VLAN.", "name KSIEGOWOSC")
            c("switchport mode access", IF, "Port dostępowy", "Port należy do jednego VLAN (dla hostów).")
            c("switchport access vlan <id>", IF, "VLAN portu", "Przypisuje port do VLAN (tworzy VLAN, jeśli nie istnieje).", "switchport access vlan 10")
            c("switchport mode trunk", IF, "Trunk", "Port przenosi wiele VLAN (tagi 802.1Q).")
            c("switchport trunk encapsulation dot1q", IF, "Enkapsulacja trunk", "Wymagane przed mode trunk na przełącznikach obsługujących ISL (np. 3560).")
            c("switchport trunk allowed vlan <lista>", IF, "Dozwolone VLAN", "Ogranicza VLAN na trunku.", "switchport trunk allowed vlan 10,20,30", "Dodawanie bez nadpisania: allowed vlan add 40")
            c("switchport trunk native vlan <id>", IF, "Native VLAN", "VLAN bez tagu. Musi być zgodny po obu stronach.", "switchport trunk native vlan 99")
            c("switchport nonegotiate", IF, "Wyłącz DTP", "Port nie wysyła ramek DTP (bezpieczeństwo).")
            c("switchport voice vlan <id>", IF, "VLAN głosowy", "VLAN dla telefonu IP na porcie access.", "switchport voice vlan 150")
            c("no vlan <id>", CONF, "Usuń VLAN", "Porty z tego VLAN przestaną działać – przenieś je wcześniej.")
            c("show vlan brief", EXEC, "Lista VLAN", "VLAN-y i przypisane porty access.")
            c("show interfaces trunk", EXEC, "Trunki", "Porty trunk, native VLAN, dozwolone i aktywne VLAN.")
            c("vtp mode <server|client|transparent>", CONF, "Tryb VTP", "Transparent – lokalne VLAN-y, bez synchronizacji.", "vtp mode transparent")
            c("vtp domain <nazwa>", CONF, "Domena VTP", "Musi być zgodna w obrębie domeny.")
            c("show vtp status", EXEC, "Stan VTP", "Tryb, domena, revision number.")
            c("delete flash:vlan.dat", EXEC, "Usuń bazę VLAN", "Kasuje VLAN-y (z erase startup-config i reload = pełny reset switcha).")
        }
        cat("intervlan") {
            c("interface <if>.<vlan>", CONF, "Podinterfejs", "Router-on-a-stick: jeden podinterfejs na VLAN.", "interface g0/0.10")
            c("encapsulation dot1Q <vlan> [native]", SUBIF, "Tag VLAN", "Wiąże podinterfejs z VLAN. Wpisz przed ip address.", "encapsulation dot1Q 10")
            c("ip address <ip> <maska>", SUBIF, "Brama VLAN", "Adres bramy dla hostów w VLAN.", "ip address 192.168.10.1 255.255.255.0")
            c("no shutdown", IF, "Włącz interfejs fizyczny", "Podinterfejsy działają tylko, gdy interfejs fizyczny jest włączony.")
            c("ip routing", CONF, "Routing na switchu L3", "Wymagane dla SVI na przełączniku wielowarstwowym.")
            c("interface vlan <id>", CONF, "SVI", "Wirtualny interfejs L3 dla VLAN.", "interface vlan 10")
            c("no switchport", IF, "Port routowany", "Port L3 na przełączniku (np. uplink do routera).")
            c("show ip interface brief | include Vlan", EXEC, "Stan SVI", "SVI jest up, gdy VLAN istnieje i ma aktywny port.")
        }
        cat("stp") {
            c("spanning-tree mode <pvst|rapid-pvst|mst>", CONF, "Tryb STP", "Rapid PVST+ zalecany – szybka zbieżność.", "spanning-tree mode rapid-pvst")
            c("spanning-tree vlan <id> root primary", CONF, "Root bridge", "Ustawia priorytet, by przełącznik został rootem.", "spanning-tree vlan 1-100 root primary", "Zapasowy root: root secondary")
            c("spanning-tree vlan <id> priority <0-61440>", CONF, "Priorytet mostu", "Wielokrotność 4096. Niższy wygrywa.", "spanning-tree vlan 10 priority 4096")
            c("spanning-tree portfast", IF, "PortFast", "Port access od razu w forwarding (tylko porty do hostów!).")
            c("spanning-tree portfast default", CONF, "PortFast globalnie", "PortFast na wszystkich portach access.")
            c("spanning-tree bpduguard enable", IF, "BPDU Guard", "Wyłącza port (err-disabled) po odebraniu BPDU.")
            c("spanning-tree portfast bpduguard default", CONF, "BPDU Guard globalnie", "BPDU Guard na wszystkich portach z PortFast.")
            c("spanning-tree guard root", IF, "Root Guard", "Port nie może prowadzić do nowego roota.")
            c("spanning-tree vlan <id> cost <koszt>", IF, "Koszt portu", "Wpływa na wybór root portu.", "spanning-tree vlan 10 cost 4")
            c("spanning-tree vlan <id> port-priority <0-240>", IF, "Priorytet portu", "Wielokrotność 16; niższy preferowany.")
            c("show spanning-tree", EXEC, "Stan STP", "Root, role portów (Root/Desg/Altn), stany.", "show spanning-tree vlan 10")
            c("show spanning-tree summary", EXEC, "Podsumowanie STP", "Tryb, PortFast, BPDU Guard, liczniki portów.")
        }
        cat("etherchannel") {
            c("channel-group <nr> mode active", IF, "LACP aktywny", "Inicjuje negocjację LACP (802.3ad). active+active lub active+passive.", "channel-group 1 mode active")
            c("channel-group <nr> mode passive", IF, "LACP pasywny", "Czeka na drugą stronę.")
            c("channel-group <nr> mode desirable", IF, "PAgP", "Cisco PAgP. desirable+desirable lub desirable+auto.")
            c("channel-group <nr> mode on", IF, "Statyczny", "Bez negocjacji – obie strony muszą mieć on.")
            c("interface port-channel <nr>", CONF, "Interfejs Port-channel", "Konfiguracja trunk/access dla całej grupy.", "interface port-channel 1")
            c("port-channel load-balance <metoda>", CONF, "Równoważenie", "Sposób rozkładania ruchu.", "port-channel load-balance src-dst-ip")
            c("show etherchannel summary", EXEC, "Stan EtherChannel", "Flagi: P = w grupie, SU = L2 w użyciu, s = zawieszony.")
            c("show etherchannel port-channel", EXEC, "Szczegóły grupy", "Protokół i porty członkowskie.")
        }
        cat("acl") {
            c("access-list <1-99> <permit|deny> <źródło> <wildcard>", CONF, "ACL standardowa", "Filtruje tylko po adresie źródłowym. Umieszczaj blisko celu.", "access-list 10 permit 192.168.1.0 0.0.0.255")
            c("access-list <100-199> <permit|deny> <protokół> <źródło> <wc> <cel> <wc> [eq <port>]", CONF, "ACL rozszerzona", "Źródło, cel, protokół, porty. Umieszczaj blisko źródła.", "access-list 100 permit tcp 192.168.1.0 0.0.0.255 any eq 443")
            c("ip access-list standard <nazwa>", CONF, "Nazwana standardowa", "Tworzy nazwaną ACL standardową.", "ip access-list standard ADMIN")
            c("ip access-list extended <nazwa>", CONF, "Nazwana rozszerzona", "Tworzy nazwaną ACL rozszerzoną.", "ip access-list extended WEB")
            c("[<sekwencja>] <permit|deny> ...", EXT_ACL, "Wpis z numerem", "Numer pozwala wstawić wpis w środek listy. no <nr> usuwa wpis.", "15 deny ip host 10.0.0.5 any")
            c("remark <opis>", EXT_ACL, "Komentarz", "Opis w ACL.", "remark Blokada gosci")
            c("ip access-group <ACL> <in|out>", IF, "Przypisz ACL do interfejsu", "Jedna ACL na kierunek i protokół.", "ip access-group 100 in")
            c("access-class <ACL> in", LINE, "ACL na vty", "Ogranicza, kto może się logować przez SSH/telnet.", "line vty 0 15\n access-class 10 in")
            c("host <ip> / any", EXT_ACL, "Skróty adresów", "host 10.0.0.1 = 10.0.0.1 0.0.0.0; any = 0.0.0.0 255.255.255.255.")
            c("... eq|neq|gt|lt|range <port>", EXT_ACL, "Operatory portów", "eq 80, gt 1023, range 20 21. Nazwy: www, ssh, telnet, domain.", "permit tcp any host 10.0.0.10 eq www")
            c("... established", EXT_ACL, "Ruch powrotny TCP", "Pakiety TCP z flagą ACK/RST (odpowiedzi na połączenia wychodzące).", "permit tcp any 192.168.1.0 0.0.0.255 established")
            c("... log", EXT_ACL, "Logowanie trafień", "Wpis do logów przy dopasowaniu.", "deny ip any any log")
            c("ip access-list resequence <nazwa> <start> <krok>", CONF, "Przenumeruj", "Porządkuje numery sekwencji.", "ip access-list resequence WEB 10 10")
            c("show access-lists", EXEC, "Pokaż ACL", "Wpisy i liczniki trafień (matches).", "show ip access-lists WEB")
            c("show ip interface <if> | include access list", EXEC, "ACL na interfejsie", "Które ACL są przypisane do interfejsu.")
            c("clear access-list counters", EXEC, "Zeruj liczniki", "Zeruje liczniki trafień.")
            c("ipv6 traffic-filter <nazwa> <in|out>", IF, "ACL IPv6 na interfejsie", "ACL IPv6 tworzy się przez ipv6 access-list <nazwa>.")
        }
        cat("nat") {
            c("ip nat inside", IF, "Strona wewnętrzna", "Interfejs w stronę LAN.")
            c("ip nat outside", IF, "Strona zewnętrzna", "Interfejs w stronę Internetu/ISP.")
            c("ip nat inside source static <lokalny> <globalny>", CONF, "NAT statyczny 1:1", "Stałe mapowanie (np. serwer).", "ip nat inside source static 192.168.1.10 203.0.113.10")
            c("ip nat inside source static tcp <lokalny> <port> <globalny> <port>", CONF, "Przekierowanie portu", "Port forwarding.", "ip nat inside source static tcp 192.168.1.10 80 203.0.113.2 8080")
            c("ip nat pool <nazwa> <od> <do> netmask <maska>", CONF, "Pula adresów", "Publiczne adresy dla NAT dynamicznego.", "ip nat pool PULA 203.0.113.10 203.0.113.20 netmask 255.255.255.0")
            c("ip nat inside source list <ACL> pool <nazwa>", CONF, "NAT dynamiczny", "Adresy z ACL tłumaczone na pulę.", "ip nat inside source list 1 pool PULA", "Dodaj overload na końcu, by używać PAT na puli")
            c("ip nat inside source list <ACL> interface <if> overload", CONF, "PAT (overload)", "Wiele hostów na jednym adresie interfejsu zewnętrznego.", "ip nat inside source list 1 interface g0/0 overload")
            c("access-list <nr> permit <sieć> <wildcard>", CONF, "Ruch do translacji", "ACL określa adresy wewnętrzne do NAT.", "access-list 1 permit 192.168.1.0 0.0.0.255")
            c("show ip nat translations", EXEC, "Translacje", "Inside local/global, outside local/global.")
            c("show ip nat statistics", EXEC, "Statystyki NAT", "Interfejsy, liczba translacji, trafienia.")
            c("clear ip nat translation *", EXEC, "Wyczyść translacje", "Usuwa dynamiczne translacje.")
            c("debug ip nat", EXEC, "Debug NAT", "Podgląd translacji na żywo.")
        }
        cat("security") {
            c("enable secret <hasło>", CONF, "Hasło enable", "Hasło do trybu # (hash). Ma pierwszeństwo przed enable password.", "enable secret Cisco123!")
            c("service password-encryption", CONF, "Szyfruj hasła", "Szyfruje hasła zapisane jawnie (typ 7 – słabe, ale ukrywa przed wzrokiem).")
            c("security passwords min-length <n>", CONF, "Minimalna długość", "Minimalna długość nowych haseł.", "security passwords min-length 10")
            c("username <user> privilege 15 secret <hasło>", CONF, "Konto lokalne", "Użytkownik do login local / SSH.", "username admin privilege 15 secret Admin123!")
            c("line console 0", CONF, "Linia konsoli", "Konfiguracja portu konsoli.")
            c("line vty 0 15", CONF, "Linie zdalne", "Sesje SSH/telnet (0 4 na starszych urządzeniach).")
            c("password <hasło>", LINE, "Hasło linii", "Wymaga też polecenia login.", "password cisco")
            c("login", LINE, "Wymagaj hasła linii", "Używa hasła z polecenia password.")
            c("login local", LINE, "Logowanie kontami lokalnymi", "Użytkownik i hasło z username.")
            c("transport input ssh", LINE, "Tylko SSH", "Blokuje telnet na vty.")
            c("ip domain-name <domena>", CONF, "Domena (do SSH)", "Wymagana do wygenerowania kluczy RSA.", "ip domain-name lab.local")
            c("crypto key generate rsa modulus <bity>", CONF, "Klucze RSA", "Generuje klucze dla SSH. Min. 768 dla SSHv2, zalecane 2048.", "crypto key generate rsa modulus 2048")
            c("ip ssh version 2", CONF, "SSH v2", "Wymusza SSH w wersji 2.")
            c("ip ssh time-out <s>", CONF, "Timeout SSH", "Czas na zalogowanie.", "ip ssh time-out 60")
            c("ip ssh authentication-retries <n>", CONF, "Próby logowania", "Liczba prób w jednej sesji.", "ip ssh authentication-retries 3")
            c("login block-for <s> attempts <n> within <s>", CONF, "Blokada brute-force", "Blokuje logowanie po nieudanych próbach.", "login block-for 120 attempts 3 within 60")
            c("no ip http server", CONF, "Wyłącz HTTP", "Wyłącza serwer WWW (także: no ip http secure-server).")
            c("show ip ssh", EXEC, "Stan SSH", "Wersja i parametry SSH.")
            c("show ssh", EXEC, "Sesje SSH", "Aktywne połączenia SSH.")
        }
        cat("l2sec") {
            c("switchport port-security", IF, "Włącz port security", "Wymaga trybu access (switchport mode access).")
            c("switchport port-security maximum <n>", IF, "Limit MAC", "Maksymalna liczba adresów MAC (domyślnie 1).", "switchport port-security maximum 2")
            c("switchport port-security mac-address sticky", IF, "Sticky MAC", "Uczy się adresów i zapisuje je w running-config.")
            c("switchport port-security mac-address <mac>", IF, "Statyczny MAC", "Ręcznie dozwolony adres.", "switchport port-security mac-address 0050.7966.6800")
            c("switchport port-security violation <protect|restrict|shutdown>", IF, "Reakcja na naruszenie", "shutdown (domyślne) = err-disabled; restrict = odrzuca i loguje; protect = tylko odrzuca.", "switchport port-security violation restrict")
            c("switchport port-security aging time <min>", IF, "Starzenie wpisów", "Czas po którym MAC jest usuwany.", "switchport port-security aging time 10")
            c("show port-security", EXEC, "Stan port security", "Porty, limity, liczba naruszeń.", "show port-security interface fa0/1")
            c("show port-security address", EXEC, "Bezpieczne MAC", "Nauczone i statyczne adresy.")
            c("ip dhcp snooping", CONF, "DHCP snooping", "Ochrona przed fałszywymi serwerami DHCP.")
            c("ip dhcp snooping vlan <lista>", CONF, "Snooping w VLAN", "VLAN-y objęte ochroną.", "ip dhcp snooping vlan 10,20")
            c("ip dhcp snooping trust", IF, "Port zaufany", "Port w stronę prawdziwego serwera DHCP / uplink.")
            c("ip dhcp snooping limit rate <pps>", IF, "Limit DHCP", "Ochrona przed zalewaniem DHCP (starvation).", "ip dhcp snooping limit rate 10")
            c("no ip dhcp snooping information option", CONF, "Bez opcji 82", "Często potrzebne, gdy serwer DHCP odrzuca pakiety z opcją 82.")
            c("show ip dhcp snooping binding", EXEC, "Tablica snoopingu", "MAC, IP, VLAN, port klientów.")
            c("ip arp inspection vlan <lista>", CONF, "Dynamic ARP Inspection", "Weryfikuje ARP z tablicą DHCP snooping.", "ip arp inspection vlan 10")
            c("ip arp inspection trust", IF, "DAI – port zaufany", "Uplinki i porty do routerów.")
        }
        cat("fhrp") {
            c("standby <grupa> ip <wirtualny IP>", IF, "HSRP – brama wirtualna", "Wirtualny adres bramy dla hostów.", "standby 1 ip 192.168.1.254")
            c("standby <grupa> priority <0-255>", IF, "Priorytet HSRP", "Wyższy = aktywny (domyślnie 100).", "standby 1 priority 110")
            c("standby <grupa> preempt", IF, "Preempt", "Router o wyższym priorytecie odzyskuje rolę aktywnego.")
            c("standby version 2", IF, "HSRP v2", "Większy zakres grup, obsługa IPv6. Zgodny po obu stronach.")
            c("standby <grupa> track <obiekt> decrement <n>", IF, "Śledzenie", "Obniża priorytet, gdy obiekt (np. uplink) padnie.", "standby 1 track 1 decrement 20", "Obiekt: track 1 interface g0/0 line-protocol")
            c("show standby brief", EXEC, "Stan HSRP", "Grupa, priorytet, stan (Active/Standby), VIP.")
            c("vrrp <grupa> ip <wirtualny IP>", IF, "VRRP (standard)", "Otwarty odpowiednik HSRP. W IOS-XE: vrrp <grupa> address-family ipv4.", "vrrp 1 ip 192.168.1.254")
            c("show vrrp brief", EXEC, "Stan VRRP", "Grupy i stany VRRP.")
        }
        cat("services") {
            c("ntp server <ip>", CONF, "Serwer NTP", "Synchronizacja czasu.", "ntp server 192.168.1.5")
            c("ntp master <stratum>", CONF, "Router jako serwer NTP", "Urządzenie staje się źródłem czasu.", "ntp master 3")
            c("clock timezone <nazwa> <przesunięcie>", CONF, "Strefa czasowa", "Przesunięcie względem UTC.", "clock timezone CET 1")
            c("clock summer-time <nazwa> recurring last Sun Mar 2:00 last Sun Oct 3:00", CONF, "Czas letni (UE)", "Automatyczna zmiana czasu.", "clock summer-time CEST recurring last Sun Mar 2:00 last Sun Oct 3:00")
            c("show ntp status", EXEC, "Stan NTP", "Czy zegar jest zsynchronizowany.", "show ntp associations")
            c("logging host <ip>", CONF, "Serwer Syslog", "Wysyła logi na serwer.", "logging host 192.168.1.20")
            c("logging trap <poziom>", CONF, "Poziom logów", "0 emergencies … 7 debugging.", "logging trap informational")
            c("service timestamps log datetime msec", CONF, "Znaczniki czasu", "Data i milisekundy w logach.")
            c("snmp-server community <nazwa> ro", CONF, "SNMP community", "Dostęp tylko do odczytu (ro) lub zapisu (rw).", "snmp-server community MONITOR ro 10")
            c("snmp-server location <tekst>", CONF, "Lokalizacja SNMP", "Opis lokalizacji urządzenia.")
            c("snmp-server host <ip> version 2c <community>", CONF, "Odbiorca trapów", "Serwer otrzymujący SNMP trap.")
            c("cdp run", CONF, "CDP globalnie", "Włącza/wyłącza (no cdp run) CDP.")
            c("no cdp enable", IF, "CDP na porcie", "Wyłącza CDP na interfejsie (np. do Internetu).")
            c("show cdp neighbors", EXEC, "Sąsiedzi CDP", "Urządzenie, port lokalny i zdalny, platforma.", "show cdp neighbors detail")
            c("lldp run", CONF, "LLDP", "Standardowy odpowiednik CDP (domyślnie wyłączony).")
            c("show lldp neighbors", EXEC, "Sąsiedzi LLDP", "Sąsiednie urządzenia LLDP.", "show lldp neighbors detail")
        }
        cat("ipv6") {
            c("ipv6 unicast-routing", CONF, "Włącz routing IPv6", "Bez tego router nie przekazuje IPv6 ani nie wysyła RA.")
            c("ipv6 address <adres>/<prefiks>", IF, "Adres IPv6", "Adres globalny.", "ipv6 address 2001:db8:1::1/64")
            c("ipv6 address <prefiks>/64 eui-64", IF, "Adres EUI-64", "Część hosta wyliczana z MAC.", "ipv6 address 2001:db8:1::/64 eui-64")
            c("ipv6 address <adres> link-local", IF, "Link-local", "Ręczny adres link-local (czytelniejszy).", "ipv6 address fe80::1 link-local")
            c("ipv6 enable", IF, "Tylko link-local", "Włącza IPv6 z samym adresem link-local.")
            c("ipv6 address autoconfig", IF, "SLAAC", "Adres z ogłoszeń routera (RA).")
            c("ipv6 address dhcp", IF, "Klient DHCPv6", "Adres z serwera DHCPv6.")
            c("ipv6 route <prefiks>/<dł> <next-hop>", CONF, "Trasa statyczna IPv6", "Next-hop: adres globalny lub interfejs + link-local.", "ipv6 route 2001:db8:2::/64 2001:db8:12::2")
            c("ipv6 route ::/0 <next-hop>", CONF, "Trasa domyślna IPv6", "Gateway of last resort dla IPv6.", "ipv6 route ::/0 g0/0 fe80::2")
            c("ipv6 router ospf <pid>", CONF, "OSPFv3", "Proces OSPFv3; ustaw router-id (IPv4!).", "ipv6 router ospf 1\n router-id 1.1.1.1")
            c("ipv6 ospf <pid> area <nr>", IF, "OSPFv3 na interfejsie", "Włącza OSPFv3 na interfejsie.", "ipv6 ospf 1 area 0")
            c("ipv6 dhcp pool <nazwa>", CONF, "Pula DHCPv6", "address prefix, dns-server, domain-name.", "ipv6 dhcp pool LAN6\n address prefix 2001:db8:1::/64\n dns-server 2001:4860:4860::8888")
            c("ipv6 dhcp server <pula>", IF, "Serwer DHCPv6 na porcie", "Przypina pulę do interfejsu.")
            c("ipv6 nd managed-config-flag", IF, "Flaga M (stateful)", "Klienci pobierają adres z DHCPv6.")
            c("ipv6 nd other-config-flag", IF, "Flaga O (stateless)", "SLAAC + DNS z DHCPv6.")
            c("show ipv6 interface brief", EXEC, "Interfejsy IPv6", "Adresy IPv6 i stan.")
            c("show ipv6 route", EXEC, "Tablica routingu IPv6", "Trasy IPv6.")
            c("show ipv6 neighbors", EXEC, "Sąsiedzi (ND)", "Odpowiednik tablicy ARP.")
        }
        cat("files") {
            c("show flash:", EXEC, "Zawartość flash", "Pliki IOS i konfiguracje.", "dir flash:")
            c("show startup-config", EXEC, "Zapisana konfiguracja", "Konfiguracja w NVRAM.")
            c("copy running-config tftp:", EXEC, "Kopia na TFTP", "Backup konfiguracji (pyta o adres i nazwę).")
            c("copy tftp: flash:", EXEC, "IOS z TFTP", "Wgrywanie obrazu IOS na flash.")
            c("copy tftp: running-config", EXEC, "Wczytaj konfigurację", "Scala plik z bieżącą konfiguracją.")
            c("boot system flash:<plik.bin>", CONF, "Obraz startowy", "Wybór obrazu IOS przy uruchomieniu.", "boot system flash:c2900-universalk9-mz.SPA.157-3.M8.bin")
            c("verify /md5 flash:<plik>", EXEC, "Suma kontrolna", "Weryfikacja obrazu po wgraniu.")
            c("erase startup-config", EXEC, "Kasuj konfigurację", "Po reload urządzenie startuje bez konfiguracji.", "write erase")
            c("config-register 0x2102", CONF, "Normalny start", "Domyślny rejestr – wczytuje startup-config.")
            c("confreg 0x2142", ROMMON, "Pomiń startup-config", "Odzyskiwanie hasła: w ROMMON (Ctrl+Break przy starcie), potem reset.", tip = "Po starcie: copy start run, zmień hasła, config-register 0x2102, wr, reload")
        }
        cat("configs") {
            t(
                "Podstawowa konfiguracja", "Nazwa, hasła, baner i konsola – start każdego urządzenia.",
                """
                |hostname R1
                |no ip domain-lookup
                |enable secret Cisco123!
                |service password-encryption
                |banner motd # Tylko autoryzowany dostep #
                |line console 0
                | password cisco
                | login
                | logging synchronous
                | exec-timeout 10 0
                |end
                |copy running-config startup-config
                """,
            )
            t(
                "Dostęp SSH", "Zdalne logowanie SSHv2 kontem lokalnym.",
                """
                |ip domain-name lab.local
                |username admin privilege 15 secret Admin123!
                |crypto key generate rsa modulus 2048
                |ip ssh version 2
                |login block-for 120 attempts 3 within 60
                |line vty 0 15
                | login local
                | transport input ssh
                | exec-timeout 10 0
                """,
            )
            t(
                "Router-on-a-stick", "Routing między VLAN 10 i 20 na jednym porcie routera.",
                """
                |interface g0/0
                | no shutdown
                |interface g0/0.10
                | encapsulation dot1Q 10
                | ip address 192.168.10.1 255.255.255.0
                |interface g0/0.20
                | encapsulation dot1Q 20
                | ip address 192.168.20.1 255.255.255.0
                |! po stronie switcha:
                |interface g0/1
                | switchport mode trunk
                """,
            )
            t(
                "SVI na switchu L3", "Routing między VLAN na przełączniku wielowarstwowym.",
                """
                |ip routing
                |vlan 10
                | name LAN10
                |vlan 20
                | name LAN20
                |interface vlan 10
                | ip address 192.168.10.1 255.255.255.0
                | no shutdown
                |interface vlan 20
                | ip address 192.168.20.1 255.255.255.0
                | no shutdown
                """,
            )
            t(
                "Serwer DHCP", "Pula dla 192.168.1.0/24 z wykluczeniem pierwszych adresów.",
                """
                |ip dhcp excluded-address 192.168.1.1 192.168.1.10
                |ip dhcp pool LAN
                | network 192.168.1.0 255.255.255.0
                | default-router 192.168.1.1
                | dns-server 8.8.8.8 1.1.1.1
                | domain-name lab.local
                | lease 7
                """,
            )
            t(
                "PAT – dostęp do Internetu", "LAN wychodzi na adresie interfejsu WAN.",
                """
                |interface g0/1
                | ip nat inside
                |interface g0/0
                | ip nat outside
                |access-list 1 permit 192.168.1.0 0.0.0.255
                |ip nat inside source list 1 interface g0/0 overload
                |ip route 0.0.0.0 0.0.0.0 203.0.113.1
                """,
            )
            t(
                "OSPF – jeden obszar", "OSPF w obszarze 0 z pasywnym LAN i trasą domyślną.",
                """
                |router ospf 1
                | router-id 1.1.1.1
                | network 10.0.12.0 0.0.0.3 area 0
                | network 192.168.1.0 0.0.0.255 area 0
                | passive-interface g0/1
                | auto-cost reference-bandwidth 10000
                | default-information originate
                """,
            )
            t(
                "Port dostępowy (bezpieczny)", "Access + port security + PortFast + BPDU Guard.",
                """
                |interface range fa0/1 - 20
                | switchport mode access
                | switchport access vlan 10
                | switchport nonegotiate
                | switchport port-security
                | switchport port-security maximum 2
                | switchport port-security mac-address sticky
                | switchport port-security violation restrict
                | spanning-tree portfast
                | spanning-tree bpduguard enable
                """,
            )
            t(
                "Trunk między switchami", "Trunk z native VLAN 99 i listą VLAN.",
                """
                |interface g0/1
                | switchport trunk encapsulation dot1q
                | switchport mode trunk
                | switchport trunk native vlan 99
                | switchport trunk allowed vlan 10,20,99
                | switchport nonegotiate
                """,
            )
            t(
                "EtherChannel LACP", "Dwa porty w Port-channel 1 jako trunk.",
                """
                |interface range g0/1 - 2
                | channel-group 1 mode active
                |interface port-channel 1
                | switchport mode trunk
                | switchport trunk allowed vlan 10,20
                |do show etherchannel summary
                """,
            )
            t(
                "HSRP – para routerów", "R1 aktywny (priorytet 110), R2 z domyślnym 100.",
                """
                |! R1
                |interface g0/1
                | ip address 192.168.1.2 255.255.255.0
                | standby version 2
                | standby 1 ip 192.168.1.1
                | standby 1 priority 110
                | standby 1 preempt
                |! R2
                |interface g0/1
                | ip address 192.168.1.3 255.255.255.0
                | standby version 2
                | standby 1 ip 192.168.1.1
                """,
            )
            t(
                "Nieużywane porty", "VLAN „czarna dziura” i wyłączenie portów.",
                """
                |vlan 999
                | name BLACKHOLE
                |interface range fa0/21 - 24
                | switchport mode access
                | switchport access vlan 999
                | shutdown
                """,
            )
            t(
                "Odzyskiwanie hasła (router)", "Procedura przez ROMMON.",
                """
                |! restart i Ctrl+Break w ciągu 60 s
                |rommon 1 > confreg 0x2142
                |rommon 2 > reset
                |! bez kreatora konfiguracji (no):
                |enable
                |copy startup-config running-config
                |configure terminal
                |enable secret NoweHaslo!
                |config-register 0x2102
                |end
                |copy running-config startup-config
                """,
            )
        }
    }

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }

    fun inCategory(id: String): List<IosCommand> = commands.filter { it.category == id }

    fun byId(id: String): IosCommand? = commands.firstOrNull { it.id == id }
}

private class CatBuilder(val category: String, val out: MutableList<IosCommand>) {
    fun c(syntax: String, mode: Mode, title: String, description: String, example: String? = null, tip: String? = null) {
        out += IosCommand(category, title, syntax, mode, description, example, tip)
    }

    /** Gotowa konfiguracja (szablon wielolinijkowy). */
    fun t(title: String, description: String, body: String) {
        out += IosCommand(category, title, body.trimMargin(), Mode.CONF, description)
    }
}

private fun MutableList<IosCommand>.cat(id: String, block: CatBuilder.() -> Unit) = CatBuilder(id, this).block()
