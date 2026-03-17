# AGENTS.md - Contexto del Proyecto

## 1) Que es este proyecto
Este repositorio implementa un **PoC (Prueba de Concepto) de OCR de facturas** en **Java puro (Java 17 + Swing)**.

El sistema:
- Lee imagenes desde una carpeta.
- Envia cada imagen a un proveedor OCR multimodal (`gemini` o `openai`).
- Detecta si el documento es factura.
- Extrae campos estructurados (numero, fecha, comercio, montos, etc.).
- Muestra resultados en UI y guarda salida JSON.

## 2) Para que sirve
Sirve para validar rapidamente un flujo de extraccion documental sin frameworks pesados:
- Clasificacion de documentos (factura / no factura).
- Extraccion de datos clave para automatizacion.
- Revision manual imagen por imagen en una UI simple.

## 3) Que se espera del proyecto
- Facilidad de uso: si falta configuracion, pedirla con UI.
- Flujo visual: procesar imagenes y mostrar avance.
- Salida util: prosa interpretada en pantalla + JSON persistido.
- Extensibilidad: poder ajustar prompts, reglas y proveedores.

## 4) Flujo funcional actual (alto nivel)
1. `Main` carga config desde `env/args/config.properties`.
2. Si faltan datos obligatorios, abre dialogo de configuracion.
3. Ventana principal (`MainFrame`) permite `Configurar` o `Procesar`.
4. Al procesar:
   - busca imagenes en carpeta;
   - abre `ProcessingDialog`;
   - procesa una imagen a la vez con avance manual (`Siguiente`).
5. En `ProcessingDialog` se muestra:
   - vista de imagen,
   - barra de progreso,
   - etiquetas de clasificacion:
     - `ES FACTURA: SI/NO`
     - `ES DE CARRO: SI/NO`
   - texto interpretado en prosa.
6. `raw_text` del OCR se registra en log nivel INFO.
7. Se guarda JSON acumulado en archivo de salida.

## 5) Estructura tecnica clave
- UI:
  - `src/main/java/com/poc/ocr/ui/MainFrame.java`
  - `src/main/java/com/poc/ocr/ui/ConfigurationDialog.java`
  - `src/main/java/com/poc/ocr/ui/ProcessingDialog.java`
- Config:
  - `src/main/java/com/poc/ocr/config/AppConfig.java`
  - `src/main/java/com/poc/ocr/config/LocalConfigStore.java`
- OCR/servicios:
  - `src/main/java/com/poc/ocr/service/GeminiOcrService.java`
  - `src/main/java/com/poc/ocr/service/OpenAIOcrService.java`
  - `src/main/java/com/poc/ocr/service/PromptFactory.java`
  - `src/main/java/com/poc/ocr/service/ImageScanner.java`
  - `src/main/java/com/poc/ocr/service/ResultWriter.java`
- Modelos:
  - `src/main/java/com/poc/ocr/model/*`

## 6) Reglas importantes para agentes
- Mantener compatibilidad con Java 17.
- No romper el flujo UI manual por imagen.
- No eliminar persistencia de `config.properties`.
- No exponer API keys en logs o archivos versionados.
- Mantener `OCR_PROVIDER=gemini` como opcion por defecto.
- Conservar salida JSON estructurada para trazabilidad.

## 7) Como ejecutar y validar
- Compilar:
  - `mvn clean package -DskipTests`
- Ejecutar:
  - `mvn clean compile exec:java`
- Verificar:
  - UI abre correctamente.
  - Config dialog aparece si falta carpeta/API key.
  - `Procesar` detecta imagenes.
  - `Siguiente` solo activo cuando hay pendientes.
  - Se genera archivo JSON de salida.

## 8) Guia de analisis para IA (Cursor/Copilot/otros)
Cuando analices este proyecto:
1. Revisa primero `Main`, `MainFrame`, `ProcessingDialog`.
2. Luego valida `AppConfig` + `LocalConfigStore`.
3. Verifica proveedores OCR y formato de respuesta JSON.
4. Comprueba que la UI no se bloquee (uso de `SwingWorker`).
5. Asegura que cambios en prompt no rompan parseo.

## 9) Mejoras esperadas (backlog recomendado)
- Agregar pruebas unitarias para:
  - parseo de JSON,
  - deteccion de factura de carro,
  - validacion de configuracion.
- Agregar reintentos y backoff en llamadas API.
- Agregar export adicional (CSV/Excel).
- Mejorar heuristicas semanticas de clasificacion no-factura.

