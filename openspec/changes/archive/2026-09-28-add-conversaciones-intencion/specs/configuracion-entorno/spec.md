# Spec Delta

## MODIFIED Requirements

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
