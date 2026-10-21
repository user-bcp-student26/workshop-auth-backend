## ADDED Requirements

### Requirement: El 500 no expone detalles internos
El servicio SHALL responder `500` sin el nombre de la clase de excepción ni el mensaje interno, y SHALL registrar la excepción con su traza en el log del servidor. Origen: Issue #12 (H-05).

#### Scenario: Excepción no controlada
- **WHEN** ocurre una excepción no controlada en `POST /auth/login`
- **THEN** la respuesta es `500` con un mensaje genérico
- **AND** el cuerpo no contiene el nombre de la clase de excepción ni el mensaje original
- **AND** el log del servidor contiene la excepción con su traza
