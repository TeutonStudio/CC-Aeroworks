# CC-Aeroworks

CC-Aeroworks verbindet Create: Aeroworks Control Desks direkt mit CC:Tweaked und ergänzt zwei- und dreistellige Desk-Displays sowie die kombinierte Maussteuerung für Lever, Joystick und Throttle Quadrants. Die Displays unterstützen Ziffern und frei beschreibbare `7x5`- beziehungsweise `11x5`-Pixelraster. Das zweistellige Display passt in kleine und große Slots, das dreistellige ausschließlich in große Slots. Drive By Wire 0.2.9 ist optional unterstützt.

Für die kombinierte Steuerung im Aeroworks-Modulbildschirm den Input Type `Kombiniert` auswählen, anschließend im mittleren Eingabefeld die gewünschte Aktivierungstaste erfassen und diese beim Steuern halten. Der Modus wechselt über das vorhandene Symbol zyklisch zwischen `Buttons`, `Analog` und `Kombiniert`. Das API-Handbuch liegt als Buchitem im Abschnitt `CC-Aeroworks` des Aeroworks-Creative-Tabs.

## Entwicklungsumgebung

- Minecraft 1.21.1, NeoForge 21.1.228 und Java 21
- Kotlin 2.2.20 mit KotlinForForge NeoForge 5.11.0
- Create 6.0.10, Aeronautics/Aeroworks 1.3.0
- CC:Tweaked API-Baseline 1.119.0; Metadatenbereich bis vor 1.121

Die lizenzpflichtigen Mod-JARs werden nicht mitgeliefert. Die erwarteten Namen stehen in [libs/README.md](libs/README.md). Danach:

```bash
./gradlew clean build
./gradlew runClient
```

Eine deutschsprachige Einführung zur Programmierung steht in [`docs/peripheral-programming.md`](docs/peripheral-programming.md). Die kompakte API-Referenz und ausführbare Beispiele stehen unter `docs/cc-peripheral-api.md` und `examples/cc/`.

Repository: `TeutonStudio/CC-Aeroworks`
