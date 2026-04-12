# PoC OCR Factura (Java puro)

Proyecto en Java 17 (sin framework) para:

1. Buscar imagenes en una carpeta.
2. Enviarlas a un modelo multimodal (`gemini`, `openai` o `banco`).
3. Hacer OCR.
4. Detectar si el documento es factura.
5. Extraer campos clave en JSON.

Incluye UI en Swing para configuracion y procesamiento.

## Requisitos

- Java 17+
- Maven 3.9+
- API key del proveedor elegido

## Configuracion

Usa variables de entorno (ver `.env.example`):

- `OCR_PROVIDER`: `gemini` (default), `openai` o `banco`
- `INPUT_DIR`: carpeta de imagenes (default `input`)
- `OUTPUT_FILE`: ruta del JSON de salida (default `output/results.json`)
- `MAX_IMAGES`: limite de imagenes a procesar (default `0` = todas)
- `GEMINI_API_URL`, `GEMINI_API_KEY`, `GEMINI_MODEL`
- `OPENAI_API_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL`
- `BANCO_API_URL`, `BANCO_API_KEY`, `BANCO_MODEL`

## Ejecucion

```powershell
$env:OCR_PROVIDER="gemini"
$env:GEMINI_API_KEY="TU_API_KEY"
$env:INPUT_DIR="input"
$env:OUTPUT_FILE="output/results.json"
mvn clean compile exec:java
```

### Flujo UI

Si faltan datos (carpeta o API key segun proveedor), se abre una ventana Swing para:

- Seleccionar carpeta de imagenes.
- Elegir proveedor (`gemini`, `openai`, `banco`).
- Configurar por proveedor:
  - API URL
  - MODELO (combo editable)
  - API KEY

La configuracion se guarda localmente en `config.properties`.

Modelos sugeridos en la UI:

- Gemini: `gemini-3.1-flash-lite-preview` (default), `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-2.0-flash`, `gemini-2.0-flash-lite`.
- OpenAI: `gpt-4.1-mini` (default), `gpt-4.1`, `gpt-4.1-nano`, `gpt-4o-mini`, `gpt-4o`, `gpt-5-mini`, `gpt-5`, `gpt-5-nano`.
- Banco: `banco-ocr-v1` (default), `banco-ocr-v2`.

En la ventana principal:

- Boton `Procesar`: revisa la carpeta y busca imagenes.
- Si hay 1 o mas, abre dialogo de procesamiento.
- Tambien soporta archivos PDF.

En el dialogo de procesamiento:

- Muestra la imagen actual.
- Muestra barra de progreso mientras envia a API y recupera informacion.
- Muestra abajo el texto OCR recuperado (`raw_text`).
- El avance es manual con `Siguiente`.
- `Siguiente` solo se activa si hay imagenes pendientes.
- Si un PDF no trae imagenes extraibles y es PDF de texto, se marca como sospechoso de posible documento falso.
- Regla de utilidad principal: solo se considera factura util cuando cumple:
  - es factura,
  - es de Chile (`CL`),
  - es factura de compra/venta de vehiculo.

Tambien puedes sobreescribir por CLI:

```powershell
mvn exec:java -Dexec.args="--provider=gemini --input=input --output=output/results.json --max-images=10"
```

## Formato de salida

Se genera un JSON con un objeto por imagen procesada:

- `success`: si la llamada y parseo fueron exitosos
- `error`: mensaje de error si fallo
- `extraction`: resultado OCR (solo cuando `success=true`)

Campos extraidos principales:

- `is_invoice`
- `document_type`
- `confidence`
- `invoice_number`
- `invoice_date`
- `supplier_name`
- `supplier_tax_id`
- `customer_name`
- `subtotal`, `tax`, `total`, `currency`
- `raw_text`
- `line_items`
- `notes`

## Notas

- Este PoC usa prompting para extraer JSON; para produccion conviene agregar validacion y reintentos.
- La precision depende de calidad de imagen, idioma y formato de factura.
- Extensiones soportadas: `png`, `jpg`, `jpeg`, `webp`, `bmp`, `tif`, `tiff`, `pdf`.
