## Contexto técnico

- `POST /auth/login` está en [AuthResource.java](../../../src/main/java/com/bcp/workshop/auth/AuthResource.java). Valida `request`, `username` y `password` en ese orden y llama a `LegacyDatabase.findByCredentials`.
- El token lo genera `LegacyTokenUtil.create(username, role)`: Base64 de `username|role|expiry|SECRET`, con `SECRET` constante en el código y `expiresIn` fijo en 3600 en el Resource.
- `LoginRequest` ya expone el campo `client`, pero el Resource no lo lee.
- No hay dependencia JWT en [pom.xml](../../../pom.xml). Las dependencias actuales son Quarkus REST/Jackson, ARC, health, sqlite-jdbc y commons-text.
- `application.properties` solo fija host, puerto, CORS y `fail-on-unknown-properties=false`. No define secretos.
- Discrepancias con [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md), detectadas al revisar el código:
  - El contrato menciona `AuthService`, `TokenService`, `TokenPolicy`, `PasswordHasher`, `LoginResponse` y `UserResponse`. No existen en `src/main`. Se usa el código real como fuente de verdad.
  - El contrato dice que el login usa Argon2id y `PreparedStatement`. `LegacyDatabase.findByCredentials` concatena `username` y `password` en el SQL y compara la contraseña en texto plano.
  - El contrato referencia `AuthResourceTest.java:64` para el 500. El test actual solo tiene un caso de login exitoso.

## Goals

- Emitir un token firmado con claims `sub`, `client`, `iat` y `exp`.
- Validar `client` como obligatorio, no vacío y de máximo 64 caracteres.
- Calcular `expiresIn` como `exp - iat` con tolerancia de 5 segundos. El valor numérico sigue siendo 3600.
- Leer la clave de firma desde `AUTH_TOKEN_SECRET`, sin valor por defecto fuera de laboratorio.
- Impedir que token o secreto aparezcan en logs.
- Preservar los contratos listados en la propuesta.

## Non-goals

- Cambiar `LegacyDatabase`, el esquema SQLite o la verificación de contraseñas. Son hallazgos separados.
- Activar `TokenService` RS256 o diseñar gestión de claves asimétricas.
- Crear un endpoint o componente de verificación de tokens.
- Refresh, revocación o cierre de sesión.
- Cambiar `GET /q/health`.
- Crear `tasks.md`. Esta fase no lo incluye.

## Contratos protegidos

| Contrato | Fuente | Tratamiento |
|---|---|---|
| Ruta, verbo y media types | `AuthResource` | Sin cambios |
| Orden de validación y mensajes `Request is null`, `Username is required`, `Password is required` | `AuthResource` | Sin cambios; `client` se valida después de `password` |
| `401` con `Invalid user <username> or password` | `AuthResource` | Sin cambios |
| `500` con `message` y `exception` | `AuthResource` | Sin cambios |
| Errores del framework 400/405/406/415 con cuerpo vacío | Quarkus | Sin cambios; se cubren con pruebas |
| `200` con `user.displayName` escapado y `debug` | `AuthResource` | Sin cambios |
| `expiresIn` numérico 3600 | `AuthResource` | Se mantiene el valor, ahora derivado de `exp - iat` |
| Token Base64 sin firma | `LegacyTokenUtil` | Cambio intencional aprobado en el Issue #11 |
| `client` opcional | Contrato | Cambio intencional: pasa a obligatorio |

## Componentes afectados

- `AuthResource`: lee `client`, valida longitud, calcula `expiresIn` desde el token emitido.
- `LegacyTokenUtil`: sustituido o reescrito para emitir el token firmado. Es el único punto que cambia el formato.
- Nuevo componente de emisión de claims con reloj inyectable (`java.time.Clock`) para pruebas deterministas.
- `application.properties`: configuración de `AUTH_TOKEN_SECRET` por perfil.
- `src/test/.../AuthResourceTest.java` y pruebas nuevas: caracterización y cambios.
- `docs/ENDPOINTS.md`: actualizar después de implementar, no en esta fase.

Sin cambios previstos en `LegacyDatabase`, `LoginRequest` ni `UserRecord`.

## Decisiones técnicas

### D1. Formato del token

| Opción | Pros | Contras |
|---|---|---|
| A. JWS HS256 con JDK (`javax.crypto` + Base64url) | Sin dependencias nuevas; claims estándar; formato JWT reconocible | Implementación propia de firma: hay que probarla bien |
| B. Librería JWT (por ejemplo, Nimbus JOSE o SmallRye JWT) | Menos código propio; validación lista | Dependencia nueva; aumenta superficie de revisión |
| C. RS256 con `TokenService` existente | Ya hay código en el repositorio según el contrato | Ese código no está en `src/main`; requiere gestión de claves privadas |
| D. Mantener Base64 y solo añadir claims | Mínimo cambio | Sin firma; el secreto sigue expuesto si se usa como clave |

**Decidido: opción A, JWS HS256 con JDK** (`javax.crypto` y Base64url, sin dependencias nuevas). Las opciones B y C quedan descartadas para esta historia.

### D2. Secreto de firma

- `AUTH_TOKEN_SECRET` se lee por configuración de Quarkus, sin valor por defecto en el perfil `prod`.
- En `dev` y `test` se usa un valor de laboratorio declarado en `application.properties` con un comentario que indica que no es válido fuera del laboratorio.
- Si falta en `prod`, el proceso no arranca. Es preferible a aceptar logins sin firma válida.
- El secreto debe tener al menos 32 bytes (256 bits) para HS256; si es más corto, el arranque falla en todos los perfiles.

Alternativa descartada: validar en tiempo de ejecución en cada login. Retrasa el fallo y deja el servicio levantado sin poder emitir tokens.

### D3. Cálculo de `expiresIn`

Se emite con `iat = now` y `exp = now + 3600`. La respuesta devuelve `exp - iat`, que es 3600 y cumple la tolerancia de 5 segundos. El valor numérico se conserva para no romper consumidores.

### D4. Validación de `client`

Se valida después de `password`, para no cambiar el orden de los errores existentes. La comprobación de vacío usa `trim()`, pero el valor almacenado en el claim `client` es el recibido sin recortar.

- Vacío o solo blancos: `400` con `{"message":"Client is required"}`.
- Más de 64 caracteres: `400` con `{"message":"Client must be at most 64 characters"}`.

Son los únicos mensajes nuevos.

### D5. Logs

No se añade ningún log con token, secreto ni payload del token. Una prueba captura la salida de error durante un login válido y otro con excepción, y verifica que no aparecen el token ni el secreto. Si `LegacyDatabase.printStackTrace` imprime la contraseña por la consulta concatenada, se documenta como riesgo y no se corrige aquí.

## Estrategia incremental

1. **Caracterización (antes de tocar comportamiento):** pruebas que pasan con el código actual:
   - login válido de `student` y `trainer`;
   - `400` por solicitud nula, `username` vacío o solo blancos, y `password` vacío;
   - `401` por usuario inexistente y password incorrecto;
   - `200` con forma de respuesta: `token`, `expiresIn`, `user`, `debug`;
   - errores del framework: JSON malformado, GET, `Accept: text/plain`, `Content-Type: text/plain`, con cuerpo vacío.
   - Estas pruebas deben pasar sin cambios de código.
2. **Validación de `client`:** añadir validación y pruebas de `400` por ausencia y longitud.
3. **Emisión de claims firmados:** nueva clase de emisión con `Clock` inyectado. Pruebas unitarias de firma, claims y expiración con reloj fijo.
4. **Integración en el Resource:** sustituir `LegacyTokenUtil`, calcular `expiresIn` desde el token emitido.
5. **Configuración del secreto:** perfiles `dev`/`test`/`prod` y prueba de arranque sin secreto en `prod`.
6. **Redacción:** pruebas que capturan logs y verifican que no aparecen token ni secreto.
7. **Documentación:** actualizar `docs/ENDPOINTS.md` con el contrato nuevo.

Cada paso debe dejar la suite en verde antes del siguiente.

## Pruebas de caracterización necesarias

Antes de modificar el Resource, deben existir y pasar con el código actual:

- Login exitoso con `student` y con `trainer`, incluyendo `debug` y `user`.
- Las tres validaciones `400` con sus mensajes exactos y el orden: `username` antes que `password`.
- `401` con mensaje exacto y sin `token`.
- Errores del framework con cuerpo vacío.
- Caso de `displayName` con caracteres HTML escapados. Los seeds no lo incluyen, por lo que requiere insertar un usuario de prueba o usar un doble.
- Caso `500`: el contrato lo describe con un doble, pero el repositorio no tiene ese test. Debe crearse un doble de `LegacyDatabase` que lance excepción.

## Riesgos y trade-offs

- **Consumidores del token (pendiente, bloquea despliegue):** el frontend Angular y la API Python consumen el token según el README. Hay que confirmar con sus dueños que no decodifican el Base64 actual antes de desplegar. Este repositorio no tiene consumidores del token.
- **Inyección SQL en `LegacyDatabase`:** la consulta concatena credenciales. Fuera de alcance de esta fase, pero no debe considerarse seguro. Se registra como hallazgo y no se corrige aquí porque la propuesta excluye `LegacyDatabase`.
- **Contraseñas en texto plano:** el contrato dice que se usa Argon2id; el código no lo hace. Esta discrepancia es un riesgo de seguridad independiente del token.
- **Firma propia (opción A):** introduce código criptográfico nuevo. Mitigación: pruebas con vectores conocidos y revisión de pares.
- **Reloj:** `exp` depende del reloj del servidor. La tolerancia de 5 segundos cubre latencia, no desajustes grandes de reloj.
- **Secreto de laboratorio en `dev`/`test`:** se puede filtrar si el perfil se usa fuera del laboratorio. Mitigación: fallo de arranque en `prod`.

## Rollback

- El cambio no toca base de datos ni esquema: el rollback es revertir el commit y redesplegar la versión anterior.
- Los tokens emitidos con el formato nuevo no los valida el backend actual, así que no hay estado persistente que limpiar.
- Si se rompe un consumidor, el rollback restablece el token Base64 sin firma. Esa es la razón para confirmar consumidores antes de desplegar.
- La variable `AUTH_TOKEN_SECRET` puede dejarse configurada: la versión anterior no la lee.
