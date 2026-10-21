# Token de autenticación firmado

## Fuente
[HALLAZGO-TOKEN-SIN-FIRMA.md](HALLAZGO-TOKEN-SIN-FIRMA.md), declarado aprobado en la solicitud. El archivo fuente todavía conserva el estado `CANDIDATO`. Evidencia: [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8-L15), [main.py](../workshop-requirements-api/app/main.py#L17-L22) y [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java#L65-L68). También aplica [HU-01.md](HU-01.md), que establece el endurecimiento del token y preservar el contrato HTTP de login.

## Contexto actual
`POST /auth/login` entrega un token Base64 cuyo contenido incluye un secreto fijo. La API de requerimientos decodifica el valor y comprueba sus campos, pero no verifica una firma criptográfica.

## Problema confirmado
El formato actual no permite verificar criptográficamente la integridad ni el emisor del token. La API depende de un secreto fijo para aceptar la identidad.

## Comportamiento a preservar
- Mantener `POST /auth/login` y su respuesta HTTP satisfactoria con los campos actuales.
- Mantener las rutas protegidas y el esquema de autenticación Bearer para consumidores válidos.
- No exponer secretos en respuestas ni en errores.

## Alcance
Definir el contrato de emisión y validación de tokens firmados, incluida la aceptación de tokens válidos y el rechazo de tokens inválidos o expirados.

## Fuera de alcance
- Cambios a los campos de identidad o a la forma general de la respuesta de login.
- Cambios a permisos de negocio distintos de la validación del token.
- Seleccionar algoritmo, biblioteca, rotación de claves o periodo de transición sin decisión explícita.

## Criterios de aceptación
- Un login válido conserva el estado y los campos de respuesta del contrato existente, y entrega un token verificable.
- Las rutas protegidas aceptan un token válido y rechazan tokens alterados, malformados o expirados.
- La ausencia de configuración necesaria para firmar produce un fallo controlado que no revela secretos.
- Hay pruebas que cubren emisión, validación, integridad, expiración y configuración ausente.

## Contratos afectados/protegidos
- `POST /auth/login`: se conserva el contrato HTTP existente, como exige [HU-01.md](HU-01.md).
- Rutas `/api/*` protegidas: el token debe seguir siendo consumible mediante Bearer, pero el formato interno puede cambiar.
- [REGLAS.md](REGLAS.md): no agregar secretos reales; conservar el contrato de login.

## Riesgos, supuestos y preguntas abiertas
- Definir cómo se manejarán los tokens emitidos antes del cambio: rechazo inmediato o transición acotada.
- Definir configuración, rotación y disponibilidad de claves entre backend y API.
- La prioridad depende de si el servicio se expone fuera del entorno aislado del taller.
