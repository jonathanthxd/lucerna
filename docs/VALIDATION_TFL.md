# Validación de la candidata TFL

## Evidencia disponible

- `bash tools/test-regressions.sh`: 2.035 comprobaciones de interacción/layout
  y 22 de contrato CPU, ejecutadas con el compilador incluido en Java 21.
- El CI con JDK 25 compiló el código y ejecutó esas mismas suites. La primera
  ejecución falló después en la configuración de Test: había fuentes ejecutables
  sin tests JUnit. Se separaron a `src/regression/java`, con tareas JavaExec
  dependientes de `check`; no se desactivaron assertions ni checks para ocultarlo.
- La suite `shaderSourceChecks` prueba las clases reales ProgramSet,
  ProgramFallbackResolver y TransformPatcher: descubrimiento DH, dimensión,
  ausencia de fallback arbitrario, DRAWBUFFERS 04, AST de vértices/materiales,
  rechazo por profundidad nativa y caché de transformación. También comprueba
  que declaraciones raw defectuosas producen IOException recuperable.
- `python tools/test-gl-cleanup.py`: 12 comprobaciones de inyección de fallos
  sobre GlShader y ProgramCreator reales, con llamadas GL simuladas. Verifica
  eliminación de handles al fallar source/compile/attach/link y propiedad en éxito.
  No mide fugas del driver ni ejecuta OpenGL real.
- `git diff --check`: sin errores al preparar los cambios.
- El build local completo no pudo descargar Gradle 9.7.0 por red y no tiene
  JDK 25 configurado; esa verificación se lleva a GitHub Actions.
- Sin arranque de Minecraft ni pruebas GL reales. Ninguna cifra anterior mide FPS,
  éxito de compilación GLSL por driver ni resultado visual.

## Crash de opciones

La evidencia del usuario corresponde a `IrisObjectSelectionList.mouseClicked`
(0.1.1). En 0.1.4 la clase se llama `LucernaObjectSelectionList` y ya captura
la fila antes del callback y valida el índice. La causa identificable en la
revisión antigua es la reconstrucción síncrona de una lista dentro del clic:
un índice 8 de la lista anterior deja de existir cuando el menú nuevo tiene 5 filas.
No se ejecutó el JAR 0.1.1 ni se reprodujo gráficamente ese stacktrace.

Quedaba un problema de estado en la revisión actual: tras reconstruir y limpiar
el foco, el callback volvía a enfocar la fila antigua. La nueva generación de
interacción impide esa restauración; las reconstrucciones limpian selección,
scroll y foco, y las liberaciones se consumen una sola vez. El widget pulsado se
mantiene durante el arrastre para no liberar otro widget bajo el cursor.
Columnas no válidas se diagnostican y se usa un layout automático acotado.

Las 2.035 assertions incluyen un ciclo de 1.000 reconstrucciones; no equivalen
a 2.035 escenarios gráficos distintos. Scroll nativo, escalado y navegación real
se mantienen en la siguiente matriz.

## Mensajes auditados individualmente

| Mensaje | Origen y tratamiento |
| --- | --- |
| shooting_star.png ausente | Recurso declarado por el pack. ShaderPack ya captura IOException y omite la textura. No se inventa el recurso; su efecto visual puede faltar. Se validan declaraciones vacías, namespaces y dimensiones raw para evitar errores fuera de rango/números sin controlar. |
| Invalid pack option PLAYER_SHADOW | ProfileSet detecta una opción que no pertenece al OptionSet. Advertencia y omisión ya existentes; puede ser perfil obsoleto o condición de preprocesador. Sin el ZIP exacto no se atribuye a un bug del loader. |
| menú PUDDLES desconocido | OptionMenuElementScreen captura IllegalArgumentException y pone un elemento vacío. Se conserva esa recuperación. Las nuevas validaciones de columnas evitan tamaños patológicos. |
| alphaTest ... false | Lucerna acepta `off` o función/referencia, y diagnostica `false` como inválido. No se lo traduce a otra semántica sin evidencia del pack; la directiva se ignora sin crash. |
| Unsupported uniform int isSnow | Diagnóstico de ProgramUniforms para un uniform no registrado, no una excepción. Existe `isSnowy` float en Lucerna, que no es el mismo contrato. No se inventa un valor de isSnow ni se cambia el tipo. Requiere inspeccionar el pack exacto. |
| error GLSL | GlShader/ProgramCreator ahora incorporan nombre/archivo y log al error, eliminan handles fallidos, y ProgramBuilder libera las etapas compiladas previamente. No se oculta una incompatibilidad grave del shader. |
| metadata JSON inválida | Se ignora metadata opcional con IOException/JsonParseException/tipos inválidos y se conserva la carga de la textura. El JSON vacío tampoco produce NPE. |

## Matriz de pruebas reales pendiente

Todos los casos siguientes necesitan Minecraft 1.8.9 real y evidencia del driver.
Los tests CPU no los certifican.

| Caso | Pasos y criterio |
| --- | --- |
| Sin shaders | Puente apagado; abrir mundo, mapa y opciones; comparar con 0.1.4. |
| Shaders convencionales | Puente apagado; comprobar terreno, cielo, sombras, partículas, agua y HUD con/sin Argentum. |
| Pack con DH | Adaptador TFL instalado y flag activo; verificar materiales/normales, niebla, depth0/depth1 y postprocesado con captura GPU. |
| Pack sin DH | hasProgram falso, API UNAVAILABLE; aplicar omisión/fallback descrito sin borrar depth del mundo. |
| LOD activados/desactivados | Comprobar que no hay doble dibujo, datos de otra sesión ni VBOs liberados prematuramente. |
| Cambio de shaderpack | Compilar/repetir/volver a desactivado; comprobar destrucción de programas/FBOs/texturas y estabilidad del menú. |
| Cambio de dimensión/mundo | Verificar proyección previa, separación de datos, recursos y recarga con dimensiones que no tengan programas DH. |
| Navegación intensiva | Scroll al final, submenú pequeño desde fila alta, clics rápidos, perfiles, Escape/volver, drag de sliders fuera del widget y GUI scale. |
| Recursos/directivas defectuosos | PNG ausente, JSON inválido, columna negativa/0/enorme, uniform desconocido y GLSL que no compila: diagnóstico y recuperación utilizables. |
| OpenGL limitado | Driver de compatibilidad sin DSA/compute y targets al límite; validar rechazo explícito cuando el pack requiere más capacidades. |
| Agua/oclusiones | Agua LOD delante/detrás de bloques, entidades y agua normal; orden back-to-front y ausencia de lectura de depth adjunto. |
| Resolución/TAA | Redimensionar, fullscreen, GUI scale y render quality; inspeccionar bordes con jitter y proyecciones extendidas. |

## Bloqueo de finalización

No declarar esta integración completa ni fusionar la rama hasta conectar los VBOs
reales de TFL Core, superar la matriz gráfica y probar Mellow 3.5 con su ZIP exacto.
