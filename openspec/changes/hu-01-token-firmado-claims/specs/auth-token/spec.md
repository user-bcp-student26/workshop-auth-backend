## ADDED Requirements

### Requirement: El token de login está firmado
El servicio SHALL emitir en `POST /auth/login` un `token` firmado con una clave de firma, en sustitución del Base64 sin firma. Origen: Issue #15 (H-03).

#### Scenario: Token de login válido
- **WHEN** un cliente envía credenciales válidas
- **THEN** la respuesta `200` contiene un `token` que verifica con la clave de firma del servicio
- **AND** el `token` no es Base64 de texto plano sin firma

### Requirement: La verificación interna rechaza tokens alterados
Una función interna de verificación SHALL rechazar cualquier token cuyo usuario, rol o expiración hayan sido modificados después de emitirse. No existe endpoint de verificación.

#### Scenario: Rol alterado
- **WHEN** se modifica el rol de un token válido sin volver a firmarlo
- **THEN** la verificación interna lo rechaza

#### Scenario: Usuario alterado
- **WHEN** se modifica el usuario de un token válido sin volver a firmarlo
- **THEN** la verificación interna lo rechaza

### Requirement: expiresIn se mantiene como 3600
La respuesta `200` SHALL devolver `expiresIn` como entero `3600`, igual que en el contrato actual.

#### Scenario: Respuesta de login válido
- **WHEN** un login válido se completa
- **THEN** `expiresIn` es `3600`

### Requirement: El token contiene expiración verificable
El servicio SHALL incluir una expiración en el `token` y SHALL rechazar los tokens expirados.

#### Scenario: Token dentro de su vigencia
- **WHEN** la verificación interna recibe un token antes de su expiración
- **THEN** lo acepta

#### Scenario: Token expirado
- **WHEN** la verificación interna recibe un token cuya expiración ya pasó
- **THEN** lo rechaza como expirado



### Requirement: La clave de firma no aparece en el código Java
El repositorio SHALL NOT contener la clave de firma en código Java. Origen: Issue #15 (H-03); la configuración externa se especifica en `configuracion-secreto-cors`.

#### Scenario: Búsqueda en el código fuente
- **WHEN** se busca el valor de la clave de firma en los archivos Java del repositorio
- **THEN** no aparece ninguna coincidencia




