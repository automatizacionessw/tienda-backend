# instancia-n8n Specification

## Purpose

Provee una instancia de n8n junto al backend, en el despliegue y en desarrollo, para construir sobre ella las automatizaciones y agentes que consumen las funciones del backend.

## Requirements

### Requirement: Instancia de n8n con versión fija
Cada compose del repositorio que incluya n8n MUST usar la imagen oficial de n8n con una versión explícita, nunca una etiqueta móvil como `latest`, y la misma versión en todos los compose. La instancia MUST reportar su estado con un healthcheck basado en su endpoint de salud.

#### Scenario: Versión fijada
- **WHEN** se revisa la imagen del servicio `n8n` en cada compose del repositorio
- **THEN** todas indican la misma versión explícita de n8n

#### Scenario: Instancia saludable
- **WHEN** se levanta un compose que incluye n8n
- **THEN** el servicio `n8n` queda saludable
- **AND** el editor de n8n responde en su puerto HTTP

### Requirement: Persistencia de workflows y credenciales
Los workflows, credenciales, ejecuciones y usuarios de n8n MUST persistir en un volumen con nombre, de modo que sobrevivan a reinicios, redeploys y recreaciones del contenedor.

#### Scenario: Datos tras recrear el contenedor
- **WHEN** se crea la cuenta owner y un workflow, y luego se recrea el servicio `n8n` sin borrar volúmenes
- **THEN** la cuenta owner y el workflow siguen disponibles

### Requirement: Clave de cifrado estable
En el despliegue, la clave con la que n8n cifra las credenciales MUST definirse explícitamente mediante una variable de entorno. Así las credenciales guardadas se pueden descifrar tras cualquier redeploy o migración del volumen.

#### Scenario: Credencial legible tras redeploy
- **WHEN** se guarda una credencial en n8n desplegado y luego se redespliega el servicio con la misma clave
- **THEN** la credencial se puede usar sin volver a cargarla

### Requirement: URL del backend disponible para los workflows
El contenedor de n8n MUST recibir la variable `TIENDA_BACKEND_URL` con la URL base con la que alcanza al backend en su entorno, sin barra final. Las expresiones de los workflows MUST poder leerla como `$env.TIENDA_BACKEND_URL`. El contenedor de n8n MUST NOT recibir credenciales de la base de datos del backend ni secrets de Telegram.

#### Scenario: Workflow alcanza al backend
- **WHEN** un workflow ejecuta un HTTP Request a `{{ $env.TIENDA_BACKEND_URL }}/actuator/health` con el backend en ejecución
- **THEN** la respuesta contiene `"status":"UP"`

#### Scenario: Sin secretos del backend en n8n
- **WHEN** se inspeccionan las variables de entorno del contenedor de n8n
- **THEN** no contienen la contraseña de la base de datos, el token del bot ni el secret del webhook de Telegram

### Requirement: Telemetría de n8n deshabilitada
La instancia de n8n MUST arrancar con el envío de diagnósticos y telemetría a n8n deshabilitado.

#### Scenario: Diagnósticos apagados
- **WHEN** se inspecciona la configuración del servicio `n8n` en cada compose
- **THEN** el envío de diagnósticos está deshabilitado
