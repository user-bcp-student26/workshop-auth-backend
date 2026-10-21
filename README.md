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

## Migración de contraseñas

Al arrancar, `LegacyDatabase` convierte las contraseñas en claro a hash PBKDF2-HMAC-SHA256 en una sola transacción. Si la base tiene más de 10000 usuarios o la migración falla, el arranque se detiene y ninguna fila queda a medias. Los valores ya hasheados (`pbkdf2_sha256$...`) se omiten, así que la migración solo hace trabajo una vez.

Antes de arrancar con una base real:

1. Detener el servicio.
2. Copiar el archivo SQLite (`data/workshop.db` o el valor de `AUTH_DB_URL`) a un respaldo, por ejemplo `workshop.db.bak`.
3. Arrancar el servicio.
4. Si el arranque falla, restaurar el respaldo y revisar el log.

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
