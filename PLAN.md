# Plan tecnico: H-01 y H-02

## Resumen y alcance

Propuesta para LT y developers que sustituye el plan anterior y se limita a H-01 y H-02, ambos Pendiente en [HALLAZGOS.md](HALLAZGOS.md). No se implementan cambios ni se modifican estados. El seguimiento permanece exclusivamente en esa matriz.

Bases normativas: [docs/ENDPOINTS.md](docs/ENDPOINTS.md), [docs/GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) y [REGLAS.md](REGLAS.md). Algoritmo, formato de hash, clases y migracion se presentan como propuestas tecnicas para revision, no como convenciones ya existentes.

- Dentro: SQL parametrizado, verificacion de hashes, migracion SQLite, siembra segura y separacion minima HTTP/servicio/repositorio/seguridad necesaria para esas correcciones.
- Fuera: tokens, CORS, exposicion de errores, actualizacion de dependencias, refactor general y otros componentes. No agregar endpoints ni modificar el contrato.
- Esta intervencion actualiza solo PLAN.md. Las rutas y clases enumeradas mas adelante son candidatas para implementacion futura, no archivos creados ni codigo cambiado.

## Contrato e invariantes

| Superficie | Comportamiento a preservar |
|---|---|
| Ruta y medios | POST /auth/login; entrada/salida application/json. |
| Entrada | username y password requeridos; client opcional e ignorado; campos desconocidos ignorados. |
| Validacion | Orden: request null, username invalido, password invalido. trim solo detecta vacios; no recortar ni normalizar credenciales al autenticar. |
| 400 | Mensajes exactos Request is null, Username is required y Password is required segun la rama. |
| 401 | message exacto: Invalid user seguido de un espacio, username recibido y or password con el espaciado actual. No introducir mensajes diferentes para usuario ausente/password incorrecto. |
| 200 | token string no vacio; expiresIn=3600; user con username/displayName/role; escapeHtml4 para displayName; debug=authenticated-with-legacy-flow. Sin id, password o hash. |
| Errores actuales | Fallo SQL capturado: null y luego 401. Excepcion que alcance el Resource: 500 con message original o Unknown error y nombre completo de exception. |
| Token/JSON | Mantener LegacyTokenUtil y su formato; nombres y tipos JSON intactos. No fijar orden de propiedades. |

No convertir fallos SQL en 500 ni ocultar detalles del 500 como efecto secundario. El contrato y GOOD_PRACTICES discrepan sobre errores: esa correccion queda fuera del alcance, sin alterar silenciosamente el comportamiento. No registrar nuevos passwords, hashes, tokens o SQL con valores sensibles.

### Flujo actual comprobado

1. [LegacyDatabase.java:15](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15) obtiene AUTH_DB_URL mediante System.getenv, con SQLite local por defecto. PostConstruct crea tabla y cuentas; los fallos se capturan e imprimen.
2. [AuthResource.java:26](src/main/java/com/bcp/workshop/auth/AuthResource.java#L26) valida con anidamiento y envia credenciales originales a findByCredentials.
3. [LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32) concatena credenciales en SQL; ejecuta Statement y mapea la primera fila a UserRecord. Ausencia o excepcion retorna null.
4. [AuthResource.java:39](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39) genera token y compone HTTP. Ningun hash debe llegar a ese mapeo.
5. [AuthResourceTest.java:13](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L13) solo comprueba una cuenta, username y presencia del token; es integracion Quarkus, no una unidad aislada del repositorio.

## H-01: Parametrizar SQL

### Hallazgo y problema

H-01, vulnerabilidad alta y Pendiente en [HALLAZGOS.md](HALLAZGOS.md). Evidencia: [LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32), [LegacyDatabase.java:34](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L34) y [LegacyDatabase.java:35](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L35).

Valores controlados por el cliente se convierten en sintaxis SQL. Pueden alterar la condicion de autenticacion y romper consultas de cuentas legitimas con apostrofos. Validar no vacios no corrige esto; escape HTML, reemplazo de comillas o prohibicion de caracteres tampoco sustituyen parametrizacion y restringirian credenciales.

### Cambio propuesto

Implementar H-01 antes de cambiar almacenamiento. Primer cambio revisable: conservar findByCredentials y UserRecord, sustituyendo consulta y recursos JDBC.

```sql
SELECT id, username, display_name, role
FROM legacy_users
WHERE username = ? AND password = ?
```

- SQL constante con Connection.prepareStatement; username/password mediante setString, sin trim ni normalizacion.
- Connection, PreparedStatement y ResultSet en try-with-resources. No concatenar entradas como valores, columnas o clausulas.
- Conservar mapeo, primera fila, ausencia null y tratamiento contractual de SQLException; no ampliar catches para silenciar defectos nuevos.
- Extraer un mapeador de fila solo si separa realmente consulta/conversion o se reutiliza. No introducir interfaces ni renombrar repositorios por simetria.
- DDL fijo no es concatenacion de credenciales; en la futura migracion, identificadores seran constantes internas e inserciones de usuarios parametrizadas.
- Sin dependencia nueva: JDBC y sqlite-jdbc ya estan disponibles en [pom.xml](pom.xml).

Este SQL es transitorio entre cambios: H-02 reemplaza la busqueda de password por consulta parametrizada de username y verificacion de hash. No conservar dos rutas de autenticacion permanentes.

### Archivos a tocar

| Archivo | Intervencion futura |
|---|---|
| [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) | PreparedStatement, bindings, cierre de recursos y mapeo si resulta necesario. |
| [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) | Ampliar caracterizacion HTTP y regresiones de credenciales. |
| Nuevo propuesto: src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java | Integracion JDBC con SQLite aislada; reutilizarlo para H-02. |

H-01 no requiere cambiar produccion en AuthResource, UserRecord, configuracion, token ni pom.xml.

### Pruebas de caracterizacion previas

Escribir y ejecutar antes de tocar produccion sobre datos sinteticos. Reutilizar AuthResourceTest para contrato y agregar prueba real JDBC para persistencia.

| Caso | Baseline a comprobar |
|---|---|
| Ambas cuentas sembradas | Usuario/rol correcto y 200 con cuerpo contractual completo. |
| Usuario inexistente/password incorrecto | null en repositorio; 401 y message exacto en HTTP. |
| Espacios significativos y mayusculas | Comparacion con originales, sin normalizacion nueva. |
| Campos vacios, client omitido/distinto | Validaciones, precedencia y resultado sin cambios. |
| displayName con caracteres HTML | Mapeo y escape exactos, sin id expuesto. |
| Tabla ausente durante consulta | Fallo SQL capturado, null/401; restaurar solo el fixture propio. |
| Cuenta sintetica con apostrofo | Insertar con parametros desde la prueba; registrar defecto baseline sin perpetuarlo en el contrato corregido. |

Separar caracterizaciones verdes de regresiones de seguridad: escribir antes de corregir una prueba que falle por el defecto de apostrofos/consulta, y hacerla pasar con PreparedStatement. No exigir que la version corregida reproduzca un comportamiento explotable; no incluir payloads de explotacion en esta documentacion.

### Validacion posterior y cierre

- Cuenta con apostrofo autentica solo con password exacto; entradas parecidas o caracteres SQL se tratan como literales y no alteran la condicion.
- Mismos JSON/estados para credenciales normales; ausencia y error SQL probados por separado aunque ambos produzcan 401.
- Revisar consulta y bindings junto con tests SQLite; verificar solamente llamadas a prepareStatement mediante mocks no demuestra comportamiento.
- Cerrar H-01 solo con pruebas contractuales/regresiones satisfactorias y evidencia futura anotada en HALLAZGOS.md.

## H-02: Hash lento con salt y migracion

### Hallazgo y problema

H-02, vulnerabilidad alta y Pendiente en [HALLAZGOS.md](HALLAZGOS.md). Evidencia: [LegacyDatabase.java:22](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22), [LegacyDatabase.java:23](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L23), [LegacyDatabase.java:24](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L24) y [LegacyDatabase.java:32](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32).

La siembra persiste passwords literales y la consulta compara el claro. TEXT no es por si mismo el defecto: un hash codificado tambien puede ser TEXT. El riesgo es recuperar passwords utilizables al acceder a datos. Las cuentas publicas de laboratorio siguen necesitando hashes en almacenamiento, sin introducir secretos reales.

### Cambio propuesto: algoritmo y formato

Proponer PBKDF2WithHmacSHA256 con SecretKeyFactory y PBEKeySpec del JDK 21: KDF estandar, lento y con salt, sin agregar una biblioteca. GOOD_PRACTICES cita BCrypt como ejemplo, no como dependencia obligatoria. No usar SHA-256 simple, cifrado reversible ni criptografia implementada manualmente.

| Decision propuesta | Justificacion y revision necesaria |
|---|---|
| Salt de 16 bytes con SecureRandom por usuario | Evitar hashes identicos y precalculos compartidos; constante SALT_BYTES. |
| Derivado de 32 bytes | Constante HASH_BYTES; guardar salida, nunca password. |
| Coste inicial de 600000 iteraciones | Propuesta para revisar con LT/Cyber y medir en JDK 21; no benchmark ya realizado ni regla heredada. Configuracion auth.password.iterations con valor por defecto nombrado. |
| Formato versionado | `v1$pbkdf2-sha256$iterations$saltBase64$hashBase64`; sin password ni pepper obligatorio. |
| Parser estricto | Version/algoritmo permitidos, campos exactos, longitudes validas, coste entre limites nombrados aprobados. No ejecutar costes arbitrarios de una base corrupta. |
| Comparacion | MessageDigest.isEqual sobre derivados de igual longitud; no equals de passwords. |
| Entrada | Sin trim, truncamiento, normalizacion Unicode ni nueva longitud maxima de API; probar el proveedor JCA con Unicode y claves largas. |

LT/Cyber deben aprobar costes minimo/maximo y objetivos de latencia/concurrencia antes de implementar. No reducir el coste para pasar tests ni declarar rendimiento sin evidencia. Detectar configuracion/proveedor invalidos antes de servir autenticacion, sin fallback a claro.

PasswordHasher separara generacion, parsing y verificacion en metodos de una responsabilidad. Usarlo desde login, siembra y migracion evita duplicacion. Limpiar PBEKeySpec y buffers mutables al finalizar; no prometer borrar los Strings que ya crea LoginRequest.

### Cambio propuesto: flujo final

```sql
SELECT id, username, password_hash, display_name, role
FROM legacy_users
WHERE username = ?
```

1. AuthResource conserva validacion HTTP y delega credenciales originales a AuthService.
2. LegacyDatabase mantiene el papel de repositorio; findByUsername consulta con PreparedStatement y devuelve registro interno con identidad/hash o ausencia. No recibe password ni decide autenticacion.
3. AuthService verifica con PasswordHasher y devuelve solo identidad o ausencia. Usuario inexistente, password incorrecto o hash corrupto reconocido producen la misma respuesta 401; no exponer parsing ni derivado.
4. Resource conserva token legacy y respuesta JSON. Usar StoredUser interno para hash y UserRecord para identidad evita propagar el hash al mapeo; no modificar UserRecord si este registro separado basta.
5. Mantener error SQL/null/401 y 500 para excepciones inesperadas que alcancen Resource. Tratar formatos corruptos como resultado controlado, sin catch generico que absorba cualquier error de programacion.

La separacion minima de servicio/hasher respeta GOOD_PRACTICES y es necesaria para estas dos correcciones; no depende de ejecutar otro hallazgo ni pretende cerrar toda la deuda de capas. No exponer o registrar toString del registro con hash.

### Cambio propuesto: esquema y migracion

Esquema final: legacy_users conserva id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, display_name TEXT NOT NULL y role TEXT NOT NULL; sustituye password TEXT NOT NULL por password_hash TEXT NOT NULL. Preservar datos, IDs y restricciones existentes; no agregar NOT NULL a username incidentalmente.

Agregar password_hash sin retirar password no basta: la columna original sigue siendo obligatoria y conservaria el claro. Proponer reconstruccion transaccional con version propia del esquema de autenticacion, sin doble autenticacion perpetua.

1. **Preflight offline:** detener trafico y evitar dos migradores. Verificar esquema por metadatos SQLite, volumen, permisos y version de autenticacion. Esquema desconocido o inconsistencias: abortar sin alterar datos ni imprimir valores sensibles.
2. **Respaldo consistente:** usar mecanismo SQLite soportado dentro del ambito y fuera de Git, con acceso restringido. No copiar ingenuamente un archivo abierto con WAL; ensayar restauracion antes de operar sobre la base de desarrollo.
3. **Calcular hashes:** leer filas legacy sin logs de passwords y generar salts distintos. Acotar lote/tiempo de parada; evitar mantener una transaccion de escritura durante todo el KDF. La exclusividad operativa impide cambios entre lectura y escritura.
4. **Transaccion:** crear tabla de reemplazo con nombre constante, insertar mediante parametros, validar conteos/IDs/atributos, intercambiar tablas y registrar version en una tabla propia de autenticacion. Preservar secuencia AUTOINCREMENT e indices/triggers realmente existentes, previa revision de metadatos.
5. **Commit y activacion:** confirmar solo si todo valida. Login final consulta exclusivamente password_hash; sin fallback al claro. Ejecutar smoke tests de cuentas y ausencia antes de aceptar trafico.
6. **Reinicio y siembra:** version final no rehashea ni sobreescribe usuarios. Base vacia nace segura; cuentas de laboratorio ausentes se insertan con hashes del mismo PasswordHasher y SQL parametrizado, sin reemplazar cuentas existentes.
7. **Fallo:** rollback integral; no servir autenticacion con esquema parcial. No dejar que initialize silencie un fallo de migracion y aparente disponibilidad. La politica de arranque/operacion requiere revision LT, sin agregar una respuesta HTTP distinta al contrato.

No agregar Flyway u otra herramienta para este esquema pequeno salvo necesidad demostrada y aprobada. Las cuentas publicas de laboratorio pueden seguir disponibles como fixtures aislados; no introducir passwords reales en codigo/configuracion.

### Rollback y residuos

- Antes de commit: rollback restaura esquema/datos originales. Despues: detener servicio y restaurar copia consistente junto con codigo anterior; no ejecutar codigo viejo contra esquema nuevo.
- Restaurar el claro reintroduce temporalmente la vulnerabilidad: solo laboratorio aislado, nunca fallback automatico ni alternativa declarada segura.
- Retirar respaldos y auxiliares sensibles tras aceptacion; no versionar bases, journals o WAL. Ausencia de columna no prueba borrado forense de paginas libres, snapshots o copias; definir mantenimiento/retencion sin prometer erradicacion absoluta.

### Archivos a tocar

Clases nuevas propuestas bajo el paquete com.bcp.workshop.auth existente; no establecer una convencion de paquetes nueva.

| Archivo | Intervencion futura y limite |
|---|---|
| [LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) | findByUsername, esquema/siembra y coordinacion de migracion; sin KDF propio. |
| [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) | Delegar autenticacion y guard clauses; conservar validaciones, token, respuesta y errores. |
| [application.properties](src/main/resources/application.properties) | Parametros KDF nombrados; no alterar host/puerto/CORS/Jackson ni comportamiento AUTH_DB_URL. |
| [README.md](README.md) | Procedimiento offline, backup/restore y configuracion KDF; sin nuevos secretos reales. |
| [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) | Contrato HTTP en base nueva y migrada. |
| Nuevos propuestos: AuthService.java, PasswordHasher.java, StoredUser.java, PasswordMigration.java | Bajo src/main/java/com/bcp/workshop/auth: orquestacion, KDF, registro interno y migracion por etapas. No crear un motor general de migraciones. |
| Pruebas propuestas: LegacyDatabaseTest.java, PasswordHasherTest.java, PasswordMigrationTest.java, AuthServiceTest.java | Bajo src/test/java/com/bcp/workshop/auth: JDBC, KDF, migracion y servicio. Reutilizar fixtures; no crear archivos adicionales sin un riesgo/responsabilidad concreto. |

No se proponen cambios en [UserRecord.java](src/main/java/com/bcp/workshop/auth/UserRecord.java), [LoginRequest.java](src/main/java/com/bcp/workshop/auth/LoginRequest.java), [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java), [pom.xml](pom.xml) o [docs/ENDPOINTS.md](docs/ENDPOINTS.md). JCA/JDBC, sqlite-jdbc, JUnit y Quarkus/RestAssured existentes cubren implementacion y pruebas. No agregar BCrypt, Mockito o tooling sin justificar una necesidad concreta.

### Pruebas de caracterizacion previas

Escribir y ejecutar despues de H-01 y antes de tocar esquema/verificacion; conservar tambien resultados previos a H-01 para atribuir regresiones.

| Caso | Evidencia previa requerida |
|---|---|
| Base vacia | initialize crea tabla/cuentas; ambos roles autentican con cuerpo completo. |
| Base legacy poblada | IDs, username, displayName y role de usuarios sinteticos intactos; solo password exacto autentica. |
| Reinicio | INSERT OR IGNORE no sobreescribe cuentas/atributos. |
| Credenciales especiales | Espacios, apostrofos tras H-01, Unicode y mas de 72 bytes sin truncamiento. |
| Salida | Escape HTML y token legacy sin cambios; ningun dato de persistencia adicional. |
| SQL y Resource | Error SQL/null/401 y 500 contractual probado con excepcion de doble manual, en rutas separadas. |

Probar request null por llamada directa, no equipararlo al cuerpo HTTP ausente. Caracterizar JSON malformado/tipos incompatibles por separado si se amplian esos casos; no inventar respuestas del framework. La inspeccion del claro original sirve solo para migracion con fixtures; no dejar una prueba que exija almacenamiento inseguro tras corregir.

### Pruebas posteriores

| Nivel | Casos y resultado esperado |
|---|---|
| PasswordHasher | Misma clave genera salts/hashes distintos y ambos verifican; clave distinta no verifica; formato/version/coste invalidos se rechazan sin KDF arbitrario. |
| PasswordHasher | Espacios, Unicode y claves largas conservan semantica; longitudes/formato estables; configuracion/proveedor invalido no habilita fallback. |
| AuthService | Ausencia, password incorrecto y hash corrupto dan ausencia; password no llega a SQL; identidad exitosa sin hash. |
| Repositorio | Solo username enlazado; mapeo correcto; recursos cerrados; error SQL compatible. |
| Migracion | Base nueva, legacy y final; conteos, IDs, secuencia y atributos preservados; sin columna password ni tabla legacy residual tras commit. |
| Migracion | Fallo antes/durante intercambio produce rollback completo; reinicio idempotente; esquema desconocido aborta sin corrupcion. |
| Operacion | Respaldo/restore ensayados; migradores concurrentes no cambian simultaneamente; fallo no deja servicio atendiendo esquema parcial. |
| HTTP | Base nueva/migrada mantienen validaciones, client, estados, mensajes, expiresIn, debug, escape y token. |

No volcar hashes/keys a reportes; inspeccionar SELECT no demuestra borrado de residuos en disco. Escribir regresiones de seguridad antes del cambio como expectativas inicialmente fallidas, separadas de caracterizaciones verdes.

### Criterio de cierre

Cerrar H-02 solo si login verifica hashes fuera de HTTP/repositorio; base nueva/migrada almacena hashes versionados sin fallback; migracion/rollback/idempotencia estan probados; JSON/token permanecen compatibles; pruebas y cobertura pasan. Registrar evidencia y estado posteriormente solo en HALLAZGOS.md.

## Clean Code aplicado

| Regla | Aplicacion concreta |
|---|---|
| Una responsabilidad | Validacion HTTP, consulta, verificacion, mapeo, migracion y siembra separados; coordinadores solo orquestan. |
| Nombres de proposito | findByUsername, verifyPassword, migrateLegacyPasswords y seedWorkshopUsers; sin nombres genericos. |
| Sin duplicacion | Un PasswordHasher en login/siembra/migracion; mapeo y fixtures compartidos; no dos rutas SQL de login permanentes. |
| Sin anidamiento profundo | Guard clauses para request invalido, ausencia y password incorrecto; etapas de migracion con helpers verificables. |
| Sin numeros magicos | SQL, formato/version KDF, salt/derivado y costes/limites nombrados o configurados; no modificar utilidad token para limpiar deuda ajena al alcance. |
| Capas/DTO | HTTP sin SQL/KDF, repositorio sin negocio y registro con hash nunca serializado; UserRecord permanece identidad interna mapeada a salida. |

## Secuencia de entrega y validacion

1. **Caracterizacion:** ampliar pruebas HTTP/JDBC y fixtures; baseline verde y regresiones de seguridad identificadas antes de cambiar produccion. No mezclar este paso con refactor.
2. **H-01:** parametrizacion minima y regresiones resueltas, sin cambiar almacenamiento.
3. **Seguridad H-02:** PasswordHasher y tests unitarios; aprobar coste/limites y medir latencia/concurrencia. Sin activar esquema aun.
4. **Persistencia H-02:** migrador/esquema, ensayos de nueva base, legacy, idempotencia y rollback. No desplegar una version intermedia que deje codigo activo incompatible con el esquema.
5. **API/servicio H-02:** conectar servicio/consulta final y eliminar findByCredentials. Cambios de seguridad, repositorio y API revisables por separado, pero activar solo el conjunto compatible; sin trafico entre versiones incompatibles.
6. **Aceptacion:** ensayo offline backup/migracion/restore, suite completa y revision LT/Cyber; actualizar estados en HALLAZGOS.md solo con resultados reales.

### Entorno y gates futuros

- JDK 21 y Maven 3.9+: verificar java -version y mvn -version. El ultimo intento local no encontro Maven en PATH; no suponer que ya esta disponible. Este plan no ejecuta pruebas.
- Configurar AUTH_DB_URL antes de arrancar Maven/Quarkus: System.getenv estatico no se sustituye con %test. Base exclusiva por proceso bajo target; nunca usar la de desarrollo ni paralelizar sobre el mismo archivo.
- Evitar jdbc:sqlite::memory: con conexiones independientes salvo manejo explicito de vida/comparticion; pueden observar bases diferentes. Preferir archivo temporal y limpiar solo fixtures propios.
- Ejecutar `mvn -Dtest=AuthResourceTest test` y `mvn -Dtest=LegacyDatabaseTest test`; seleccionar las otras clases cuando existan. Reutilizar JUnit y dobles manuales, sin agregar Mockito por defecto.
- Gate final: `mvn -B verify`. Guardar Surefire/JaCoCo bajo target; acreditar cobertura de al menos 85% del codigo modificado segun REGLAS.md. Un reporte no implica que pom.xml ya aplique automaticamente ese umbral; revisar clases/lineas tocadas.
- Mantener CodeQL, Dependency Review y JaCoCo; no suprimir reglas, exclusiones o asserts para aparentar exito. Anotar evidencia futura en HALLAZGOS.md sin duplicar seguimiento aqui.

## Riesgos y decisiones del foro

| Riesgo/decision | Mitigacion o evidencia necesaria | Responsable sugerido |
|---|---|---|
| Trafico o dos migradores | Ventana offline/exclusividad; ensayo de fallo sin esquema parcial disponible. | LT/developers |
| Coste KDF | Benchmark JDK 21 y aprobacion de costes/limites y objetivo de latencia/concurrencia, sin nuevo limite de password. | LT/developers/Cyber |
| Residuos en copia/WAL/paginas | Backup restringido y consistente, restore y politica de retirada; no prometer borrado forense. | Developers/Cyber |
| Esquema o hash corrupto | Preflight estricto; abortar migracion y rechazar registros invalidos sin revelar valores. | Developers/QA |
| Fijar vulnerabilidad como baseline | Separar compatibilidad de regresiones que deben dejar de fallar. | Developers/QA |
| Cambios incidentales HTTP/token | Aserciones exactas y diff; contrato y LegacyTokenUtil intactos. | LT/QA |

Decisiones abiertas: costes KDF definitivos; volumen/metadatos reales de la base; mecanismo SQLite concreto de backup/bloqueo; manejo controlado de hashes corruptos. Verificar en el ambito autorizado antes de implementar, sin asumir infraestructura externa.

## Criterios de aprobacion

1. Solo H-01 y H-02 tienen propuestas de correccion, archivos, caracterizacion previa y regresiones posteriores; el seguimiento permanece en HALLAZGOS.md.
2. Contrato intacto y matriz HTTP compatible; las entradas explotables se prueban como defectos corregidos, no como autenticacion legitima a preservar.
3. Ningun SQL de autenticacion concatena entrada; consulta final solo enlaza username y verificacion usa KDF estandar con salt.
4. Migracion conserva datos/restricciones, es idempotente, tiene rollback probado y no deja columna de claro ni fallback; residuos y respaldos tienen tratamiento explicito.
5. Cobertura/gates acreditados, sin dependencias injustificadas ni cambios fuera del backend. Este plan no modifica codigo ni declara resueltos los hallazgos.


- Aprobador: Paul Elguera - PO
- Fecha: 2026-10-19
- Decision: Aprobado
- Alcance: implementar H-01 conforme a este plan.
- Condiciones: preservar el contrato HTTP y los quality gates.

## Aprobacion humana de H-02

- Aprobador: Paul Elguera
- Fecha: 2026-10-19
- Decision: Aprobado
- Alcance: implementar exclusivamente H-02 conforme a este plan.
- Algoritmo: PBKDF2WithHmacSHA256.
- Iteraciones de generacion: 600000.
- Iteraciones minimas/maximas aceptadas: 600000/1200000.
- Objetivo de latencia: p95 de login menor o igual a 1000 ms, medido en el entorno local de laboratorio.
- Concurrencia de referencia: 5 solicitudes simultaneas, sostenidas durante 60 segundos.
- Migracion: aprobada offline, con respaldo consistente,
  rollback probado y sin fallback a contrasenas en claro.
- Condiciones: preservar el contrato HTTP, mantener los quality
  gates y alcanzar al menos 85% de cobertura por lineas.