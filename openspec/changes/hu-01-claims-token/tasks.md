## 1. Caracterización (antes de cambiar comportamiento)

- [ ] 1.1 Añadir prueba de login válido para `student` y `trainer`, verificando `token`, `expiresIn`, `user` y `debug`.
- [ ] 1.2 Añadir pruebas de `400` para solicitud nula, `username` ausente, vacío o solo blancos, y `password` ausente, vacío o solo blancos, con mensajes exactos y orden `username` antes que `password`.
- [ ] 1.3 Añadir pruebas de `401` para usuario inexistente y password incorrecto, verificando mensaje exacto y ausencia de `token`.
- [ ] 1.4 Añadir pruebas de errores del framework (JSON malformado, GET, `Accept: text/plain`, `Content-Type: text/plain`) verificando status y cuerpo vacío.
- [ ] 1.5 Añadir prueba de `displayName` con caracteres HTML escapados, insertando un usuario de prueba.
- [ ] 1.6 Añadir doble de `LegacyDatabase` que lance excepción y prueba de `500` con `message` y `exception`, incluyendo el caso `Unknown error`.
- [ ] 1.7 Ejecutar la suite completa y confirmar que todas las pruebas de caracterización pasan sin cambios en el código de producción.

## 2. Validación de `client`

- [ ] 2.1 Escribir pruebas que fallen: `client` ausente, vacío o solo blancos devuelve `400` con `Client is required`.
- [ ] 2.2 Escribir prueba que falle: `client` de más de 64 caracteres devuelve `400` con `Client must be at most 64 characters`.
- [ ] 2.3 Implementar la validación en `AuthResource` después de la validación de `password`, usando `trim()` solo para la comprobación de vacío.
- [ ] 2.4 Confirmar que las pruebas de caracterización de la fase 1 siguen en verde, salvo las que incluyan login sin `client`; actualizarlas para enviar `client` válido.

## 3. Emisión de claims firmados (JWS HS256 con JDK)

- [ ] 3.1 Añadir pruebas unitarias del emisor con `Clock` fijo: claims `sub`, `client`, `iat` y `exp` presentes, y `exp - iat` igual a 3600.
- [ ] 3.2 Añadir prueba de firma: el token verifica con la clave correcta y falla con otra clave o con el payload alterado.
- [ ] 3.3 Añadir prueba de token con `exp` en el pasado, marcado como expirado.
- [ ] 3.4 Implementar el emisor JWS HS256 con `javax.crypto` y Base64url, sin dependencias nuevas en `pom.xml`.
- [ ] 3.5 Verificar que las pruebas unitarias pasan.

## 4. Integración en el Resource

- [ ] 4.1 Escribir prueba que falle: la respuesta `200` contiene un token en formato JWS con los claims esperados.
- [ ] 4.2 Escribir prueba que falle: `expiresIn` coincide con `exp - iat` dentro de 5 segundos.
- [ ] 4.3 Reemplazar la llamada a `LegacyTokenUtil` en `AuthResource` por el emisor nuevo, sin cambiar la forma de la respuesta.
- [ ] 4.4 Eliminar `LegacyTokenUtil` cuando ya no tenga usos, o dejarlo sin referencias si hay que retirarlo en otro paso.
- [ ] 4.5 Confirmar que las pruebas de caracterización siguen en verde.

## 5. Configuración del secreto

- [ ] 5.1 Escribir prueba que falle: en perfil `prod` sin `AUTH_TOKEN_SECRET` el arranque falla.
- [ ] 5.2 Escribir prueba que falle: un secreto de menos de 32 bytes impide el arranque en cualquier perfil.
- [ ] 5.3 Configurar `AUTH_TOKEN_SECRET` en `application.properties` por perfil: valor de laboratorio solo en `dev` y `test`, con comentario que indica que no es válido fuera del laboratorio.
- [ ] 5.4 Eliminar el secreto embebido en el código.
- [ ] 5.5 Verificar que las pruebas de configuración pasan y que la aplicación arranca en `dev` y `test`.

## 6. Redacción de logs

- [ ] 6.1 Escribir prueba que capture la salida de error durante un login válido y verifique que no aparecen el token ni el secreto.
- [ ] 6.2 Escribir prueba equivalente para el caso de excepción con `500`.
- [ ] 6.3 Confirmar que el emisor y el Resource no registran el token ni el secreto.
- [ ] 6.4 Registrar como riesgo, sin corregir, si `LegacyDatabase.printStackTrace` imprime la contraseña por la consulta concatenada.

## 7. Verificación final y documentación

- [ ] 7.1 Ejecutar la suite completa y confirmar que todas las pruebas pasan.
- [ ] 7.2 Ejecutar `openspec validate hu-01-token-firmado-claims --strict`.
- [ ] 7.3 Actualizar [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) con el contrato nuevo: token firmado, `client` obligatorio, `expiresIn` calculado y secreto externo.
- [ ] 7.4 Confirmar con los dueños del frontend Angular y de la API Python que no decodifican el Base64 actual. Este paso bloquea el despliegue, no el desarrollo.
- [ ] 7.5 Revisar que no se modificó `LegacyDatabase`, `TokenService` ni otros endpoints.
