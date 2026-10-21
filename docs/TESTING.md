# Pruebas del backend de autenticacion

Verificacion realizada el 2026-10-19 contra [ENDPOINTS.md](ENDPOINTS.md). Solo se completaron pruebas y configuracion de su ejecucion; no se modifico codigo de produccion ni el contrato. Los ejemplos usan exclusivamente cuentas publicas del laboratorio y fixtures sinteticos.

**Correccion 2026-10-21:** al inicio de la caracterizacion, en este repositorio solo existia `AuthResourceTest` con una prueba. Ahora existen `AuthResourceTest` (24 pruebas) y `HealthResourceTest` (4 pruebas). `LegacyDatabaseTest`, `PasswordHasherTest` y los totales de 37 y 65 pruebas de las tablas siguientes no existen en `src/test` de este repositorio; se conservan solo como historico y no son reproducibles aqui.

## Matriz de casos ejecutados

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | student sin Authorization | Camino feliz | 200, JSON completo, expiresIn=3600, user exacto, debug actual y token string. Sin id/password/hash. |
| POST /auth/login | trainer con credenciales validas | Camino feliz | 200, identidad y rol TRAINER. |
| POST /auth/login | client omitido, distinto y campos desconocidos | Caso borde | 200; client/campos extra no cambian identidad ni contrato. |
| POST /auth/login | username ausente, vacio o solo espacios, con password tambien invalido | Caso borde/error | 400, solo message=Username is required; precedencia de username. |
| POST /auth/login | username explicitamente null | Caso borde/error | 400, solo message=Username is required. |
| POST /auth/login | password ausente, vacio o solo espacios | Caso borde/error | 400, solo message=Password is required. |
| POST /auth/login | password explicitamente null | Caso borde/error | 400, solo message=Password is required. |
| POST /auth/login | Espacios significativos y displayName con HTML | Caso borde | Password/username exactos autentican; no se recortan; displayName escapado. Password recortado produce 401. |
| POST /auth/login | Apostrofos en credenciales sinteticas | Caso borde/correccion H-01 | 200 con identidad exacta; SQL interpreta valores, no sintaxis. |
| POST /auth/login | Usuario inexistente o password incorrecto | Error | 401 con message exacto incluyendo username recibido. |
| POST /auth/login | Excepcion SQL por tabla ausente en fixture | Error | 401, no 500, conforme al contrato legacy. |
| POST /auth/login | request null mediante llamada directa | Caso borde/error | 400 con Request is null; distinto de demostrar HTTP. |
| POST /auth/login | JSON literal null por HTTP | Caso borde/error | 400 con Request is null. |
| POST /auth/login | Cuerpo ausente por HTTP | Caso borde/error | 400 con Request is null. |
| POST /auth/login | JSON malformado, cuerpo `{` | Error | 400, cuerpo de 0 bytes y sin Content-Type. |
| POST /auth/login | Estructura incompatible, cuerpo `[]` | Error | 400, cuerpo de 0 bytes y sin Content-Type. |
| POST /auth/login | Content-Type text/plain | Error | 415, cuerpo de 0 bytes y sin Content-Type. |
| POST /auth/login | Accept application/xml | Error | 406, cuerpo de 0 bytes y sin Content-Type. |
| POST /auth/login | Metodo GET | Error | 405, cuerpo de 0 bytes y sin Content-Type. |
| POST /auth/login | Excepcion con mensaje, mediante doble y llamada directa | Error | 500, message=Synthetic failure y exception=java.lang.IllegalStateException. |
| POST /auth/login | Excepcion sin mensaje, mediante doble y llamada directa | Error | 500, message=Unknown error y exception=java.lang.IllegalStateException. |
| POST /auth/login | Registro inconsistente sintetico con displayName null, por HTTP | Error | 500 con message=Unknown error y exception=java.lang.NullPointerException, sin campos adicionales. |
| GET /q/health | GET sin autenticacion ni cuerpo | Camino feliz | 200 JSON exacto con status=UP y checks=[]. |
| GET /q/health | Cuerpo JSON vacio `{}` | Caso borde | 200 y mismo JSON exacto. No convertirlo en requisito de entrada. |
| GET /q/health | Accept application/xml | Caso borde | 200 con Content-Type JSON y mismo cuerpo, segun el contrato observado. |
| GET /q/health | POST sobre la misma ruta, compatibilidad documentada | Caso borde | 200 y mismo JSON; GET sigue siendo el metodo de integracion recomendado. |

**Errores de salud:** el contrato no define una respuesta de error reproducible ni un 503 comprobado. Por tanto hay cero respuestas de error de salud documentadas que convertir en test; no se inventa un fallo, un check o un endpoint para aparentar cobertura. Los escenarios de salud negativa permanecen pendientes de definicion, como se detalla al final.

## Codigo de las pruebas

Codigo completo y autoritativo: [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java), [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java) y [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java). Los fragmentos siguientes son codigo ejecutado, no pruebas propuestas. Los imports y helpers de los casos existentes permanecen en esos archivos; no es necesario copiar estos fragmentos para ejecutar Maven.

### Login: errores HTTP y nulls anadidos

Metodos incorporados a AuthResourceTest; assertEmptyFrameworkError comprueba tanto estado como ausencia real de cuerpo y Content-Type, sin parsear JSON inexistente.

```java
@Test
void rejectsNullJsonRequest() {
    given().contentType("application/json").body("null")
            .when().post("/auth/login").then().statusCode(400)
            .body("", is(Map.of("message", "Request is null")));
}

@Test
void rejectsMissingRequestBody() {
    given().contentType("application/json")
            .when().post("/auth/login").then().statusCode(400)
            .body("", is(Map.of("message", "Request is null")));
}

@Test
void rejectsExplicitlyNullUsername() {
    given().contentType("application/json").body("{\"username\":null,\"password\":\"valid\"}")
            .when().post("/auth/login").then().statusCode(400)
            .body("", is(Map.of("message", "Username is required")));
}

@Test
void rejectsExplicitlyNullPassword() {
    given().contentType("application/json").body("{\"username\":\"student\",\"password\":null}")
            .when().post("/auth/login").then().statusCode(400)
            .body("", is(Map.of("message", "Password is required")));
}

@Test
void rejectsMalformedJson() {
    assertEmptyFrameworkError(given().contentType("application/json").body("{")
            .when().post("/auth/login"), 400);
}

@Test
void rejectsIncompatibleJsonShape() {
    assertEmptyFrameworkError(given().contentType("application/json").body("[]")
            .when().post("/auth/login"), 400);
}

@Test
void rejectsUnsupportedMediaType() {
    assertEmptyFrameworkError(given().contentType("text/plain").body("test")
            .when().post("/auth/login"), 415);
}

@Test
void rejectsUnacceptableResponseMediaType() {
    assertEmptyFrameworkError(given().contentType("application/json").accept("application/xml")
            .body(Map.of("username", "student", "password", "Workshop123!"))
            .when().post("/auth/login"), 406);
}

@Test
void rejectsWrongHttpMethod() {
    assertEmptyFrameworkError(given().when().get("/auth/login"), 405);
}

private void assertEmptyFrameworkError(io.restassured.response.Response response, int status) {
    assertEquals(status, response.statusCode());
    assertEquals(0, response.asByteArray().length);
    assertNull(response.header("Content-Type"));
}
```

### Login: 500 con y sin mensaje

Se separaron las dos llamadas directas para que Surefire identifique cada variante. No sustituyen la prueba HTTP siguiente.

```java
@Test
void characterizesUnexpectedExceptionMessageDirectly() {
    assertInternalError(new IllegalStateException("Synthetic failure"), "Synthetic failure");
}

@Test
void characterizesNullExceptionMessageDirectly() {
    assertInternalError(new IllegalStateException(), "Unknown error");
}

@Test
void returnsDocumented500ForSyntheticInconsistentRecord() throws Exception {
    try (Connection connection = LegacyDatabaseTest.openTestConnection(); Statement statement = connection.createStatement()) {
        statement.executeUpdate("DROP TABLE legacy_users");
        statement.executeUpdate("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY, username TEXT, password TEXT, display_name TEXT, role TEXT)");
    }
    LegacyDatabaseTest.insertUser("inconsistent", "SyntheticPassword", null, "STUDENT");
    Map<String, Object> response = given().contentType("application/json")
            .body(Map.of("username", "inconsistent", "password", "SyntheticPassword"))
            .when().post("/auth/login").then().statusCode(500).contentType("application/json")
            .extract().jsonPath().getMap("$");
    assertEquals(Map.of("message", "Unknown error", "exception", NullPointerException.class.getName()), response);
}
```

El 500 HTTP se provoca deliberadamente solo en la base sintetica: se recrea una tabla de prueba sin la restriccion NOT NULL para display_name y se inserta un valor inconsistente. No se cambia el esquema de produccion, no se afirma que un request normal pueda crear esa fila y BeforeEach restaura el fixture para el siguiente test. El contrato ya exige el comportamiento generico de la rama 500. La exposicion del nombre de excepcion es un defecto de seguridad conocido, no una recomendacion; se informa por separado mas adelante.

### Salud: clase anadida

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class HealthResourceTest {
    @Test
    void returnsHealthWithoutAuthentication() {
        given().accept("application/json")
                .when().get("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void acceptsEmptyJsonBody() {
        given().contentType("application/json").body("{}")
                .when().get("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void returnsJsonDespiteXmlAcceptHeader() {
        given().accept("application/xml")
                .when().get("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void characterizesDocumentedPostCompatibility() {
        given().when().post("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }
}
```

Los caminos felices de login, validaciones vacias y credenciales invalidas ya existian y se conservaron. Se ejecutan junto a los nuevos casos. LegacyDatabaseTest aporta diez pruebas JDBC, incluidas consultas parametrizadas, mapeo, inicializacion, errores y apostrofos. Los tests Quarkus son de integracion; las llamadas directas al Resource no prueban transporte HTTP.

## Configuracion y aislamiento

Surefire ahora prepara AUTH_DB_URL en [pom.xml](../pom.xml) antes de iniciar la JVM de pruebas:

```xml
<maven.build.timestamp.format>yyyyMMdd-HHmmss-SSS</maven.build.timestamp.format>
```

```xml
<environmentVariables>
    <AUTH_DB_URL>jdbc:sqlite:${project.build.directory}/auth-test-${maven.build.timestamp}.db</AUTH_DB_URL>
</environmentVariables>
```

La primera propiedad pertenece a properties; environmentVariables pertenece a la configuracion de maven-surefire-plugin. El timestamp evita reutilizar por defecto una base de otra invocacion; el nombre observado en el gate final fue auth-test-20261019-220603-511.db. El fixture conserva su comprobacion de que la base esta dentro de target y sus inserciones parametrizadas. No se cambia LegacyDatabase para admitir otra via de configuracion ni se usa reflexion para modificar System.getenv.

Ejecutar desde la raiz workshop-auth-backend, no desde docs. No se requiere levantar un servidor aparte ni definir AUTH_DB_URL en la consola. Maven configura el proceso hijo, incluso si la terminal no tiene esa variable. Para reproducir sin entorno heredado en PowerShell:

```powershell
Remove-Item Env:AUTH_DB_URL -ErrorAction SilentlyContinue
mvn -B verify
```

Ese comando elimina la variable solo de la terminal actual; no modifica variables persistentes. La comprobacion ejecutada restauro el valor previo de la sesion al terminar. Para runners IDE, usar la ejecucion Maven o reproducir explicitamente el entorno del fork; ejecutar JUnit directamente fuera de Maven no activa environmentVariables de Surefire.

La configuracion actual usa un fork y ejecucion secuencial: los tests comparten el archivo de esa invocacion y restauran la tabla mediante BeforeEach. No habilitar forks/paralelismo compartiendo esta URL sin disenar fixtures por fork. Se conservan bases/evidencias bajo target para inspeccion; no se versionan. No se apuntan tests destructivos a data/workshop.db.

## Ejecuciones reales

Entorno: Windows 11, PowerShell, Apache Maven 3.10.0 y Eclipse Adoptium JDK 21.0.12.1. **Directorio de todas las ejecuciones: raiz de workshop-auth-backend/**. No se presuponen un PR, un merge ni resultados remotos de CI.

| ejecucion | comando realmente ejecutado | codigo de salida | resultado |
|---|---|---|---|
| Reproduccion previa del bloqueo, sin AUTH_DB_URL; revision anterior conservada | `mvn -B -Dtest=LegacyDatabaseTest#characterizesSeededUsers test` | 1 | 1 test, 0 fallos de asercion, 1 error de setup: Tests require an isolated AUTH_DB_URL. |
| Mismo caso despues de configurar Surefire, sin variable heredada | `mvn -B -Dtest=LegacyDatabaseTest#characterizesSeededUsers test` | 0 | 1 test, 0 fallos, 0 errores, 0 omitidos. |
| Ambos endpoints, sin variable heredada | `mvn -B -Dtest=AuthResourceTest,HealthResourceTest test` | 0 | 27 tests: 23 login y 4 salud; 0 fallos, 0 errores, 0 omitidos. |
| Gate completo con archivo JaCoCo nuevo | `mvn -B -Djacoco.destFile=target/testing-evidence/coverage-e93c8894d2ac464da9555b333c53575b.exec -Djacoco.dataFile=target/testing-evidence/coverage-e93c8894d2ac464da9555b333c53575b.exec verify` | 0 | 37 tests: 23 login, 4 salud y 10 JDBC; 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESS. |

Evidencias locales, no versionadas:

- [Fallo previo de setup](../target/review-h01/missing-env/com.bcp.workshop.auth.LegacyDatabaseTest.txt).
- [Caso de setup corregido](../target/testing-evidence/fixture-after/com.bcp.workshop.auth.LegacyDatabaseTest.txt).
- Endpoints: [login](../target/testing-evidence/endpoints/com.bcp.workshop.auth.AuthResourceTest.txt) y [salud](../target/testing-evidence/endpoints/com.bcp.workshop.auth.HealthResourceTest.txt).
- Gate final: [login](../target/testing-evidence/verify/com.bcp.workshop.auth.AuthResourceTest.txt), [salud](../target/testing-evidence/verify/com.bcp.workshop.auth.HealthResourceTest.txt) y [JDBC](../target/testing-evidence/verify/com.bcp.workshop.auth.LegacyDatabaseTest.txt). En las mismas carpetas estan los XML TEST-* de Surefire con los nombres y resultados individuales.

La ejecucion completa ya no depende del AUTH_DB_URL configurado manualmente durante la implementacion anterior. Se resolvio el bloqueo de preparacion, no se adapto un assert para ignorarlo. No se agregaron dependencias, perfiles que omitan casos, excludes de cobertura ni cambios a CodeQL o Dependency Review.

## Cobertura: reporte y metodo

Objetivo de [REGLAS.md](../REGLAS.md): al menos 85% del codigo modificado. En esta solicitud no se modificaron clases de produccion; se mide ademas todo src/main actual y LegacyDatabase, la clase productiva previamente modificada frente a main. Las clases de test y el proveedor externo de salud no se incluyen artificialmente en el denominador de produccion.

Se uso JaCoCo 0.8.12 ya declarado en el POM: prepare-agent durante tests y report en verify. El fichero exec del gate no existia antes de esa ejecucion, evitando cobertura acumulada. Metricas calculadas como `covered / (covered + missed) * 100`, leyendo los counters LINE y BRANCH del XML.

| ambito | lineas cubiertas/total | porcentaje |
|---|---|---|
| AuthResource | 25/25 | 100% |
| LegacyDatabase | 25/25 | 100% |
| LegacyTokenUtil | 3/3 | 100% |
| LoginRequest | 1/1 | 100% |
| UserRecord | 1/1 | 100% |
| Total produccion | 55/55 | 100% |

Total instrucciones: 265/265 (100%); ramas: 19/22 (86,36%); metodos: 10/10 (100%). Se cumple el objetivo por lineas. La cobertura de ramas de LegacyDatabase sigue siendo 5/8 (62,5%); no se presenta la cobertura global como si cada clase alcanzara ese porcentaje de ramas. Salud se acredita con respuestas HTTP verificadas, no con cobertura del codigo de una dependencia.

Reportes: [XML final preservado](../target/testing-evidence/verify/jacoco.xml), [HTML JaCoCo](../target/site/jacoco/index.html) y [XML JaCoCo actual](../target/site/jacoco/jacoco.xml). Los enlaces bajo target dejan de existir si se limpia el directorio; repetir verify regenera el reporte actual, no las instantaneas historicas.

Ejemplo para inspeccionar el XML del gate que si se ejecuto:

```powershell
[xml]$report = Get-Content target/testing-evidence/verify/jacoco.xml
$line = $report.report.counter | Where-Object type -eq 'LINE'
100 * [int]$line.covered / ([int]$line.covered + [int]$line.missed)
```

Ese calculo devuelve 100 para la evidencia indicada. No hay un check automatico de umbral en el POM actual: se conserva JaCoCo y se explicita la medicion, sin afirmar que Maven ya aplica un gate numerico que no esta configurado.

## Fallos y defectos, separados de los resultados verdes

- **Fallo de infraestructura reproducido y corregido:** AUTH_DB_URL no se preparaba para el fork de Surefire; una consola sin la variable hacia fallar setup y el comando de CI. Se preserva el reporte con exit 1 y el posterior con exit 0. No se redujeron requisitos del fixture.
- **Fallos actuales de la suite ejecutada:** ninguno en el gate de 37 tests. Un resultado verde acredita compatibilidad con ENDPOINTS.md, no ausencia de vulnerabilidades.
- **Defectos legacy conocidos, no corregidos aqui:** passwords en claro, token reversible sin firma, CORS amplio y excepciones internas en 500, ya descritos en [ENDPOINTS.md](ENDPOINTS.md) y seguidos en [HALLAZGOS.md](../HALLAZGOS.md). Los tests del contrato inseguro son caracterizacion, no tests que acrediten una autenticacion segura. No se modifican sus estados en este trabajo.
- **Sin hallazgos funcionales nuevos confirmados:** no se ajustaron expectativas al observar una discrepancia. Los nuevos casos HTTP, incluidos errores del framework antes solo observados en dev, pasaron tal como estaban documentados tambien en el perfil test.

## Pendientes y pruebas propuestas no ejecutadas

| escenario | motivo y estado |
|---|---|
| Salud negativa y cuerpo de error/503 | Pendiente: el contrato no define una condicion reproducible ni un check propio. No hay test omitido ni assert inventado. Requiere definir el contrato antes de implementarlo. |
| Perfiles prod y despliegue con proxy | Propuesto, no ejecutado: pruebas contra artefacto desplegado para errores del framework, TLS y limites de transporte. No se asumen headers, codigos o rutas diferentes. |
| Otras coerciones JSON o limites de tamano | Pendiente de definicion en ENDPOINTS.md; no se fija un limite ni un resultado esperado inexistente. |
| CodeQL y Dependency Review remotos | Pendiente de CI: configuraciones intactas, pero no se presentan como ejecutadas localmente ni se presupone PR. |

## Documentos consultados

[ENDPOINTS.md](ENDPOINTS.md), [REGLAS.md](../REGLAS.md), [GOOD_PRACTICES.md](GOOD_PRACTICES.md) y [README.md](../README.md). Se contrasto el fallo de setup con los reportes previos y se referencia [HALLAZGOS.md](../HALLAZGOS.md) como unico seguimiento de vulnerabilidades. La implementacion de pruebas y la medicion se verificaron con [pom.xml](../pom.xml), [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java), [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java) y [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java).