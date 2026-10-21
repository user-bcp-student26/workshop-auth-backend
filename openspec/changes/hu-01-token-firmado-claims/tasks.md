## 0. Preparación

- [x] 0.1 Configurar las pruebas para que usen `AUTH_DB_URL` apuntando a un archivo bajo `target/`, nunca a la base de desarrollo.
- [x] 0.2 Confirmar que `mvn -B test` pasa antes de cualquier cambio. Puerto de pruebas fijado en `application.properties` (`%test.quarkus.http.test-port=18081`) porque 8081 lo ocupa otro proceso.

## 1. Caracterización (sin cambio de comportamiento)

- [x] 1.1 Prueba de login válido para `student` y `trainer`: `token`, `expiresIn`, `user` y `debug`.
- [x] 1.2 Pruebas de `400`: solicitud nula, `username` ausente/vacío/blancos, `password` ausente/vacío/blancos, con mensajes exactos y orden.
- [x] 1.3 Pruebas de `401`: usuario inexistente y contraseña incorrecta, con mensaje exacto y sin `token`.
- [x] 1.4 Pruebas de errores del framework (JSON malformado, GET, `Accept: text/plain`, `Content-Type: text/plain`) con cuerpo vacío.
- [x] 1.5 Prueba de `displayName` con HTML escapado usando un usuario de prueba.
- [x] 1.6 Ejecutar la suite y confirmar que la caracterización pasa sin cambios de producción. Hecho: 24 pruebas de login, 0 fallos.
- [x] 1.7 Caracterización de `GET /q/health` (añadida a petición): 200 `UP` con `checks` vacío sin autenticación, ignora `Authorization`, responde JSON con `Accept: application/xml`, y `POST` responde igual por compatibilidad. Hecho: 4 pruebas, 0 fallos.

## 2. Consulta parametrizada (#13, H-01)

- [x] 2.1 Escribir prueba que falle: `username` o `password` con `' OR '1'='1` devuelve `401`, no `200`.
- [x] 2.2 Escribir prueba: credenciales válidas con apóstrofos en un usuario de prueba devuelven `200`.
- [x] 2.3 Cambiar la búsqueda en `LegacyDatabase` a `PreparedStatement` con `username` y `password` como parámetros enlazados. La comparación de `password` en SQL es temporal y se retira en 5.x.
- [x] 2.4 Verificar que la caracterización y las pruebas nuevas pasan.

## 3. Respuesta 500 sin detalles internos (#12, H-05)

- [x] 3.1 Escribir prueba que falle: el cuerpo `500` no contiene el nombre de la clase ni el mensaje original.
- [x] 3.2 Escribir prueba que falle: la excepción aparece en el log del servidor con su traza.
- [x] 3.3 Cambiar el `catch` de `AuthResource.login` a `{"message":"Internal error"}` y registrar la excepción.
- [x] 3.4 Verificar que las pruebas pasan.

## 4. Secreto externo y CORS (#16, H-04)

- [x] 4.1 Escribir prueba que falle: en `prod` sin `AUTH_TOKEN_SECRET`, el login no devuelve `token`.
- [x] 4.2 Escribir prueba que falle: un secreto de menos de 32 bytes impide el arranque.
- [x] 4.3 Escribir prueba que falle: un `Origin` no listado no recibe `Access-Control-Allow-Origin`; un origen listado sí lo recibe. Una petición preflight con método o cabecera fuera de la lista no recibe esos valores en `Access-Control-Allow-Methods` ni `Access-Control-Allow-Headers`.
- [x] 4.4 Configurar `auth.token.secret` desde `AUTH_TOKEN_SECRET` por perfil; valor de laboratorio solo en `dev` y `test`, con comentario de que no es válido fuera del laboratorio.
- [x] 4.5 Restringir CORS a orígenes por perfil y a los métodos y cabeceras del contrato.
- [ ] 4.6 Verificar que las pruebas pasan y que la aplicación arranca en `dev` y `test`. Pendiente: pruebas pasan en `test`; arranque en `dev` no verificado.

## 5. Hash de contraseñas y migración (#14, H-02)

- [x] 5.1 Escribir prueba que falle: el valor almacenado no es igual a la contraseña en claro y tiene el formato versionado.
- [x] 5.2 Escribir prueba que falle: dos usuarios con la misma contraseña tienen sales distintas.
- [x] 5.3 Escribir prueba que falle: contraseña correcta devuelve `200`, incorrecta `401`, hash con formato inválido `401` sin `token`.
- [x] 5.4 Escribir prueba que falle: la respuesta no incluye `password`, hash, sal ni `id`.
- [x] 5.5 Implementar PBKDF2-HMAC-SHA256 con sal aleatoria y formato versionado (`javax.crypto`).
- [x] 5.6 Escribir pruebas de migración: migración exitosa, y migración fallida que revierte la transacción sin usuarios a medias.
- [x] 5.7 Implementar la migración al arrancar con preflight de 10000 usuarios y transacción única.
- [x] 5.8 Documentar el procedimiento de respaldo y migración en el README.
- [x] 5.9 Verificar que las cuentas `student` y `trainer` siguen autenticando.

## 6. Token firmado y verificación interna (#15, H-03)

- [ ] 6.1 Escribir prueba que falle: el token de login tiene formato JWS HS256 y verifica con la clave configurada.
- [ ] 6.2 Escribir prueba que falle: un token con rol o usuario alterado es rechazado por la verificación interna.
- [ ] 6.3 Escribir prueba que falle: un token con `exp` vencido es rechazado como expirado.
- [ ] 6.4 Escribir prueba que falle: `expiresIn` sigue siendo `3600`.
- [ ] 6.5 Implementar la emisión HS256 con `javax.crypto` y Base64url, y la función interna de verificación con reloj inyectable.
- [ ] 6.6 Sustituir `LegacyTokenUtil` en `AuthResource` y eliminarlo cuando ya no tenga usos.
- [ ] 6.7 Escribir búsqueda de control: no hay clave de firma en código Java del repositorio.
- [ ] 6.8 Verificar que las pruebas pasan.

## 7. Verificación final y contrato

- [ ] 7.1 Ejecutar la suite completa y confirmar que todas las pruebas pasan.
- [ ] 7.2 Ejecutar `openspec validate hu-01-token-firmado-claims --strict`.
- [ ] 7.3 Actualizar [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) con: contraseñas con hash, consulta parametrizada, token firmado, secreto externo, CORS por entorno y `500` genérico.
- [ ] 7.4 Gate de despliegue (no bloquea el desarrollo): confirmar con los dueños del frontend Angular y de la API Python que no decodifican el Base64 actual. Bloquea solo el despliegue de 6.
- [ ] 7.5 Gate previo a la sección 6: fijar con `hu-01-claims-token` quién modifica `AuthResource` primero, para evitar conflictos.
