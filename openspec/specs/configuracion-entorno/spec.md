# configuracion-entorno Specification

## Purpose

Define de dónde toma la aplicación su configuración: una base versionada sin secretos que se completa con variables de entorno, más un archivo local opcional donde cada desarrollador hace sus overrides.

## Requirements

### Requirement: Configuración base versionada sin secretos
El repositorio MUST incluir `src/main/resources/application.properties` con todas las propiedades que la aplicación necesita para arrancar. Toda propiedad que dependa del entorno MUST poder definirse mediante una variable de entorno. Esto incluye:
- la URL, el usuario y la contraseña de la base de datos;
- la estrategia de esquema de JPA y el log de SQL;
- la habilitación, el token y el modo del bot de Telegram;
- la URL y el secret del webhook;
- el directorio de almacenamiento;
- el tiempo de inactividad de las conversaciones;
- la activación de la documentación de la API.

El archivo MUST NOT contener credenciales, tokens ni secrets reales.

#### Scenario: Conexión tomada de variables de entorno
- **WHEN** la aplicación arranca con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` definidas en el entorno
- **THEN** se conecta a la base de datos indicada por esas variables

#### Scenario: Valores por defecto seguros
- **WHEN** la aplicación arranca sin variables de Telegram ni de documentación de la API definidas y sin archivo de override local
- **THEN** el bot de Telegram queda deshabilitado
- **AND** `GET /v3/api-docs` y `GET /swagger-ui.html` responden 404

#### Scenario: Inactividad de conversaciones tomada del entorno
- **WHEN** la aplicación arranca con `CONVERSACION_INACTIVIDAD=30m` definida en el entorno
- **THEN** una conversación deja de estar vigente cuando pasan 30 minutos desde su último mensaje
- **AND** sin la variable, el tiempo de inactividad es de 2 horas

### Requirement: Override local del desarrollador
Si `application-local.properties` existe junto a `application.properties`, la aplicación MUST cargarlo automáticamente, sin activar ningún perfil. Sus valores MUST tener precedencia sobre los de `application.properties`. Si el archivo no existe, la aplicación MUST arrancar igual.

#### Scenario: Override local presente
- **WHEN** un desarrollador define `springdoc.api-docs.enabled=true` y `springdoc.swagger-ui.enabled=true` en `application-local.properties` y arranca la aplicación con `./mvnw spring-boot:run`
- **THEN** `/swagger-ui.html` está disponible

#### Scenario: Sin archivo local
- **WHEN** `application-local.properties` no existe y la aplicación arranca
- **THEN** usa `application.properties` y las variables de entorno sin reportar error por el archivo ausente

### Requirement: Archivos de configuración local fuera del control de versiones
Git MUST ignorar `application-local.properties`. El repositorio MUST incluir `application-local.properties.example` como plantilla de override, con las propiedades que un desarrollador suele ajustar y sin valores reales.

#### Scenario: Archivo local ignorado
- **WHEN** un desarrollador crea `src/main/resources/application-local.properties` y ejecuta `git status`
- **THEN** el archivo no aparece entre los cambios ni entre los archivos sin seguimiento
