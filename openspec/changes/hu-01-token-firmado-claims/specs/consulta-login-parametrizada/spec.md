## ADDED Requirements

### Requirement: La autenticación no interpreta credenciales como SQL
El servicio SHALL consultar las credenciales de login mediante parámetros enlazados, sin concatenar `username` ni `password` en la sentencia SQL. Origen: Issue #13 (H-01).

#### Scenario: Credenciales con sintaxis SQL
- **WHEN** un cliente envía un `username` o `password` con comillas y sintaxis SQL, por ejemplo `' OR '1'='1`
- **THEN** la respuesta es `401` con `{"message":"Invalid user <username> or password"}`
- **AND** la respuesta no contiene `token`

#### Scenario: Credenciales válidas con apóstrofos
- **WHEN** existe un usuario de prueba cuyo `password` contiene apóstrofos y el cliente envía esas credenciales exactas
- **THEN** la respuesta es `200` con la identidad de ese usuario

### Requirement: Un fallo de consulta devuelve 401
El servicio SHALL responder `401` sin `token` cuando la consulta de credenciales falla, como documenta el contrato actual.

#### Scenario: Fallo de la consulta
- **WHEN** la consulta a la base de datos lanza un error
- **THEN** la respuesta es `401` con `{"message":"Invalid user <username> or password"}`
- **AND** la respuesta no contiene `token`
