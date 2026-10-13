# Offene Fahrten

Verwaltung ungenutzter DB-Fahrkarten mit aufgehobener Zugbindung. Alle Daten bleiben auf dem Gerät, es gibt keinen Server und kein Konto.

## Warum die App um ein Verfallsdatum herum gebaut ist

Bei aufgehobener Zugbindung ist die Fahrkarte nicht verbraucht. Sie gilt für eine frei gewählte Verbindung und eine frei gewählte Route, bis zu zwölf Monate nach dem ursprünglichen Reisedatum. Jede ungenutzte Fahrt ist also ein Guthaben mit Ablaufdatum. Die App sortiert nach genau diesem Datum.

## Funktionen

**Erfassung über den bahn.de-Link.** In der DB-App bei der Verbindung auf Teilen tippen, Link einfügen. Der kurze Teilen-Link mit `vbid` wird bei bahn.de aufgelöst, der lange Link aus der Adressleiste direkt gelesen. Daraus kommen Strecke, Datum, Abfahrt, Ankunft, alle Zugnummern, Umstiege und der vollständige Reiseverlauf mit Zwischenhalten. Nur Preis und Auftragsnummer stehen in keinem Link.

Die Verbindung steckt im Recon-String des Links und wird dort im Klartext ausgelesen, nicht über eine Fahrplanabfrage. Deshalb funktioniert es auch für Fahrten, die Monate zurückliegen und die keine Fahrplanauskunft mehr kennt.

Wer die App installiert hat, kann in der DB-App auch direkt auf Teilen und dann auf Offene Fahrten tippen, der Link landet im Linkfenster.

**Texterkennung** bleibt als Rückfallweg für Screenshots vorhanden, liefert bei DB-Layouts aber unzuverlässige Ergebnisse. Sie schlägt Werte vor, trägt sie aber nicht selbst ein.

**Weitere Funktionen**

- Verfallsuhr je Fahrt mit Ampel: unter 30 Tagen rot, unter 90 Tagen gelb
- Einlösen-Knopf direkt auf der Karte, dazu die Status offen, beantragt, eingelöst, erstattet, verfallen
- Fahrkartenansicht: Der Code wird im Screenshot gesucht, freigestellt und auf einer gezeichneten Fahrkarte groß dargestellt, mit Strecke, Zeiten, Zug, Auftragsnummer und Frist. Sitzt der Ausschnitt nicht, lässt er sich mit einem Rahmen nachziehen. Die Displayhelligkeit geht dabei auf Maximum und danach zurück.
- BahnCard hinterlegen, mit eigenem Code und Gültigkeitsdatum, in der Fahrkartenansicht einen Tipp entfernt
- Zwei unabhängige Zeitquellen je Fahrt: der gespeicherte Link und die Fahrplanauskunft von bahn.de, mit Abgleich beider Werte
- Fahrgastrechte-Rechner: 25 Prozent ab 60 Minuten, 50 Prozent ab 120 Minuten, Hinweis unter dem Mindestauszahlbetrag von 4 Euro
- Zugfinder im App-Fenster, Pro-Anmeldung bleibt erhalten, abgelesene Verspätung kommt mit einem Tipp zurück in die Fahrt
- Auswertung: offener Wert, Wert nach Strecke, Verfall über die nächsten zwölf Monate
- Sicherung als JSON inklusive Screenshots, Codes und BahnCard, Ausgabe als CSV

## Warum der Code ausgeschnitten und nicht neu erzeugt wird

Der Code auf einem DB-Online-Ticket ist ein Aztec-Code mit signierten Binärdaten. Ein aus dem erkannten Inhalt neu erzeugter Code wäre am Zugang wertlos, sobald ein einziges Byte anders kodiert ist. Die App schneidet deshalb die Originalpixel in voller Auflösung aus und stellt sie frei, einschließlich der weißen Ruhezone, die jeder Scanner braucht.

Gefunden wird der Code über die Flächenform: eine geschlossene, annähernd quadratische Fläche, die durchgehend zu einem Drittel bis drei Vierteln schwarz ist und fast keine Graustufen enthält. Textzeilen zerfallen dabei in flache Streifen und fallen heraus. Liegt der Code unmittelbar an Text, ohne weißen Abstand, kann die Erkennung ihn mitnehmen, dafür gibt es den Rahmen zum Nachziehen.

## APK bauen ohne Android Studio

1. Repository auf GitHub anlegen und diesen Ordner hineinschieben.
2. Unter *Actions* läuft `APK bauen` automatisch, oder von Hand über *Run workflow* starten.
3. Nach etwa drei Minuten unten beim Lauf das Artifact `offene-fahrten-apk` herunterladen und entpacken.
4. Die `.apk` auf das Telefon kopieren und installieren. Android fragt einmal nach der Erlaubnis, Apps aus dieser Quelle zu installieren.

Die APK ist mit dem Debug-Schlüssel signiert. Für den eigenen Gebrauch reicht das; über den Play Store verteilen ließe sie sich damit nicht.

## Mit Android Studio

Den Ordner `android` öffnen und `Run` drücken. Gradle 8.7 oder neuer, JDK 17, compileSdk 34.

## Ohne Android überhaupt

`web/offene-fahrten.html` ist dieselbe App als einzelne Datei. Im Browser öffnen und über das Menü zum Startbildschirm hinzufügen. Die Daten liegen dann in der IndexedDB des Browsers, also getrennt von einer installierten APK.

Drei Dinge kann nur die APK, weil sie eine Hülle um die Oberfläche braucht: das Auflösen des kurzen `vbid`-Links und die Fahrplanabfrage bei bahn.de, die aus einer Webseite fremder Herkunft gesperrt sind, das Hochdrehen der Displayhelligkeit, und den Empfang geteilter Links aus der DB-App. Lange Links und direkt eingefügte Recon-Strings funktionieren überall.

## Aufbau

```
web/index.html              Quelle, Fassung für Claude-Artifacts
web/offene-fahrten.html     eigenständige Fassung, gleiche App
android/                    Gradle-Projekt
  .../MainActivity.java       Hülle: HTTP ohne Herkunftssperre, Helligkeit, Teilen-Empfang
  .../ZugfinderActivity.java  Zugfinder im App-Fenster mit Rückgabe der Minuten
  .../assets/www/             die eingebaute Weboberfläche
.github/workflows/apk.yml   Bauauftrag für die APK
```

Zum Ändern der Oberfläche `web/index.html` bearbeiten und die eigenständige Fassung sowie die Kopie unter `assets/www` neu erzeugen.

## Rechtlicher Rahmen

Die Rechenregeln folgen der Verordnung (EU) 2021/782 und den Angaben auf bahn.de, Stand Oktober 2026. Die App dokumentiert und rechnet, sie ersetzt keine Rechtsauskunft. Eine Fahrkarte gilt nur für die Person, auf die sie ausgestellt ist, und nur innerhalb ihrer Gültigkeit.
