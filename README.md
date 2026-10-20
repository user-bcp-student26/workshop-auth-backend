# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.

## H-02: almacenamiento de contrasenas y migracion local

La implementacion local usa PBKDF2WithHmacSHA256 del JDK, salt aleatorio de 16 bytes y derivado de 32 bytes. El formato almacenado es `v1$pbkdf2-sha256$iterations$saltBase64$hashBase64`. El servicio busca por username parametrizado y verifica el hash; no autentica contra texto plano ni expone el derivado en HTTP. CORS y las respuestas HTTP no fueron corregidos como parte de H-02.

Configuracion:

| Variable | Valor por defecto | Uso |
|---|---|---|
| AUTH_DB_URL | jdbc:sqlite:./data/workshop.db | Archivo SQLite local. La migracion requiere una URL de archivo simple; no admite memory, URI file: ni parametros de conexion porque necesita identificar y respaldar el archivo exacto. |
| AUTH_PASSWORD_ITERATIONS | 600000 | Coste para hashes nuevos; rango aprobado 600000 a 1200000. Valores fuera del rango impiden el arranque. No reducir el coste para cumplir una prueba de rendimiento. |
| AUTH_TOKEN_SECRET | (sin valor por defecto) | Clave HMAC de firma JWT; minimo 32 caracteres. Sin ella el servicio no arranca. Usar valores de laboratorio, nunca secretos reales. |

Las cuentas publicas student/trainer mantienen sus credenciales de laboratorio, pero la siembra guarda hashes y no sobrescribe usuarios existentes. No agregar credenciales reales a los fixtures. Cambiar el coste no recalcula hashes existentes durante un reinicio.

### Procedimiento offline

1. Detener trafico y todas las instancias que puedan escribir la base. Conservar el artefacto anterior y comprobar espacio/permisos para respaldo. No arrancar dos versiones del servicio sobre la misma base.
2. Al iniciar, se adquiere un bloqueo de archivo `.migration.lock`, se inspecciona esquema/version y se rechazan esquemas desconocidos. El lote legacy admite hasta 10000 usuarios; para un volumen mayor se requiere otro plan offline, no una migracion parcial.
3. Para una base legacy se crea una copia SQLite consistente mediante VACUUM INTO, en el mismo directorio, con patron `workshop.db.pre-h02-*.db` para la URL por defecto. El respaldo recibe acceso solo para su propietario (POSIX o ACL Windows). Si no puede asegurarse ese acceso, se aborta.
4. Se calculan hashes y se reconstruye la tabla en transaccion: se conserva id, atributos, secuencia AUTOINCREMENT, unicidad e indices/triggers compatibles; se retira password y se registra auth_schema_version. Un indice/trigger incompatible causa rollback, no su eliminacion silenciosa.
5. Solo se habilita el servicio si la inicializacion termina correctamente. Una base vacia nace con password_hash; una base ya migrada no se rehashea. Un fallo impide servir login con un esquema parcial.
6. Comprobar login de ambas cuentas y de los usuarios migrados, password incorrecto y ausencia de password en el esquema activo, sin volcar passwords ni hashes a logs. Retirar respaldos sensibles cuando se acepte la migracion segun la retencion acordada.

El bloqueo coordina migradores de esta implementacion; no detiene una aplicacion antigua ni un editor SQLite externo. La ventana offline es una precondicion operativa. No borrar o reemplazar el archivo de bloqueo mientras exista un migrador activo.

### Rollback y restauracion

- Antes del commit, un fallo revierte la reconstruccion transaccional. El respaldo consistente queda disponible para inspeccion/recuperacion por su propietario.
- Despues del commit: detener servicio y escritores, restaurar la copia consistente y ejecutar el artefacto anterior compatible. Nunca ejecutar codigo anterior contra password_hash ni codigo nuevo contra una restauracion parcialmente copiada.
- Los tests PasswordMigrationTest ensayan copia/restauracion y rollback, incluida una incompatibilidad de indice y una tabla vacia con secuencia historica. No se ha migrado la base de desarrollo para ejecutar esas pruebas.
- Restaurar una copia legacy reintroduce la vulnerabilidad: solo es una recuperacion temporal en laboratorio aislado. No hay fallback automatico a passwords en claro.
- Los respaldos contienen las credenciales legacy. No versionarlos ni distribuirlos. Quitar una columna no garantiza borrado forense de paginas libres, WAL, journals o snapshots; gestionar esos residuos y sus permisos antes de dar por terminada la operacion.

### Verificacion y rendimiento

`mvn -B verify` ejecuta la suite con SQLite aislada bajo target gracias a Surefire; no requiere AUTH_DB_URL manual para tests. No se modificaron las dependencias ni los quality gates.

Para ejecutar el artefacto empaquetado siempre en local:

```powershell
mvn -B quarkus:build
java -Dquarkus.http.host=127.0.0.1 -jar target/quarkus-app/quarkus-run.jar
```

Con 600000 iteraciones, cinco calentamientos y cinco solicitudes simultaneas durante 60 segundos: el artefacto empaquetado obtuvo p95 312,472 ms, 1262 solicitudes y cero errores. En modo dev el mismo cliente obtuvo p95 1694,483 ms y no cumplio el objetivo de 1000 ms. Son mediciones locales, no una garantia en otros equipos/perfiles; no se oculto el resultado dev ni se redujo el coste. La evidencia y los comandos completos quedan referenciados en [HALLAZGOS.md](HALLAZGOS.md).
