## Referencia

- Issue: [#11 Especificaciones para HU-01](https://github.com/user-bcp-student26/workshop-auth-backend/issues/11)
- Historia de origen: [HU-01.md](../../../HU-01.md)
- Cambio separado de `hu-01-token-firmado-claims`, que cubre los Issues #12 a #16. Este cambio no implementa esos issues.
- Contrato baseline: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md), sección `POST /auth/login`

## Por qué

Según el contrato actual, el `token` de `POST /auth/login` es Base64 de `username|role|expiry|SECRET`, sin firma. El secreto de laboratorio está en el código y viaja dentro del token. Además, `expiresIn` es una constante 3600 sin relación verificable con el `exp` real. Sin claims `sub`, `client`, `iat` y `exp`, los consumidores no pueden confiar en la identidad, el cliente ni la vigencia del token.

## Comportamiento que cambia frente al contrato

| Aspecto | Contrato actual (`docs/ENDPOINTS.md`) | Cambio propuesto | Motivo |
|---|---|---|---|
| `client` | Opcional; no interviene ni se devuelve | Obligatorio, no vacío, máximo 64 caracteres, formato libre; se refleja en el claim `client` | HU-01: obtener `client` de la solicitud, validarlo y reflejarlo en el token |
| `token` | Base64 sin firma; no es JWT | Token firmado con claims `sub`, `client`, `iat` y `exp` | HU-01: token firmado con claims mínimos |
| `expiresIn` | Constante 3600 | `exp - now`, dentro de una tolerancia | HU-01: coherencia entre `expiresIn` y `exp` |
| Secreto | `SECRET` embebido en `LegacyTokenUtil` | Lectura desde `AUTH_TOKEN_SECRET`, sin valor por defecto fuera de laboratorio | HU-01: eliminar secreto por defecto |
| Logs | No especificado | Ni token ni secreto en logs | HU-01 |

Los demás comportamientos del contrato se preservan tal como están documentados.

## Alcance

- `POST /auth/login` emite un token firmado con claims `sub`, `client`, `iat` y `exp`.
- Validación de `client` como obligatorio, máximo 64 caracteres y reflejo en el token.
- `expiresIn` calculado desde `exp - now` con tolerancia de 5 segundos.
- `AUTH_TOKEN_SECRET` como única fuente del secreto de firma.
- Redacción de token y secreto en logs.
- Pruebas de login válido, credenciales inválidas, `client` ausente y token expirado.
- Registrar la decisión entre HMAC y una librería JWT, sin elegirla en esta propuesta.

## Fuera de alcance

- Diseño técnico e implementación (clases, algoritmo, librería). Se aborda en una fase posterior.
- `tasks.md`.
- Refresh, revocación o cierre de sesión.
- Gestión de usuarios, roles o credenciales en `LegacyDatabase`.
- `GET /q/health` y cualquier endpoint distinto de `POST /auth/login`.
- Activar `TokenService` RS256 o definir qué consumidores verifican firma, issuer y expiración.
- Auditoría de seguridad exhaustiva (corresponde a `security-review`).

## Comportamiento que debe preservarse

- Ruta y verbo: `POST /auth/login`, `Content-Type` y `Accept` `application/json`.
- Orden de validación: solicitud nula, luego `username`, luego `password`.
- Mensajes exactos `Request is null`, `Username is required` y `Password is required` con status `400`.
- `401` con `{"message":"Invalid user <username> or password"}` para usuario inexistente, password incorrecto o fallo de consulta.
- `500` con `message` y `exception`, y `Unknown error` cuando la excepción no tiene mensaje.
- Errores del framework (400 por JSON malformado, 405, 406, 415) con cuerpo vacío y sin mensaje JSON.
- Respuesta `200` con `username`, `displayName` escapado con `escapeHtml4`, `role` y `debug` con valor `authenticated-with-legacy-flow`, que se conserva en esta iteración.
- La identidad procede de la base de datos, no de campos enviados por el cliente.
- Passwords nunca devueltos. El contrato anterior afirmaba Argon2id, pero el código no lo usa (H-02, Issue #14).

## Decisiones tomadas

- `debug` se conserva. Retirarlo requiere aviso previo a los consumidores y se trata en una historia posterior.
- `client` es de formato libre, no vacío y de máximo 64 caracteres; no se valida contra lista cerrada.
- Tolerancia de `expiresIn` frente a `exp - now`: 5 segundos.

## Preguntas abiertas

- El contrato no define validación de tokens en el backend. La prueba de expiración se formula sobre los claims emitidos; un verificador requiere una propuesta posterior.
- `TokenService` RS256 existente queda fuera de alcance; su activación se decide junto con HMAC frente a JWT.
