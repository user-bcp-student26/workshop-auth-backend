# Endpoints del backend de autenticacion

Contrato de la implementacion local revisada el 2026-10-19, no de cambios futuros: la consulta de credenciales esta parametrizada, pero las contrasenas siguen en claro y el token sigue siendo Base64 sin firma. No se modifico codigo para escribir este documento.

## Conexion y verificacion

- URL local por defecto: `http://127.0.0.1:8080`; perfil prod enlaza `0.0.0.0`. Evidencia: [application.properties:1](../src/main/resources/application.properties#L1), [application.properties:2](../src/main/resources/application.properties#L2) y [application.properties:3](../src/main/resources/application.properties#L3).
- Requisitos: JDK 21 y Maven 3.9+, segun [README.md](../README.md). En el POM actual, `mvn quarkus:dev` omite el arranque porque el plugin exige el goal build; se comprobo el arranque sin editar el POM con `mvn -Dquarkus.enforceBuildGoal=false quarkus:dev`. El plugin esta en [pom.xml:66](../pom.xml#L66).
- Para la comprobacion documental se uso loopback, puerto temporal 54629 y AUTH_DB_URL a una SQLite exclusiva bajo target. Los ejemplos HTTP siguientes usan el puerto por defecto, no el temporal. No apuntar las pruebas a una base compartida.
- Los cuerpos HTTP indicados como observados se consultaron sin Authorization, en perfil dev con Quarkus 3.27.4. El comportamiento del framework en otros perfiles se distingue como pregunta abierta cuando no esta caracterizado.

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de una cuenta de laboratorio y entrega su identidad, rol y un token para los consumidores del taller.

Ruta y metodo: [AuthResource.java:15](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15), [AuthResource.java:22](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L22) y [AuthResource.java:23](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L23).

### 2. Autenticacion y headers

No requiere sesion ni autenticacion previa. No hay un header Authorization obligatorio; no se requiere `Authorization: Bearer ...` ni Basic. Las credenciales se envian en el cuerpo. Esto se verifico con una peticion exitosa sin Authorization; AuthResource no declara restricciones de seguridad.

| Header | Valor para integrar | Obligatorio |
|---|---|---|
| Content-Type | application/json | Usarlo para que el cuerpo se procese como JSON. Se observo 415 con text/plain. |
| Accept | application/json | Recomendado; omitirlo funciona. application/xml produjo 406. |
| Authorization | Ninguno | No. |

Evidencia de consumo/produccion JSON: [AuthResource.java:16](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L16) y [AuthResource.java:17](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L17).

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Nombre exacto del usuario. Ausente, null, vacio o solo espacios se rechaza. Los espacios significativos no se recortan al consultar. |
| password | string | Si | Password exacto. Ausente, null, vacio o solo espacios se rechaza despues de username; no se recorta al consultar. |
| client | string | No | Identificador del cliente, por ejemplo angular-workshop. Se deserializa pero no interviene en autenticacion ni modifica la respuesta. |

Ejemplo JSON completo, observado con la cuenta publica del laboratorio:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

```http
POST /auth/login HTTP/1.1
Host: 127.0.0.1:8080
Content-Type: application/json
Accept: application/json

{"username":"student","password":"Workshop123!","client":"angular-workshop"}
```

Los campos desconocidos se ignoran. No hay en el Resource una longitud maxima, patron de complejidad ni normalizacion Unicode declarados; no se garantiza que el framework acepte cualquier tipo JSON como string. Evidencia: [LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3), [AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26), [AuthResource.java:29](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L29), [AuthResource.java:32](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L32), [AuthResource.java:35](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L35) y [application.properties:8](../src/main/resources/application.properties#L8).

### 4. Response exitosa

Estado: **200 OK**. Content-Type observado: `application/json; charset=UTF-8`.

| campo | tipo | descripcion |
|---|---|---|
| token | string | Token generado para username y role. Base64, no JWT firmado; cambia con el instante de emision. |
| expiresIn | number entero | Valor fijo 3600, expresado en segundos. No demuestra que el servidor valide tokens expirados. |
| user | object | Identidad devuelta; contiene exclusivamente los tres campos siguientes. |
| user.username | string | Nombre almacenado de la cuenta autenticada. |
| user.displayName | string | Nombre visible escapado con StringEscapeUtils.escapeHtml4. Por ejemplo, un ampersand se devuelve como &amp;. No volver a escapar indiscriminadamente al presentarlo. |
| user.role | string | Rol almacenado; las cuentas sembradas usan STUDENT y TRAINER. No hay enum que limite todos los valores posibles de la base. |
| debug | string | Literal authenticated-with-legacy-flow; forma parte de la respuesta actual. |

Ejemplo completo capturado en la instancia aislada; el token es un valor historico de laboratorio, no una credencial para reutilizar:

```json
{
  "expiresIn": 3600,
  "debug": "authenticated-with-legacy-flow",
  "user": {
    "displayName": "Workshop Student",
    "role": "STUDENT",
    "username": "student"
  },
  "token": "c3R1ZGVudHxTVFVERU5UfDE3OTI0NTA1NjR8V09SS1NIT1BfU0VDUkVUXzIwMjY="
}
```

El orden de propiedades no esta garantizado. No se devuelve id, password ni hash. Evidencia: [AuthResource.java:39](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39), [AuthResource.java:41](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L41), [AuthResource.java:43](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L43), [AuthResource.java:45](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L45) y [AuthResource.java:48](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L48). El contrato tambien se comprueba en [AuthResourceTest.java:128](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L128).

### 5. Respuestas de error

Errores construidos por AuthResource; Content-Type JSON:

| estado | cuando ocurre | ejemplo completo del cuerpo |
|---|---|---|
| 400 Bad Request | request null. Se observaron tanto cuerpo vacio como literal JSON null. | `{"message":"Request is null"}` |
| 400 Bad Request | username ausente, null, vacio o solo espacios; se comprueba antes que password. | `{"message":"Username is required"}` |
| 400 Bad Request | Username valido, pero password ausente, null, vacio o solo espacios. | `{"message":"Password is required"}` |
| 401 Unauthorized | Usuario inexistente o password incorrecto. Ejemplo observado con student y password incorrecto. Tambien se produce si persistencia captura una excepcion SQL y devuelve null. | `{"message":"Invalid user student or password"}` |
| 500 Internal Server Error | Una excepcion no absorbida por persistencia alcanza el catch de login. El ejemplo fue verificado con un doble de prueba, no provocado por una peticion HTTP de usuario. | `{"message":"Synthetic failure","exception":"java.lang.IllegalStateException"}` |
| 500 Internal Server Error | Misma rama, pero getMessage() es null; ejemplo comprobado mediante llamada directa en la prueba. | `{"message":"Unknown error","exception":"java.lang.IllegalStateException"}` |

message es string. En 401 incorpora literalmente el username recibido; no usar su texto para distinguir usuario ausente de password incorrecto. En 500, exception es string con el nombre completo de la clase y message puede variar segun el fallo real; Synthetic failure no es un literal de produccion. Solo existe message en 400/401; el 500 agrega exception. No hay campos de error, timestamp o correlationId adicionales definidos por el Resource.

Evidencia: [AuthResource.java:27](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L27), [AuthResource.java:30](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L30), [AuthResource.java:33](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L33), [AuthResource.java:37](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L37), [AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54), [LegacyDatabase.java:45](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L45), [LegacyDatabase.java:48](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L48) y [AuthResourceTest.java:154](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L154).

Errores anteriores al Resource observados en Quarkus dev:

| estado | cuando ocurre y peticion comprobada | ejemplo del cuerpo recibido |
|---|---|---|
| 400 Bad Request | JSON malformado: cuerpo `{` con application/json. | Cuerpo vacio, 0 bytes; sin Content-Type. |
| 400 Bad Request | Estructura incompatible con LoginRequest: cuerpo `[]` con application/json. | Cuerpo vacio, 0 bytes; sin Content-Type. |
| 415 Unsupported Media Type | POST con Content-Type text/plain y cuerpo test. | Cuerpo vacio, 0 bytes; sin Content-Type. |
| 406 Not Acceptable | POST JSON valido con Accept application/xml. | Cuerpo vacio, 0 bytes; sin Content-Type. |
| 405 Method Not Allowed | GET /auth/login en lugar del POST declarado. | Cuerpo vacio, 0 bytes; sin Content-Type. |

Un cuerpo de 0 bytes no es JSON `null`, `{}` ni una cadena con texto: no ejecutar un parser JSON incondicionalmente ante todo error. Ejemplo HTTP observado para el Content-Type incorrecto, sin cuerpo tras la linea en blanco:

```http
HTTP/1.1 415 Unsupported Media Type

```

**Preguntas abiertas:** confirmar cuerpos de errores del framework en prod y ante otras coerciones de tipos; confirmar limites operativos de tamano de request y fallos de infraestructura del entorno consumidor. No hay un esquema unico de error implementado que permita inventar ejemplos para esos casos. No se ha identificado una peticion normal que reproduzca el 500 sintetico; la rama esta definida y probada por llamada directa.

### 6. Notas de seguridad

- El token NO esta firmado: es Base64 UTF-8 de username, role, expiracion Unix y secreto fijo, separados por `|`. No es cifrado ni prueba de integridad. Evidencia: [LegacyTokenUtil.java:8](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8), [LegacyTokenUtil.java:13](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13), [LegacyTokenUtil.java:14](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14) y [LegacyTokenUtil.java:15](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15).
- Se calcula un instante de expiracion a una hora, pero este backend no contiene un endpoint protegido o un verificador que lo haga cumplir. Pregunta abierta: como lo validan los consumidores; no se inspeccionaron otros componentes.
- La consulta usa PreparedStatement y setString ([LegacyDatabase.java:37](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L37)), pero sigue comparando passwords en claro y siembra cuentas publicas ([LegacyDatabase.java:19](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L19), [LegacyDatabase.java:27](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L27)). No usar credenciales reales ni desplegar este laboratorio en Internet.
- CORS permite cualquier origen; no equivale a autenticacion. HTTP local no ofrece TLS y las respuestas 500 exponen detalles. Evidencia: [application.properties:5](../src/main/resources/application.properties#L5), [AuthResource.java:56](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L56) y advertencia de [README.md](../README.md).

## GET /q/health

### 1. Que hace

Permite a un operador comprobar si el servicio puede responder a su consulta de salud; no verifica por si solo que una persona pueda iniciar sesion ni que SQLite este operativa.

Lo provee quarkus-smallrye-health, no AuthResource: [pom.xml:40](../pom.xml#L40). No hay checks propios en los archivos actuales de src/main/java; la respuesta observada no contiene ninguno.

### 2. Autenticacion y headers

No requiere autenticacion en la configuracion actual. No hay header Authorization obligatorio ni esquema Basic/Bearer exigido; la consulta se verifico sin Authorization.

| Header | Valor para integrar | Obligatorio |
|---|---|---|
| Accept | application/json | Recomendado; la extension respondio JSON incluso con Accept application/xml en la comprobacion local. |
| Content-Type | No necesario sin cuerpo | No. |
| Authorization | Ninguno | No. |

La configuracion local no establece permisos HTTP de seguridad: [application.properties](../src/main/resources/application.properties).

### 3. Request

No requiere parametros de ruta, query ni campos JSON. La peticion recomendada no tiene cuerpo.

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | No aplica | No | No existe un DTO de entrada ni un campo de negocio requerido para esta consulta. |

Ejemplo HTTP completo:

```http
GET /q/health HTTP/1.1
Host: 127.0.0.1:8080
Accept: application/json

```

Ejemplo JSON completo aceptado durante la comprobacion, si una herramienta exige un cuerpo de ejemplo:

```json
{}
```

Ese objeto vacio no es un requisito ni configura el chequeo: se comprobo que enviar `{}` con Content-Type application/json devuelve la misma respuesta que no enviar cuerpo. Para integrar, preferir la peticion GET sin cuerpo, evitando limitaciones de clientes/intermediarios sobre cuerpos GET.

### 4. Response exitosa

Estado observado: **200 OK**. Content-Type: `application/json; charset=UTF-8`.

| campo | tipo | descripcion |
|---|---|---|
| status | string | Valor UP en el servicio consultado. No es el estado de autenticacion de un usuario. |
| checks | array | Lista vacia en la implementacion/configuracion actual. No se atribuyen campos a sus elementos porque no hay checks propios observables. |

Ejemplo JSON real completo:

```json
{
  "status": "UP",
  "checks": []
}
```

Respaldo: extension declarada en [pom.xml:40](../pom.xml#L40), version de plataforma en [pom.xml:13](../pom.xml#L13) y respuesta HTTP obtenida en la instancia aislada. No hay una clase local que construya este JSON; lo serializa la extension.

### 5. Respuestas de error

No hay respuestas de error propias definidas por este componente para salud ni se obtuvo una en los casos comprobados. No se inventa un JSON de error ni se declara 503 como resultado verificado de esta implementacion.

| caso comprobado | estado | cuerpo completo recibido |
|---|---|---|
| GET sin autenticacion, sin cuerpo | 200 | `{"status":"UP","checks":[]}` |
| GET con JSON vacio | 200 | `{"status":"UP","checks":[]}` |
| GET con Accept application/xml | 200 | `{"status":"UP","checks":[]}` |
| POST a la misma ruta, comprobacion de metodo no recomendado | 200 | `{"status":"UP","checks":[]}` |

No asumir que un metodo distinto devuelve 405: la extension respondio tambien al POST consultado; la integracion aqui documentada utiliza GET.

**Preguntas abiertas:** que condiciones de la extension o del despliegue pueden producir salud negativa, cual seria entonces el codigo y cuerpo exactos y si existen checks externos al codigo/configuracion local. Es necesario caracterizar esos casos antes de prometer un ejemplo de error. Si el proceso no esta escuchando, el cliente recibe un error de conexion, no una respuesta JSON de este endpoint. Por tanto, no hay un ejemplo de cuerpo de error respaldado por esta revision que pueda publicarse sin asumirlo.

### 6. Notas de seguridad

- La ruta esta expuesta sin credenciales en la configuracion local; no transmite ni valida tokens de usuario.
- `UP` y checks vacio no garantizan acceso a SQLite. LegacyDatabase captura fallos de inicializacion y no implementa un HealthCheck: [LegacyDatabase.java:30](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L30). No utilizar este resultado como prueba de autenticacion de extremo a extremo.
- El perfil prod enlaza todas las interfaces y CORS permite cualquier origen ([application.properties:3](../src/main/resources/application.properties#L3), [application.properties:5](../src/main/resources/application.properties#L5)); mantener el laboratorio aislado. No se documenta proteccion mediante un proxy externo que no aparece en el componente.

## Fuentes consultadas

Documentos: version anterior de este contrato, [README.md](../README.md), [docs/GOOD_PRACTICES.md](GOOD_PRACTICES.md) y [REGLAS.md](../REGLAS.md).

Implementacion y pruebas: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java), [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java), [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java), [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java), [application.properties](../src/main/resources/application.properties) y [pom.xml](../pom.xml). Las observaciones HTTP complementan el codigo para respuestas generadas por el framework; no se inspeccionaron otros componentes.