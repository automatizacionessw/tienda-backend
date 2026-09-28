# Spec Delta

## Purpose

Identifica a las personas que interactúan con el negocio a través de Telegram como clientes, manteniendo sus datos de perfil actualizados para que otros módulos (historial de mensajes y, a futuro, ventas) puedan referirse a ellos.

## ADDED Requirements

### Requirement: Registro de cliente nuevo
Cuando se procesa un mensaje privado de un usuario de Telegram que no está registrado, el sistema MUST crear un cliente con: identificador de usuario de Telegram, username (si tiene), nombre, apellido (si tiene), código de idioma (si Telegram lo informa) y fecha de alta.

#### Scenario: Primer mensaje de un usuario
- **WHEN** un usuario de Telegram sin registro previo envía su primer mensaje privado al bot
- **THEN** existe un cliente con su identificador de usuario de Telegram, sus datos de perfil y la fecha de alta
- **AND** el mensaje queda asociado a ese cliente

### Requirement: Actualización de datos del cliente
Cuando se procesa un mensaje privado de un usuario ya registrado, el sistema MUST actualizar su username, nombre, apellido e idioma con los valores recibidos, conservando su identidad y su fecha de alta.

#### Scenario: Cambio de username
- **WHEN** un cliente registrado con username "juan_old" envía un mensaje con username "juan_new"
- **THEN** el cliente tiene username "juan_new"
- **AND** conserva su identificador y su fecha de alta originales
- **AND** sus mensajes anteriores siguen asociados a él

### Requirement: Unicidad por usuario de Telegram
El sistema MUST mantener como máximo un cliente por identificador de usuario de Telegram, incluso si llegan mensajes concurrentes del mismo usuario.

#### Scenario: Mensajes concurrentes del mismo usuario nuevo
- **WHEN** un usuario sin registro envía dos mensajes que se procesan de forma concurrente
- **THEN** existe un único cliente para ese identificador de usuario de Telegram
- **AND** ambos mensajes quedan asociados a ese cliente
