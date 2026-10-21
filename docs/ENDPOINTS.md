# Contrato HTTP del backend

Contrato del codigo actual, verificado el 2026-10-06. Base URL local por defecto:
`http://localhost:8080`. Puerto y host se configuran en
[src/main/resources/application.properties:1](../src/main/resources/application.properties#L1).
Los ejemplos HTTP observados proceden de Quarkus 3.27.4, perfil prod, puerto
temporal y SQLite aislada; no se uso la base del laboratorio. Los errores 500
del Resource se contrastaron con sus pruebas de caracterizacion.

Este documento describe implementacion y defectos actuales, no una API ideal.
Los problemas pendientes se siguen solo en [HALLAZGOS.md](../HALLAZGOS.md).

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de un usuario del laboratorio y devuelve su identidad,
rol y un token para entregar a los consumidores del sistema.
Ruta y metodo: [src/main/java/com/bcp/workshop/auth/AuthResource.java:12](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L12),
[src/main/java/com/bcp/workshop/auth/AuthResource.java:24](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L24).

### 2. Autenticacion y headers

No requiere autenticacion previa ni header `Authorization`. La identidad se
comprueba con username/password del cuerpo, no con Basic Auth ni con un token
de entrada ([src/main/java/com/bcp/workshop/auth/AuthService.java:17](../src/main/java/com/bcp/workshop/auth/AuthService.java#L17)).

Enviar `Content-Type: application/json` y `Accept: application/json`.
El Resource consume y produce JSON
([src/main/java/com/bcp/workshop/auth/AuthResource.java:13](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L13)).
Las respuestas JSON observadas llevan `application/json; charset=UTF-8`.

### 3. Request

Sin parametros de ruta ni query definidos por este endpoint. El cuerpo es un
objeto JSON con los siguientes campos:

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | si | Nombre exacto del usuario. No admite null, cadena vacia ni solo blancos segun String.trim(). |
| password | string | si | Password exacto. No admite null, cadena vacia ni solo blancos segun String.trim(). |
| client | string | no | Identificador del cliente; puede omitirse y no interviene en autenticacion ni en la respuesta. |

DTO: [src/main/java/com/bcp/workshop/auth/LoginRequest.java:4](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L4).
Validacion, en orden request, username y password:
[src/main/java/com/bcp/workshop/auth/AuthResource.java:38](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L38).
`trim()` solo detecta vacios; no recorta las credenciales usadas para autenticar.
La comparacion distingue mayusculas/minusculas con el esquema SQLite actual.
No hay normalizacion Unicode ni longitud maxima en la validacion del Resource.
Los limites de transporte no estan fijados en estas fuentes.

Los campos desconocidos se ignoran
([src/main/resources/application.properties:8](../src/main/resources/application.properties#L8)).
Las propiedades obligatorias ausentes se deserializan como null y provocan 400.

Ejemplo JSON completo, consultado con credenciales locales de prueba:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Response exitosa

**200 OK**, cuerpo JSON. La identidad procede de SQLite, no de campos enviados
por el cliente. No se devuelven id, password, hash ni client.

| campo | tipo | descripcion |
|---|---|---|
| token | string | Token no vacio generado por el emisor legacy; no es JWT. Cambia con el instante de emision. |
| expiresIn | integer | Valor 3600, en segundos; duracion declarada del token, no prueba de validacion por consumidores. |
| user | object | Datos publicos del usuario autenticado. |
| user.username | string | Username almacenado. |
| user.displayName | string | Nombre visible almacenado, escapado con escapeHtml4. Por ejemplo, `<Student & Trainer>` se devuelve como `&lt;Student &amp; Trainer&gt;`. |
| user.role | string | Rol almacenado. Los seeds usan STUDENT y TRAINER; el codigo no define un enum ni restringe todos los usuarios a esos dos valores. |
| debug | string | Valor literal `authenticated-with-legacy-flow`, que sigue presente tras el refactor. |

Evidencia: [src/main/java/com/bcp/workshop/auth/LoginResponse.java:3](../src/main/java/com/bcp/workshop/auth/LoginResponse.java#L3),
[src/main/java/com/bcp/workshop/auth/UserResponse.java:5](../src/main/java/com/bcp/workshop/auth/UserResponse.java#L5),
[src/main/java/com/bcp/workshop/auth/TokenPolicy.java:4](../src/main/java/com/bcp/workshop/auth/TokenPolicy.java#L4),
[src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:53](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L53).

Ejemplo JSON real y completo capturado el 2026-10-06 para la entrada anterior.
El token es un snapshot de laboratorio, no un token vigente para reutilizar:

```json
{
  "token": "c3R1ZGVudHxTVFVERU5UfDE3OTEzMDc5OTd8V09SS1NIT1BfU0VDUkVUXzIwMjY=",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

Las respuestas del Resource son JSON. Los errores del framework observados
antes del metodo pueden tener cuerpo vacio y no incluyen el campo message.
El cliente debe comprobar el status y si hay cuerpo antes de deserializar JSON.

**400 Bad Request: request null.** Ocurre con cuerpo JSON `null` o cuerpo vacio
en las consultas realizadas; la rama aplica cuando el metodo recibe null.

```json
{"message":"Request is null"}
```

**400 Bad Request: username requerido.** Campo ausente, null, vacio o solo blancos.
Se valida antes que password, incluso si ambos faltan.

```json
{"message":"Username is required"}
```

**400 Bad Request: password requerido.** Username supera su validacion pero
password esta ausente, null, vacio o solo blancos.

```json
{"message":"Password is required"}
```

Estos tres mensajes proceden de
[src/main/java/com/bcp/workshop/auth/AuthResource.java:16](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L16)
y de sus guard clauses en
[src/main/java/com/bcp/workshop/auth/AuthResource.java:38](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L38).

**401 Unauthorized: no se pudo autenticar.** Usuario inexistente, password
incorrecto o hash rechazado producen el mismo mensaje. El username enviado
se concatena literalmente en message. Ejemplo con student y password `wrong`:

```json
{"message":"Invalid user student or password"}
```

Tambien ocurre si la consulta SQLite falla: el repositorio captura el error y
devuelve null. No interpretar este 401 como prueba de que la base funciona.
Evidencia: [src/main/java/com/bcp/workshop/auth/AuthResource.java:55](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L55),
[src/main/java/com/bcp/workshop/auth/AuthService.java:19](../src/main/java/com/bcp/workshop/auth/AuthService.java#L19),
[src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:153](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L153).

**500 Internal Server Error: excepcion capturada por el Resource.** Devuelve
`message` (string, mensaje de excepcion o `Unknown error` si es null) y
`exception` (string, nombre completo de la clase Java).
Ejemplos completos verificados con IllegalStateException en el doble de las
pruebas, no atribuidos a un payload HTTP especifico:

```json
{"message":"test failure","exception":"java.lang.IllegalStateException"}
```

Si la misma excepcion no tiene mensaje:

```json
{"message":"Unknown error","exception":"java.lang.IllegalStateException"}
```

Evidencia: [src/main/java/com/bcp/workshop/auth/AuthResource.java:63](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L63),
[src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:64](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L64).
Los ejemplos no limitan el tipo o texto de otras excepciones posibles.

**Errores del framework, observados en perfil prod:**

| codigo | cuando ocurre | ejemplo de cuerpo real |
|---|---|---|
| 400 Bad Request | JSON malformado, por ejemplo `{`, o tipo incompatible, por ejemplo username como objeto `{}`. | Cuerpo vacio: cero bytes; sin JSON ni Content-Type de respuesta. |
| 405 Method Not Allowed | GET sobre /auth/login en vez de POST. | Cuerpo vacio: cero bytes; sin JSON ni Content-Type de respuesta. |
| 406 Not Acceptable | Accept: text/plain, incompatible con la respuesta JSON del Resource. | Cuerpo vacio: cero bytes; sin JSON ni Content-Type de respuesta. |
| 415 Unsupported Media Type | Content-Type: text/plain en vez de application/json. | Cuerpo vacio: cero bytes; sin JSON ni Content-Type de respuesta. |

Ejemplo HTTP real de 415; la linea vacia despues del status representa que no
hay cuerpo. No se debe sustituir por `{}`, `null` ni un objeto message:

```http
HTTP/1.1 415 Unsupported Media Type

```

Preguntas abiertas: que limites de tamano/tiempo y respuestas de proxy se
configuraran al desplegar, y que cuerpo devuelve cada error del framework en
otros perfiles/versiones. El repositorio no los fija; no se prometen codigos o
esquemas adicionales. Si el arranque de SQLite falla, el proceso no inicia:
no existe una respuesta HTTP de login para ese fallo
([src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:60](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L60)).

### 6. Notas de seguridad

- El token actual NO esta firmado. Es Base64 de bytes UTF-8 de
  `username|role|expiry|SECRET`. expiry es epoch en segundos calculado a una hora
  de la emision; no hay validacion de ese timestamp en este backend emisor.
  No afirmar que expira de manera efectiva en todos los consumidores.
  [src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:13](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13).
- Existe TokenService RS256, pero el login NO lo llama: sigue invocando
  LegacyTokenUtil. El cliente no debe intentar interpretar el token como JWT.
  [src/main/java/com/bcp/workshop/auth/AuthService.java:30](../src/main/java/com/bcp/workshop/auth/AuthService.java#L30).
  Pregunta abierta: cuando se activara JWT y que consumidores verificaran firma,
  issuer, rol y expiracion. No se inspeccionaron otros repositorios.
- Passwords se verifican con Argon2id y salt aleatorio, y username se consulta
  con PreparedStatement. Nunca se devuelve el hash.
  [src/main/java/com/bcp/workshop/auth/PasswordHasher.java:20](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L20),
  [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:148](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L148).
  El saneamiento de WAL sigue pendiente segun [HALLAZGOS.md](../HALLAZGOS.md).
- Los 500 exponen detalles internos; debug y el secreto de laboratorio dentro
  del token siguen presentes. No enviar ni registrar tokens/passwords reales
  usando este laboratorio. No hay evidencia de rate limiting en estas fuentes.
- CORS acepta cualquier origen por configuracion; no es control de acceso.
  [src/main/resources/application.properties:5](../src/main/resources/application.properties#L5).
  El servicio esta destinado solo a entornos locales y aislados
  ([README.md](../README.md)), no a publicacion en Internet.

## GET /q/health

### 1. Que hace

Permite a un operador o monitor comprobar que el proceso responde al chequeo
de salud agregado, sin autenticar a un usuario. Lo proporciona la extension
quarkus-smallrye-health, no un Resource propio
([pom.xml:49](../pom.xml#L49)).

### 2. Autenticacion y headers

No requiere autenticacion ni header `Authorization` en la configuracion actual.
Se consulto sin ese header y tambien con `Authorization: Bearer test-only-not-a-token`:
ambas consultas devolvieron 200. Ese header no convierte la consulta en una
validacion de token.

Enviar `Accept: application/json`. No se necesita Content-Type porque no hay
cuerpo de request. Respuesta observada: `application/json; charset=UTF-8`.
El handler tambien devolvio JSON con Accept: text/plain: no extrapolar el 406
del login a salud.

### 3. Request

Metodo GET, sin campos, parametros de ruta ni query documentados por el codigo
de la aplicacion. No enviar credenciales ni cuerpo.

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | no aplica | no aplica | La consulta de salud no tiene un DTO ni cuerpo JSON de entrada. |

**Ejemplo JSON de request:** no existe. El ejemplo real tiene cuerpo ausente;
escribir `{}` o `null` inventaria una entrada JSON no requerida. Peticion completa:

```http
GET /q/health HTTP/1.1
Host: localhost:8080
Accept: application/json

```

Los parametros adicionales no forman parte del contrato: la consulta observada
con `?client=test-only` no altero la respuesta, pero no constituye una API de filtros.

### 4. Response exitosa

**200 OK**, cuerpo JSON. Con las dependencias y fuentes actuales, la consulta
verificada devuelve UP y un array checks vacio:

| campo | tipo | descripcion |
|---|---|---|
| status | string | Valor observado `UP`; estado agregado informado por el framework, no estado de un usuario ni validacion de credenciales. |
| checks | array | `[]` en la ejecucion verificada. No hay HealthCheck propio de SQLite/autenticacion en las fuentes. No se inventan campos de elementos inexistentes. |

Ejemplo JSON real y completo:

```json
{
  "status": "UP",
  "checks": []
}
```

El esquema observado procede del handler de la extension incluida en
[pom.xml:49](../pom.xml#L49); version Quarkus en [pom.xml:13](../pom.xml#L13).
No hay implementacion local que garantice un detalle diferente de checks.

### 5. Respuestas de error

**No se identifica una respuesta de error especifica de la aplicacion para este
endpoint en las fuentes actuales.** Las consultas verificadas devolvieron 200,
incluidas las que omitieron Authorization o enviaron uno invalido.
No atribuir 401/403 a este endpoint por analogia con login.

El handler tambien devolvio 200 para POST /q/health sin cuerpo. Por tanto,
no documentar un 405 de salud que no se verifico; la integracion debe usar GET.

| codigo | cuando ocurre | ejemplo de cuerpo |
|---|---|---|
| No definido por la aplicacion | Fallo interno del handler, futuros checks DOWN o infraestructura/proxy. No se reprodujo ni se define un mapper local. | No hay cuerpo de error verificable en estas fuentes; pregunta abierta, no un JSON inventado. |

**Preguntas abiertas:** que checks se agregaran, que codigos y cuerpos produciran
sus fallos y que politica usara el monitor. No se publica un ejemplo 503/DOWN
como si ya ocurriera con checks vacio. Una negativa de conexion por proceso caido
no es una respuesta HTTP y no tiene codigo ni cuerpo JSON.

### 6. Notas de seguridad

- UP con checks vacio no demuestra que SQLite este accesible, que passwords se
  puedan verificar ni que el token sea seguro. No usar este resultado como
  prueba integral de autenticacion o de saneamiento de passwords.
- Si la inicializacion SQLite falla, este backend aborta el arranque; no hay
  endpoint disponible para responder un error de salud en ese caso
  ([src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:60](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L60)).
- Salud esta accesible sin autenticacion y CORS es permisivo. No exponer el
  laboratorio en Internet. El ejemplo no contiene secretos ni datos de usuario.
- Si se incorporan checks, revisar que sus detalles no revelen credenciales,
  URLs sensibles ni excepciones internas y actualizar el contrato con evidencia.

## Fuentes consultadas

Documentos: [README.md](../README.md), [HALLAZGOS.md](../HALLAZGOS.md) y la version
anterior de este contrato. Codigo: AuthResource, AuthService, LoginRequest,
LoginResponse, UserResponse, LegacyDatabase, PasswordHasher, LegacyTokenUtil,
TokenPolicy, AuthResourceTest, application.properties y pom.xml, enlazados arriba.

Respuestas HTTP contrastadas contra el artefacto empaquetado de este backend,
perfil prod y SQLite aislada. Evidencia generada de las consultas:
[target/docs-http-observations.json](../target/docs-http-observations.json).
Las respuestas de salud y errores del framework son observaciones de esta version,
no implementaciones Java locales ni promesas sobre futuros perfiles o proxies.