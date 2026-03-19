# Alfresco Content Syncer - Windows Build

Requisiti:

- Java 21
- Maven 3.9+
- `jpackage` disponibile nel JDK usato da `JAVA_HOME`

Build del runner jar:

```bat
mvn -pl alfresco-content-syncer -am clean package
```

Output:

- `alfresco-content-syncer\target\alfresco-content-syncer-1.0.0-SNAPSHOT-runner.jar`

Build dell'app Windows con `.exe` e runtime incluso:

```bat
mvn -pl alfresco-content-syncer -am clean package -Pwindows-app
```

Output:

- `alfresco-content-syncer\target\windows\AlfrescoContentSyncer\AlfrescoContentSyncer.exe`

Avvio rapido senza app-image:

```bat
alfresco-content-syncer\run-syncer.cmd
```

Note:

- il profilo `windows-app` usa `jpackage` e crea una `app-image`, non un installer MSI
- l'app resta standalone sul PC Windows di destinazione, senza richiedere un Java esterno separato
