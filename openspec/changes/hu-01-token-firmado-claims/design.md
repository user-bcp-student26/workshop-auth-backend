## Contexto

- Alcance: Issues #12 (H-05), #13 (H-01), #14 (H-02), #15 (H-03) y #16 (H-04) de `workshop-auth-backend`.
- Código afectado: [AuthResource.java](../../../src/main/java/com/bcp/workshop/auth/AuthResource.java), [LegacyDatabase.java](../../../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [LegacyTokenUtil.java](../../../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) y [application.properties](../../../src/main/resources/application.properties).
- Estado verificado: el código actual es el original vulnerable. `PasswordHasher`, `PasswordMigration` y `AuthService` no existen en `src/main`.
- Sin dependencias nuevas: se usa el JDK (`javax.crypto`) para hash y firma.

## Goals

- Eliminar los cinco defectos de H-01 a H-05 en `POST /auth/login`, según los Issues #12 a #16.
- Preservar el contrato de todo lo que no está en los Issues.
- Cada cambio verificable con pruebas que fallan antes y pasan después.

## Non-goals

- Refactor general del Resource (H-07) o rediseño de la aplicación.
- Cambios en `GET /q/health`, en `TokenService` RS256 o en endpoints de verificación.
- Claims de HU-01 (`client`, `expiresIn` calculado), que van en `hu-01-claims-token`.
- Actualizar dependencias (H-06).

## Contratos protegidos

| Contrato | Fuente | Tratamiento |
|---|---|---|
| Ruta, verbo y media types de `POST /auth/login` | `AuthResource` | Sin cambios |
| Orden y mensajes 400 (`Request is null`, `Username is required`, `Password is required`) | `AuthResource` | Sin cambios |
| 401 `Invalid user <username> or password` sin `token` | `AuthResource` | Sin cambios |
| 200 con `user`, `displayName` escapado, `debug` y `expiresIn` 3600 | `AuthResource` | Sin cambios |
| Errores del framework 400/405/406/415 con cuerpo vacío | Quarkus | Sin cambios |
| 500 con `message` y `exception` | `AuthResource` | Cambio intencional (#12) |
| Token Base64 sin firma | `LegacyTokenUtil` | Cambio intencional (#15) |
| CORS `origins=/.*/` | `application.properties` | Cambio intencional (#16) |
| Secreto fijo en código | `LegacyTokenUtil` | Cambio intencional (#16) |

## Componentes afectados

- `AuthResource`: catch del 500 (D5) y emisión del token con `expiresIn` (D3).
- `LegacyDatabase`: consulta parametrizada (D1), almacenamiento de contraseñas y migración al arrancar (D2).
- Componente nuevo de hash y verificación de contraseñas (D2), con PBKDF2 del JDK.
- Componente nuevo de emisión y verificación HS256 (D3), con `Clock` inyectable. Sustituye a `LegacyTokenUtil`.
- `application.properties`: `auth.token.secret` y CORS por perfil (D4).
- Pruebas: `AuthResourceTest` y pruebas nuevas de caracterización, hash, migración, token y configuración.
- `README.md`: procedimiento de respaldo y migración (D2).
- `docs/ENDPOINTS.md`: se actualiza al implementar.

## Decisiones

### D1. Consulta parametrizada (#13)

La consulta busca el usuario por `username` con `PreparedStatement` y un parámetro enlazado. La verificación del `password` se hace en Java contra el hash (D2), no en SQL. Un fallo de consulta sigue devolviendo `401` sin `token`, como documenta el contrato.

Alternativas descartadas: escapar comillas a mano (frágil) y mantener la comparación de contraseña en SQL (conserva H-02).

### D2. Hash de contraseñas (#14)

- Algoritmo: PBKDF2-HMAC-SHA256 con 600000 iteraciones y sal aleatoria de 16 bytes por usuario, todo con el JDK.
- Formato almacenado versionado, por ejemplo `pbkdf2_sha256$<iteraciones>$<sal>$<hash>`, para poder migrar el algoritmo en el futuro.
- Alternativas consideradas: Argon2id y bcrypt (requieren dependencia nueva) y PBKDF2 con el JDK (elegida).
- Argon2id se descarta por ahora: requiere una dependencia nueva. El contrato que lo mencionaba se corrige (ver `docs/ENDPOINTS.md`).
- Migración: al arrancar, dentro de una sola transacción. Un preflight cuenta usuarios y rechaza la migración si hay más de 10000. Si la migración falla, la transacción se revierte y el arranque falla; ningún usuario queda a medias.
- Contraseñas de laboratorio `student` y `trainer`: siguen autenticando tras la migración.

### D3. Token firmado (#15)

- Formato: JWS HS256 construido con `javax.crypto` y Base64url.
- Claims: `sub` (username), `role`, `iat` y `exp`, con `exp = iat + 3600`. El claim `client` no entra en este cambio; pertenece a `hu-01-claims-token`.
- `expiresIn` sigue siendo `3600` en la respuesta.
- Verificación: función interna de verificación, con reloj inyectable para pruebas deterministas. Rechaza firma inválida, payload alterado y `exp` vencido. No hay endpoint de verificación.
- Alternativas consideradas: (A) JWS HS256 con el JDK, elegida; (B) librería JWT como Nimbus o SmallRye: dependencia nueva y más código revisado externamente; (C) RS256 con `TokenService`: no existe en `src/main` y exige gestión de claves privadas; (D) mantener Base64 y añadir claims: sigue sin firma.
- Este cambio sustituye `LegacyTokenUtil`. El formato del token cambia, así que el despliegue espera la confirmación de consumidores (ver proposal).

### D4. Secreto y CORS (#16)

- El secreto de firma se lee de la propiedad `auth.token.secret`, que toma el valor de la variable de entorno `AUTH_TOKEN_SECRET`.
- Perfiles `dev` y `test`: valor de laboratorio en `application.properties`. Perfil `prod`: sin valor por defecto. Si falta el secreto, el login no emite ningún `token`.
- Un secreto de menos de 32 bytes impide el arranque en cualquier perfil.
- CORS: lista de orígenes por perfil. Los métodos y cabeceras se limitan a los que usa el contrato (`GET`, `POST`, `OPTIONS`; `accept`, `content-type`, `authorization`).
- Compatibilidad con #15: el valor de laboratorio vive en configuración de `dev`/`test`, no en código Java. Las pruebas de #15 verifican que no hay clave en código Java.
- Alternativas descartadas: validar el secreto en cada login (retrasa el fallo) y cifrar el secreto dentro del repositorio (fuera de alcance).

### D5. Respuesta 500 (#12)

- La respuesta `500` devuelve `{"message":"Internal error"}`, sin nombre de clase ni mensaje interno.
- La excepción se registra con su traza mediante el logger del servidor.
- Este cambio está previsto en el contrato y se documenta en `docs/ENDPOINTS.md` al implementar.
- Alternativa descartada: quitar el `exception` sin registrar nada, lo que dificulta el diagnóstico.

## Pruebas de caracterización

Antes de modificar comportamiento crítico deben existir y pasar con el código actual:

- Login válido de `student` y `trainer` con `token`, `expiresIn`, `user` y `debug`.
- Tres validaciones 400 con mensajes exactos y el orden `username` antes que `password`.
- 401 con mensaje exacto y sin `token`.
- Errores del framework (400 por JSON malformado, 405, 406, 415) con cuerpo vacío.
- `displayName` con HTML escapado, usando un usuario de prueba.
- 500 con un doble de `LegacyDatabase` que lanza excepción.

Los cambios de #12 y #15 solo actualizan las expectativas que el Issue modifica.

## Estrategia incremental

1. Ejecutar las pruebas de caracterización sin cambios de código; deben pasar.
2. D1 (#13), con prueba previa que falle con comillas SQL.
3. D5 (#12), pequeño y aislado en el `catch`.
4. D4 (#16): secreto externo y CORS.
5. D2 (#14): hash, verificación y migración atómica.
6. D3 (#15): token firmado y verificación interna, retirando `LegacyTokenUtil`.
7. Documentación del contrato y verificación final.

Cada paso deja la suite en verde antes de continuar.

## Riesgos

- **Consumidores del token:** el frontend Angular y la API Python podrían decodificar el Base64 actual. Confirmar antes de desplegar D3.
- **Migración:** una base real con contraseñas en claro debe respaldarse antes de migrar. El procedimiento se documenta en el README.
- **Relación con `hu-01-claims-token`:** ambos cambios tocan `AuthResource` y el emisor de tokens. Implementarlos en paralelo genera conflictos; conviene fijar el orden.
- **Secreto de laboratorio:** si el perfil `dev` se usa fuera del laboratorio, el secreto queda expuesto. Mitigación: fallo de arranque en `prod`.

## Rollback

- Sin cambios de esquema irreversibles: la migración de contraseñas se revierte con el respaldo previo.
- Revertir el commit restablece el token Base64 y las contraseñas en claro; esto solo es seguro si se restaura el respaldo.
