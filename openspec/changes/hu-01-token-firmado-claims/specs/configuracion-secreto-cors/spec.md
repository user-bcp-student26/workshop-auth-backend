## ADDED Requirements

### Requirement: El secreto de firma se lee de configuración externa
El servicio SHALL leer la clave de firma desde configuración o variable de entorno, sin valor por defecto en el código. Origen: Issue #16 (H-04).

#### Scenario: Secreto configurado
- **WHEN** el entorno define la clave de firma
- **THEN** el servicio emite tokens firmados con esa clave

#### Scenario: Secreto ausente
- **WHEN** el entorno no define la clave de firma
- **THEN** el servicio no emite ningún `token`

### Requirement: CORS acepta solo los orígenes listados por entorno
El servicio SHALL aceptar peticiones de navegador solo desde los orígenes configurados para cada entorno. Origen: Issue #16 (H-04).

#### Scenario: Origen listado
- **WHEN** una petición llega con un `Origin` listado en la configuración del entorno
- **THEN** la respuesta incluye `Access-Control-Allow-Origin` con ese origen

#### Scenario: Origen no listado
- **WHEN** una petición llega con un `Origin` que no está listado
- **THEN** la respuesta no incluye `Access-Control-Allow-Origin`
