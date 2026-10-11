# Gradle: veränderter Immutable Workspace

Wenn Gradle schon vor dem Modstart mit `The contents of the immutable workspace ... have been modified` abbricht, betrifft der gemeldete Pfad einen Gradle-Transform-Cache. Im Fehler vom 11. Oktober 2026 war dies der IntelliJ-Classpath für `util_rt.jar`, nicht die Mod-JAR.

## Gezielte Wiederherstellung

1. Laufende Gradle-Aufgaben beenden und im Projekt `./gradlew --stop` ausführen.
2. Nur das im Fehler genannte Verzeichnis unter `~/.gradle/caches/<gradle-version>/transforms/<hash>` in ein Sicherungsverzeichnis außerhalb von `transforms` verschieben. Den Pfad aus der aktuellen Fehlermeldung übernehmen.
3. Den ursprünglichen Aufruf, beispielsweise `runClient --stacktrace` in der IDE, erneut ausführen. Gradle erzeugt den fehlenden Transform neu.
4. Bei erneutem Auftreten prüfen, ob IDE-Updates oder andere Prozesse Cache-Inhalte verändern. Eine erneute Meldung mit anderem Pfad ist gesondert zu untersuchen.

Die Projektdateien, lokalen Mod-Abhängigkeiten und Spielstände müssen dafür nicht gelöscht werden. Weder die Prüfung unveränderlicher Workspaces abschalten noch einzelne `metadata.bin`-Dateien bearbeiten.

Hintergrund: [Gradle-Verzeichnisse und Caches](https://docs.gradle.org/current/userguide/directory_layout.html).
