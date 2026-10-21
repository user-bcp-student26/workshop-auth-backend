# ARC-DEC: Token de autenticacion firmado

Fuente: [SPEC-HALLAZGO-TOKEN-SIN-FIRMA.md](SPEC-HALLAZGO-TOKEN-SIN-FIRMA.md)
Estado: APROBADA con decisiones registradas. Pendiente de implementacion en una issue separada.

## Impacto
- Emision: [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) cambia el formato del token; [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) solo consume `create(...)`, sin cambios de contrato.
- Validacion: `legacy_identity` en [main.py](../workshop-requirements-api/app/main.py#L17-L22) cambia la verificacion de todas las rutas `/api/*` (tareas, resumen, uploads y backend-health).
- Frontend: sin cambios funcionales. El token se trata como valor opaco: se guarda en `workshop_token` y se envia como `Bearer` en [dashboard.component.ts](../workshop-login-frontend/src/app/dashboard/dashboard.component.ts#L42-L45).
- Sesiones: el token actual vive 3600 segundos, por lo que cualquier corte de formato obliga como maximo a volver a iniciar sesion una hora despues.

## Componentes afectados
- workshop-auth-backend: `LegacyTokenUtil.java` (emision), `AuthResource.java` (sin cambio de logica), `application.properties` (clave por entorno), `pom.xml` (si se usa libreria y para la variable de entorno de surefire), pruebas de `AuthResourceTest.java`.
- workshop-requirements-api: `legacy_identity` y la constante `LEGACY_SECRET` en `app/main.py`; `requirements.txt` si se usa libreria; el helper `token()` de `tests/test_api.py`, que hoy firma con el secreto literal.
- workshop-login-frontend: ninguno.

## Contratos protegidos
- `POST /auth/login`: campos `token`, `expiresIn` (3600), `debug` y `user` (`username`, `displayName`, `role`); mensajes 400/401/500 existentes. Segun [HU-01.md](HU-01.md) y [REGLAS.md](REGLAS.md), no cambian.
- Esquema `Authorization: Bearer` en `/api/*`, y respuesta 401 para token invalido (cubierta por `test_rejects_invalid_token`).
- Claves de `localStorage` `workshop_token` y `workshop_user`.

## Estrategia incremental
1. Caracterizar primero: confirmar con las pruebas existentes la forma exacta de la respuesta de login y el 401 de la API.
2. Introducir la clave `AUTH_TOKEN_SECRET` por variable de entorno en ambos servicios, sin valor por defecto en codigo.
3. Implementar la firma en el backend y la verificacion en la API en la misma ventana de release. No se recomienda aceptar ambos formatos: aceptar el formato legado mantiene vigentes tokens falsificables con el secreto publico.
4. Rotar el secreto: el valor `WORKSHOP_SECRET_2026` esta en el repositorio y en su historial, por lo que el nuevo valor debe ser distinto y no versionarse.
5. Validar extremo a extremo: login real en Quarkus y llamadas a la API con el token nuevo.

## Pruebas necesarias
- Backend: token firmado valido; alterar un byte produce fallo; token expirado rechazado; falta de `AUTH_TOKEN_SECRET` produce fallo controlado sin mostrar el secreto; `AuthResourceTest` sigue verificando el contrato del login.
- API: token valido aceptado; firma alterada, expirado, formato legado y token invalido rechazados con 401; el helper `token()` toma la clave de una fixture o variable de entorno de prueba.
- Cobertura de codigo modificado: 85% o superior, segun [REGLAS.md](REGLAS.md).
- Comandos: `mvn test` en el backend y `pytest -q` en la API.

## Riesgos / trade-offs
- Libreria estandar (JWT via SmallRye JWT y PyJWT) frente a HMAC propio: la libreria reduce el riesgo de error criptografico, pero agrega dependencias. [REGLAS.md](REGLAS.md) exige justificarlas.
- Falla al arrancar sin clave (recomendado) frente a error 500 en login: el arranque fallido detecta la configuracion incompleta antes de atender trafico.
- La version emitida y la validada deben desplegarse juntas; un despliegue parcial rechaza todos los logins o todas las llamadas.
- Historial de git conserva el secreto anterior; eliminarlo requiere reescribir historial, decision fuera de esta spec.
- Cambiar `pom.xml` para la variable de surefire afecta el pipeline de pruebas.

## Decisiones aprobadas
- Algoritmo y libreria: opcion B, JWT estandar con librerias en ambos servicios.
- Corte de sesiones: sin doble aceptacion; el formato legado deja de aceptarse en el mismo despliegue.
- Comportamiento sin clave: arranque fallido en ambos servicios.

## Decisiones abiertas
- Gestion de la clave en CERT/PROD: fuera de alcance; requiere decision del equipo de operaciones.
