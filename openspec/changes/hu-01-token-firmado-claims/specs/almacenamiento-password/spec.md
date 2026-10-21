## ADDED Requirements

### Requirement: La base no almacena contraseñas en claro
El servicio SHALL almacenar cada contraseña como hash con sal aleatoria por usuario, con un algoritmo de derivación documentado, y SHALL NOT guardar el valor en claro. Origen: Issue #14 (H-02).

#### Scenario: Cuentas de laboratorio sembradas
- **WHEN** la base se inicializa desde un archivo vacío
- **THEN** el valor almacenado para `student` y `trainer` no es igual a sus contraseñas en claro
- **AND** los dos usuarios tienen sales distintas

### Requirement: El login verifica la contraseña contra el hash
El servicio SHALL verificar la contraseña recibida contra el hash almacenado, sin comparar texto en claro.

#### Scenario: Contraseña correcta
- **WHEN** el cliente envía la contraseña correcta de `student`
- **THEN** la respuesta es `200` con la identidad de `student`

#### Scenario: Contraseña incorrecta
- **WHEN** el cliente envía una contraseña distinta a la almacenada
- **THEN** la respuesta es `401` sin `token`

#### Scenario: Hash almacenado con formato inválido
- **WHEN** el valor almacenado para un usuario no tiene un formato de hash válido
- **THEN** la respuesta es `401` sin `token`

### Requirement: La respuesta no expone material de contraseña
La respuesta SHALL NOT incluir `password`, hash, sal ni `id`.

#### Scenario: Respuesta de login válido
- **WHEN** un login válido se completa
- **THEN** la respuesta no contiene `password`, hash, sal ni `id`

### Requirement: La migración de contraseñas es atómica
El servicio SHALL migrar las contraseñas existentes en claro a hash de forma atómica: si la migración falla, la base conserva su estado previo.

#### Scenario: Migración de una base existente
- **WHEN** la base contiene usuarios con contraseñas en claro y se ejecuta la migración
- **THEN** todos los usuarios quedan con hash
- **AND** los logins válidos siguen devolviendo `200`

#### Scenario: Migración fallida
- **WHEN** la migración falla a mitad de proceso
- **THEN** ningún usuario queda con un valor parcialmente migrado
