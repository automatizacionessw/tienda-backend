# Spec Delta

## Purpose

Ofrece un mecanismo uniforme para guardar y recuperar archivos binarios (inicialmente audios de notas de voz) mediante referencias independientes de la ubicación física, de modo que el medio de almacenamiento pueda cambiarse sin migrar los datos que las referencian.

## ADDED Requirements

### Requirement: Guardado con referencia opaca
Al guardar un archivo, el sistema MUST devolver una referencia (clave) que lo identifica de forma única y que no depende de la ruta absoluta ni del medio de almacenamiento. Guardar dos archivos, aunque tengan el mismo nombre original, MUST producir referencias distintas sin sobrescribir contenido.

#### Scenario: Dos archivos con el mismo nombre
- **WHEN** se guardan dos archivos distintos con el mismo nombre original
- **THEN** se obtienen dos referencias distintas
- **AND** cada referencia recupera su propio contenido

### Requirement: Recuperación por referencia
El sistema MUST permitir recuperar el contenido de un archivo a partir de la referencia devuelta al guardarlo. Solicitar una referencia inexistente MUST producir un error de "no encontrado".

#### Scenario: Recuperar un archivo guardado
- **WHEN** se guarda un archivo y luego se solicita con la referencia obtenida
- **THEN** se obtiene exactamente el mismo contenido binario

#### Scenario: Referencia inexistente
- **WHEN** se solicita un archivo con una referencia que no existe
- **THEN** el sistema informa que el archivo no fue encontrado

### Requirement: Almacenamiento local configurable
La implementación en disco local MUST guardar los archivos bajo un directorio base configurable. Si el directorio no existe, el sistema MUST crearlo al arrancar; si no puede crearse o no es escribible, el arranque MUST fallar indicando la ruta.

#### Scenario: Directorio base inexistente
- **WHEN** la aplicación arranca con un directorio base configurado que no existe y el proceso tiene permisos para crearlo
- **THEN** el directorio se crea y los archivos guardados quedan dentro de él

#### Scenario: Directorio base no escribible
- **WHEN** la aplicación arranca con un directorio base sin permisos de escritura
- **THEN** el arranque falla con un mensaje que indica la ruta problemática

### Requirement: Confinamiento al directorio base
Ninguna referencia MUST permitir leer ni escribir fuera del directorio base configurado.

#### Scenario: Referencia con recorrido de directorios
- **WHEN** se solicita un archivo con una referencia que contiene segmentos como `../`
- **THEN** el sistema rechaza la solicitud sin acceder a archivos fuera del directorio base
