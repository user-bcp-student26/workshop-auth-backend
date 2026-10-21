## Referencia

- Issues de trabajo en `user-bcp-student26/workshop-auth-backend`:
  - [#13](https://github.com/user-bcp-student26/workshop-auth-backend/issues/13): H-01, SQL de autenticación concatenado. Severidad alta.
  - [#14](https://github.com/user-bcp-student26/workshop-auth-backend/issues/14): H-02, contraseñas en texto plano. Severidad alta.
  - [#15](https://github.com/user-bcp-student26/workshop-auth-backend/issues/15): H-03, token Base64 sin firma y con secreto embebido. Severidad alta.
  - [#16](https://github.com/user-bcp-student26/workshop-auth-backend/issues/16): H-04, secreto fijo y CORS abierto. Severidad media.
  - [#12](https://github.com/user-bcp-student26/workshop-auth-backend/issues/12): H-05, el 500 expone detalles internos. Severidad media.
- Hallazgos de origen: [HALLAZGOS.md](../../../HALLAZGOS.md), H-01 a H-05. Campo `Hallazgo origen` en el Project ReqFlow (proyecto 5).
- Contrato baseline: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md), sección `POST /auth/login`.
- Nombre del cambio: se conserva `hu-01-token-firmado-claims` por ser el nombre ya creado. El alcance real son los Issues #12 a #16.

## Por qué

`POST /auth/login` tiene cinco defectos que permiten acceso no autorizado o divulgan información interna: la consulta concatena credenciales (H-01), las contraseñas están en texto plano (H-02), el token es Base64 sin firma con el secreto dentro (H-03), el secreto está fijo y CORS acepta cualquier origen (H-04), y el 500 expone el nombre de la excepción y su mensaje (H-05). Los consumidores dependen de ese contrato, así que cada cambio debe ser explícito y verificable.

## Cambios de comportamiento frente al contrato

Solo se listan los cambios de los Issues. El resto del contrato se preserva.

| Aspecto | Contrato actual (`docs/ENDPOINTS.md`) | Cambio propuesto | Issue |
|---|---|---|---|
| Consulta de login | Sin cambio observable | La consulta deja de concatenar credenciales | #13 |
| Contraseñas | Documenta Argon2id y `PreparedStatement`; el código guarda y compara en claro | Solo se almacena un hash con sal | #14 |
| `token` | Base64 de `username\|role\|expiry\|SECRET`, no JWT | Token firmado y verificable; cambia el formato | #15 |
| Secreto de firma | Fijo en `LegacyTokenUtil` | Viene de configuración externa, sin valor por defecto en el código | #16 |
| CORS | `origins=/.*/` | Solo orígenes listados por entorno | #16 |
| `500` | `message` y `exception` con el nombre completo de la clase | Mensaje genérico, sin nombre de clase; la excepción se registra en el log | #12 |

Nota: el contrato afirma Argon2id, pero el código no lo usa. Este cambio cumple el requisito de almacenamiento seguro; no conserva la afirmación actual del código.

## Alcance

- Parametrizar la consulta de autenticación (#13).
- Almacenar contraseñas como hash con sal y verificarlas en el login, con migración atómica de la base existente (#14).
- Emitir un token firmado con clave fuera del código fuente y un verificador testeable (#15).
- Leer el secreto de configuración externa y restringir CORS por entorno (#16).
- Devolver un 500 sin detalles internos y registrar la excepción en el log del servidor (#12).

## Fuera de alcance

- Claims de HU-01 (`client` obligatorio, `sub`, `client`, `iat`, `exp` y `expiresIn` calculado). Se tratan en el cambio aparte `hu-01-claims-token`.
- Endpoint de verificación de tokens y cualquier endpoint distinto de `POST /auth/login`.
- Refresh, revocación o cierre de sesión.
- Activar `TokenService` RS256.
- Retirar el campo `debug` de la respuesta.
- Cambiar `GET /q/health`.
- Hallazgo H-06 (dependencias) y H-07 (refactor del Resource).
- Auditoría de seguridad exhaustiva.
- Diseño técnico: algoritmos, clases y librerías. Corresponde a la fase de diseño.

## Comportamiento que debe preservarse

- Ruta y verbo `POST /auth/login`; `Content-Type` y `Accept` `application/json`.
- Orden de validación: solicitud nula, luego `username`, luego `password`. Mensajes exactos `Request is null`, `Username is required` y `Password is required`, con status `400`.
- `401` con `{"message":"Invalid user <username> or password"}` sin `token` para usuario inexistente, contraseña incorrecta o fallo de consulta.
- Errores del framework (400 por JSON malformado, 405, 406, 415) con cuerpo vacío.
- `200` con `user.username`, `user.displayName` escapado con `escapeHtml4`, `user.role`, `expiresIn` numérico `3600` y `debug` con valor `authenticated-with-legacy-flow`.
- La identidad procede de la base de datos, no de campos enviados por el cliente.
- Las respuestas no incluyen `id`, contraseña ni hash.
- `client` sigue siendo opcional y no interviene en la respuesta.
- Las cuentas `student` y `trainer` siguen autenticando con sus credenciales de laboratorio.

## Criterios de verificación

- Las pruebas de `POST /auth/login` (200, 400, 401) y `GET /q/health` deben pasar. Las de login y salud están en `AuthResourceTest` y `HealthResourceTest`; solo cambian las expectativas que un Issue modifica, como el `500` de #12.
- Cada Issue añade pruebas que fallan antes del cambio y pasan después.
- Las pruebas usan `AUTH_DB_URL` apuntando a un archivo bajo `target/`, nunca a la base de desarrollo.

## Decisiones tomadas

- El alcance son los Issues #12 a #16. Los claims de HU-01 se tratan en `hu-01-claims-token`.
- La verificación del token es una función interna probada por pruebas, sin endpoint.
- Cada cambio de contrato es intencional y debe reflejarse en la especificación correspondiente y en `docs/ENDPOINTS.md` al implementar.
- El cambio de formato del token no se despliega hasta confirmar con los consumidores.

## Preguntas abiertas

1. **Consumidores del token:** confirmar con los dueños del frontend Angular y de la API Python que no decodifican el Base64 actual. Bloquea el despliegue, no la especificación.
2. **Migración de contraseñas:** #14 exige documentar la migración. Falta decidir si ocurre al arrancar o con una herramienta aparte.
3. **Algoritmos:** la elección del algoritmo de hash y de firma queda para la fase de diseño.
4. **Mensaje del 401:** incluye el `username` enviado. No está en los Issues; se preserva por contrato y puede tratarse como hallazgo aparte.
