## ADDED Requirements

### Requirement: El token contiene claims mínimos verificables
El servicio SHALL emitir en `POST /auth/login` un token firmado que contenga los claims `sub`, `client`, `iat` y `exp`, en sustitución del token Base64 sin firma documentado en el contrato actual.

#### Scenario: Token de login válido
- **WHEN** un cliente envía credenciales válidas y un `client` no vacío
- **THEN** la respuesta `200` contiene un `token` con los claims `sub`, `client`, `iat` y `exp`
- **AND** `sub` es el `username` almacenado en la base de datos
- **AND** `client` es el valor enviado en la solicitud

### Requirement: El campo client es obligatorio
El servicio SHALL exigir el campo `client` no vacío, de máximo 64 caracteres y formato libre, en la solicitud de login. El contrato actual lo define como opcional; este cambio proviene del Issue #11.

#### Scenario: Client ausente o vacío
- **WHEN** la solicitud no incluye `client`, o lo envía vacío o solo con blancos
- **THEN** la respuesta es `400` con un `message` que indica que `client` es obligatorio
- **AND** no se emite ningún token

#### Scenario: Client demasiado largo
- **WHEN** `client` supera 64 caracteres
- **THEN** la respuesta es `400` con un `message` que indica el límite de longitud
- **AND** no se emite ningún token

#### Scenario: Client no altera la identidad
- **WHEN** la solicitud incluye un `client` válido y credenciales válidas
- **THEN** `user.username` y `user.role` proceden de la base de datos y no del `client` enviado

### Requirement: expiresIn es coherente con exp
El servicio SHALL devolver `expiresIn` igual a `exp` menos el instante de emisión, dentro de una tolerancia de 5 segundos. El contrato actual devuelve la constante 3600.

#### Scenario: Coherencia dentro de la tolerancia
- **WHEN** se emite un token en el instante `now`
- **THEN** `expiresIn` y `exp - now` difieren como máximo 5 segundos

### Requirement: Un token expirado se detecta como expirado
Las pruebas SHALL demostrar que un token cuyo claim `exp` ya venció se identifica como expirado. El contrato no define validación de tokens en el backend.

#### Scenario: Token con exp en el pasado
- **WHEN** se evalúa un token cuyo `exp` es anterior al instante actual
- **THEN** el token se marca como expirado y no se considera vigente

### Requirement: Errores de autenticación se preservan
El servicio SHALL mantener los status, mensajes y orden de validación documentados en el contrato actual, salvo el error nuevo de `client`.

#### Scenario: Solicitud nula
- **WHEN** el cuerpo es `null`
- **THEN** la respuesta es `400` con `{"message":"Request is null"}`

#### Scenario: Username ausente
- **WHEN** `username` es ausente, `null`, vacío o solo blancos
- **THEN** la respuesta es `400` con `{"message":"Username is required"}`, aunque `password` también falte

#### Scenario: Password ausente
- **WHEN** `username` es válido y `password` es ausente, `null`, vacío o solo blancos
- **THEN** la respuesta es `400` con `{"message":"Password is required"}`

#### Scenario: Credenciales no válidas
- **WHEN** el usuario no existe, el password es incorrecto o la consulta a la base de datos falla
- **THEN** la respuesta es `401` con `{"message":"Invalid user <username> or password"}` y no contiene `token`

#### Scenario: Excepción no controlada
- **WHEN** ocurre una excepción no controlada en el Resource
- **THEN** la respuesta es `500` con `message` (o `Unknown error` si no tiene mensaje) y `exception` con el nombre completo de la clase

#### Scenario: Errores del framework
- **WHEN** el JSON es malformado, el método no es `POST`, el `Accept` no es compatible o el `Content-Type` no es `application/json`
- **THEN** la respuesta conserva su status (400, 405, 406 o 415) con cuerpo vacío

### Requirement: El secreto se configura externamente
El servicio SHALL leer la clave de firma desde `AUTH_TOKEN_SECRET` y SHALL NOT usar un valor por defecto fuera del entorno de laboratorio. El contrato actual documenta un secreto embebido en el código.

#### Scenario: Secreto ausente fuera de laboratorio
- **WHEN** `AUTH_TOKEN_SECRET` no está configurado en un entorno que no es laboratorio
- **THEN** el servicio no emite tokens y reporta un error de configuración

### Requirement: Token y secreto no se registran
El servicio SHALL NOT escribir el token, la clave de firma ni el secreto en los logs.

#### Scenario: Login exitoso sin fuga en logs
- **WHEN** se procesa un login válido
- **THEN** los logs generados no contienen el valor del token ni de `AUTH_TOKEN_SECRET`

### Requirement: La respuesta exitosa conserva su forma
La respuesta `200` SHALL mantener los campos `token`, `expiresIn`, `user` (con `username`, `displayName` escapado con `escapeHtml4`, `role`) y `debug`.

#### Scenario: Respuesta de login válido
- **WHEN** un login válido se completa
- **THEN** la respuesta `200` contiene `token`, `expiresIn`, `user` y `debug` con valor `authenticated-with-legacy-flow`
- **AND** la respuesta no incluye `id`, `password`, hash ni `client`
