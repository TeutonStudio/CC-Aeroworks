# Die vier Displays

[[Deutsch|Programmierbare-Displays]] · [[English|Displays-en]]

![Programmiertes Pixelmuster auf dem erweiterten großen Display, hier bei 32 PPB und cyanfarbiger Ausgabe.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-pixels-colour-resolution.png)


![Alle vier Displayvarianten in einer Pultreihe: normal groß, normal klein, erweitert klein und erweitert groß.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-four-displays.png)


CC-Aeroworks bietet **vier Displaymodule**: klein und groß, jeweils normal und erweitert. Alle Varianten unterstützen Text-, Zahlen- und Pixelanzeige. Größe und Stufe sind unabhängig voneinander: Ein großes Display erhält seine erweiterten Funktionen nur in der erweiterten Stufe.

## Varianten im Vergleich

| Display | Item-ID | Passende Sockets | Farbe | Touch / Draw | Raster bei 256 PPB |
|---|---|---|---|---|---|
| Kleines Display | `cc_aeroworks:small_display` | `left`, `right`, `big` | feste Signalfarbe | nein | 112 × 112 |
| Großes Display | `cc_aeroworks:large_display` | nur `big` | feste Signalfarbe | nein | 160 × 112 |
| Erweitertes kleines Display | `cc_aeroworks:advanced_small_display` | `left`, `right`, `big` | einstellbar, `0xRRGGBB` | ja, mit erweitertem Slot-Gerät | 112 × 112 |
| Erweitertes großes Display | `cc_aeroworks:advanced_large_display` | nur `big` | einstellbar, `0xRRGGBB` | ja, mit erweitertem Slot-Gerät | 160 × 112 |

Normale Displays haben einen grauen, erweiterte einen gelben Rahmen. Ein erweitertes Display verwendet eine gespeicherte 24-Bit-Anzeigefarbe. Für Touch und Draw muss zusätzlich ein **erweiterter CC:Tweaked-Computer** oder ein **erweitertes CC:-Terminals-Terminal** im aktiven Pultverbund eingesetzt sein. Ein normaler Computer genügt für reine Ausgabe.

## 1. Kleines Display

Das kleine Display ist eine reine Anzeige für kompakte Instrumente. Es passt in beide kleinen Sockets sowie in den großen Socket. Seine nutzbare Oberfläche misst **7/16 × 7/16 Block**.

**Rezept:** 1 normaler CC:Tweaked-Monitor → mechanische Presse → 1 kleines Display.

![Rezept: Normaler Monitor unter der mechanischen Presse ergibt ein kleines Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/de/small_display.png)

## 2. Großes Display

Das große Display bietet eine breitere Anzeigefläche von **10/16 × 7/16 Block**. Es passt ausschließlich in `big` und bleibt eine normale Ausgabe ohne Touch.

**Rezept:** 1 kleines Display + 1 Eisenplatte (`create:iron_sheet`) → Deployer → 1 großes Display.

![Rezept: Kleines Display und Eisenplatte im Deployer ergeben ein großes Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/de/large_display.png)

## 3. Erweitertes kleines Display

Diese Variante verbindet die kompakte Fläche des kleinen Displays mit einstellbarer Farbe und Touch-/Draw-Funktionen. Sie passt in `left`, `right` und `big`.

**Rezept:** 1 erweiterter CC:Tweaked-Monitor → mechanische Presse → 1 erweitertes kleines Display.

![Rezept: Erweiterter Monitor unter der mechanischen Presse ergibt ein erweitertes kleines Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/de/advanced_small_display.png)

## 4. Erweitertes großes Display

Diese Variante verbindet die breite Fläche des großen Displays mit einstellbarer Farbe und Touch-/Draw-Funktionen. Sie passt ausschließlich in `big`.

**Rezept:** 1 erweitertes kleines Display + 1 Eisenplatte (`create:iron_sheet`) → Deployer → 1 erweitertes großes Display.

![Rezept: Erweitertes kleines Display und Eisenplatte im Deployer ergeben ein erweitertes großes Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/recipes/de/advanced_large_display.png)

Alle Rezepte erzeugen ein Display. Beim Vergrößern bleibt die Stufe erhalten. Betreibe Presse und Deployer über Creates üblichen mechanischen Antrieb; der Deployer verwendet die Eisenplatte für den Ausbau. Die Grafiken bilden die Rezeptdateien von Version 1.5.3 mit gerenderten Mod-Modellen und Texturen ab.

## Montage und Bedienung

1. Montiere das Display in einem passenden Socket des Aeroworks-Pults.
2. Öffne mit Schleichen + Rechtsklick die Modulübersicht und setze das gewünschte Slot-Gerät ein.
3. Für die Einstellungen verwende den Create-Schraubenschlüssel auf einer horizontalen Pultseite.
4. Bei einem erweiterten Display wählt **Display-Steuerung** ein kompatibles Lua-Skript oder mit Create: Radars eine Radarquelle. Diese Quellenwahl ist exklusiv.
5. Betrete für Touch die Pultsteuerung per Rechtsklick. Linksklick erzeugt `tap`, gehaltene rechte Maustaste eine `draw`-Geste. Die konfigurierten Eingabeachsen bewegen den virtuellen Zeiger.

Der eingebettete Computer führt gebundene Handler automatisch aus. Ein erweitertes Terminal reicht für Touch an einem externen Computer; dessen Programm verarbeitet die Ereignisse selbst. Ein Terminal führt keine lokalen Lua-Dateien aus.

## Auflösung

Das Raster richtet sich nach der **Pixeldichte in Parts per Block (PPB)** und der physischen Displayfläche. Standard sind 256 PPB; Größe und Stufe verändern diese Dichte nicht. Die tatsächliche Rastergröße lässt sich über `getDisplaySize(socket)` abfragen.

Bei deaktiviertem `display.useGlobalResolution` können Programme individuelle Slot-Auflösungen einstellen. Eine Größenänderung des Rasters leert ein inkompatibles Pixelbild; das Programm muss es neu zeichnen. Details stehen unter [[Konfiguration]].

## Programmieren

Die [[Lua-API|API-Schnellreferenz]] erklärt Pultsuche, Text-/Zahlenausgabe, Pixelraster, Farbe und Touch-Handler. Weitere Anleitungen: [[Bedienung]] · [[Radar-Routing]] · [[Bedienterminals]] · [[Fehlerbehebung]].

![Draw-Beispiel: Der echte Lua-Helfer touchdisplay.drawStroke zeichnet eine Kurve aus vorgegebenen Testpunkten auf das Display.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-draw-stroke.png)
