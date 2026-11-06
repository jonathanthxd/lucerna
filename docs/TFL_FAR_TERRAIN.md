# TFL Far Terrain — Lucerna API experimental v1

## Estado y alcance

Candidata `0.1.5-tfl.2`, sobre Lucerna `0.1.4` / commit base
`ba8b45059e13d6ac8e3a5bf85024f4b61f4eef9f`. Rama de recuperación:
`checkpoint/pre-tfl-far-terrain-ba8b450`. No fusionar aún a `main`.

Se implementó una ruta del lado Lucerna que recibe VBOs reales, compila los
programas `dh_terrain`/`dh_water` mediante el pipeline GLSL existente y ejecuta
`glDrawArrays` contra los destinos declarados por el shaderpack. No se usa
`gbuffers_terrain` como sustituto. Esto NO demuestra todavía que los LOD reales
de TFL Core se hayan dibujado con éxito: el consumidor actual no llama a esta API
ni tiene el formato de vértice requerido. No se ejecutó Minecraft. En tfl.2 se
añadieron pruebas OpenGL nativas de samplers/postprocesado en Mesa, sin geometría
LOD ni escena Minecraft; ver SAMPLER_REGRESSION_TFL_2.md. El interruptor experimental está desactivado por defecto.

## Auditoría reproducible

- Lucerna base: [ba8b450](https://github.com/jonathanthxd/lucerna/tree/ba8b45059e13d6ac8e3a5bf85024f4b61f4eef9f).
- TFL Core: rama `feat/audit5-integrations`, commit
  [7afb5e2](https://github.com/TFLivesStudio/TFLClientMOD/tree/7afb5e2503749b8f5dd8d8019f0733fa25280da6).
  `OrnitheFarTerrain` está en `vendor/trs-client/ornithe/src/main/java/dev/theredstonee/trsclient/ornithe/`.
  `main` y `fix/server-map-sessions` no contienen ese renderer en las revisiones inspeccionadas.
- Iris inspeccionado: [bff1e69](https://github.com/IrisShaders/Iris/tree/bff1e69cb6c5519d8745784aa9c8b92984de67e7),
  especialmente `compat/dh` y `DHTerrainTransformer`.
- Contrato oficial: [ShaderDoc: DH](https://github.com/IrisShaders/ShaderDoc/blob/ddf473726710c6504422d192784c1652f8aece98/dh-support.md).
- Shaderpack real inspeccionado: [Complementary Reimagined c09950d](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/tree/c09950df0650b27dac260c1b0f69afc11387aa6f),
  `shaders/program/dh_terrain.glsl`, `dh_water.glsl` y wrappers de dimensiones.
  En tfl.2 se inspeccionó y compiló el ZIP original Mellow 3.5; el alcance exacto
  y su SHA-256 están documentados en SAMPLER_REGRESSION_TFL_2.md.
  No se copiaron shaders de esos proyectos al JAR.

### Pipeline Lucerna

`ShaderPackSourceNames` enumera entradas de programas, incluyendo las variantes
por dimensión. `ShaderPack` construye el grafo de includes, opciones, macros,
condiciones y perfiles antes de crear `ProgramSet`. Los programas DH se incorporan
a ese mismo recorrido únicamente con el interruptor experimental activo.
`ProgramSet` recoge directivas de destinos y formatos también desde los programas DH.
Los IDs DH no tienen fallback hacia gbuffers ni entre terreno y agua.
Se conserva la selección de ProgramSet por dimensión de Lucerna; no se añade una
regla distinta de herencia por archivo. Una dimensión con programas propios sigue
las reglas existentes, y un par incompleto se considera ausente.

`DeferredWorldRenderingPipeline` conserva sus fases y sus render targets con
alternancia de texturas. La nueva API solo acepta terreno durante la parte opaca,
y agua después de `beginTranslucents` (que ejecuta deferred). Ambos deben llamarse
antes del terreno/agua convencionales respectivos. No acepta llamadas dentro de
una pasada de sombras, postprocesado o renderizado Argentum/Celeritas ya iniciado.

Cada pasada DH tiene FBOs propios que apuntan a los mismos destinos de color que
el shaderpack usa en esa fase. La profundidad DH es independiente de la vanilla.
`dhDepthTex0` contiene terreno y agua; `dhDepthTex1` es una copia GPU de la
profundidad opaca. Durante el dibujo DH se lee la copia opaca para evitar feedback
con la textura de profundidad adjunta al FBO activo. No hay readback de imágenes
o vértices GPU a CPU. La profundidad nativa NO se borra ni recibe un blit de valores
calculados con una proyección distinta.

El transformador de fragmentos reproyecta la profundidad DH al espacio de la
proyección nativa y descarta fragmentos detrás del terreno normal. Esto resulta
especialmente necesario para agua dibujada después de deferred. Requiere QA real
en bordes, TAA y packs que escriban `gl_FragDepth`; no se certifica esa combinación.
Los uniforms DH y samplers también se registran en los constructores comunes de
programas, para que deferred/composite/final puedan consumirlos.

### Diferencias con DH oficial

| Aspecto | TFL Core inspeccionado | Contrato Lucerna v1 |
| --- | --- | --- |
| Vértices | 8 floats, 32 bytes, GL_QUADS | 13 floats, 52 bytes, GL_TRIANGLES |
| Datos | posición, RGB y luz | posición, RGBA, normal, luz y mini-ID DH |
| Agua | mezclada en el terreno de superficie | VBOs de agua separados |
| Materiales | estados conservados en TerrainColumns, no subidos al VBO | clasificación DH_BLOCK_* por vértice |
| Profundidad | extendida y borrada al terminar el dibujo LOD | texturas DH propias y comparación reproyectada |
| Shader | `far-terrain.vert/.frag`, con iluminación/niebla propias | programas reales del pack |
| API oficial DH | no implementada | no requerida; tampoco se simula su identidad |
| Sombras DH | inexistentes | no implementadas en v1 |

## API que realmente existe en esta rama

Propiedad y distribución: paquete `us.z1x.lucerna.api.farterrain`, propiedad de
Lucerna, incluido en su JAR remapeado; `FarTerrainApi.VERSION == 1`.
TFL Core debe usar una dependencia `modCompileOnly` de ese JAR y una clase adaptadora
que solo se cargue cuando Fabric Loader detecte `lucerna`. No copiar ni sombrear
clases de la API dentro de TFL Core. Lucerna no importa clases de TFL Core.

Métodos públicos:

```java
FarTerrainApi.isPipelineActive();
FarTerrainApi.hasProgram(FarTerrainApi.Phase.TERRAIN);
FarTerrainApi.hasProgram(FarTerrainApi.Phase.WATER);
FarTerrainApi.render(phase, frame, meshes);
```

`hasProgram` significa que existe un par válido de fuentes en el ProgramSet actual.
No promete que GLSL compile ni que el pack sea compatible. La compilación real es
perezosa, en la primera solicitud de dibujo. Los errores se diagnostican con
programa/archivo y desactivan el puente de ese pipeline hasta recargar el pack.
Los recursos creados permanecen bajo propiedad del pipeline y se destruyen con él.

`FarTerrainFrame` recibe matrices column-major de 16 floats, near/far reales e
int de distancia en **bloques**. Defensivamente copia las matrices. ModelView es
la matriz de cámara sin la traslación de cada región. Cada mesh añade la traslación
`origenDeRegión - cámaraInterpolada`. Proyección extendida y planos deben describir
el mismo frustum. Reutilizar exactamente el mismo objeto frame entre terreno y agua
de ese fotograma. La proyección anterior pertenece al pipeline de esa dimensión.

`FarTerrainMesh` presta un VBO, cantidad de vértices y traslación xyz. Lucerna no
sube, duplica ni destruye ese VBO. El buffer debe seguir vivo hasta terminar ambas
llamadas. Layout fijo:

| Offset en bytes | Datos float |
| --- | --- |
| 0 | posición xyz relativa a región |
| 12 | RGBA sin iluminación/niebla precalculadas |
| 28 | normal xyz unitaria de cara, en ejes del mundo |
| 40 | luz bloque/cielo, cada una `(nivel + 0.5) / 16` con nivel 0..15 |
| 48 | mini-ID entero DH representado exactamente como float |

Stride 52 bytes. Triángulos con winding coherente. No enviar quads del buffer
actual como si ya fueran triángulos. Materiales: UNKNOWN=0, LEAVES=1, STONE=2,
WOOD=3, METAL=4, DIRT=5, LAVA=6, DEEPSLATE=7, SNOW=8, SAND=9,
TERRACOTTA=10, NETHER_STONE=11, WATER=12, GRASS=13, AIR=14, ILLUMINATED=15.
No reinterpretar IDs de bloque 1.8.9 como mini-IDs DH. En 1.8.9 no hay deepslate.

### Pasos concretos en TFL Core, pendientes

1. Trabajar sobre la revisión que contiene `OrnitheFarTerrain`, no asumir `main`.
2. Ampliar el mesher de FarTerrainEngine para conservar normales, material y alfa.
   Separar agua de opacos; conservar los estados de TerrainColumns para clasificar.
3. Convertir quads a triángulos al construir el mesh; mantener VBOs persistentes,
   cargas con presupuesto y descarte por revisión ya existentes. No convertir o
   duplicar todos los VBOs por fotograma.
4. En el hook actual después del cielo y antes del terreno, construir un frame
   con la cámara/proyección extendida actual y pasar los meshes opacos a TERRAIN.
   Quitar de esa ruta la selección del shader interno y el borrado final de depth.
   La API gestiona programa, FBO, uniforms y estado; el caller no debe cambiarlos.
5. Añadir hook después de Lucerna.beginTranslucents y antes del agua vanilla,
   llamar WATER con el mismo frame y meshes transparentes ordenados back-to-front.
   No hacer ambas llamadas desde el hook del cielo. Evitar doble dibujo por frame.
6. Gestionar resultados explícitamente. Sin pipeline o con el interruptor apagado,
   usar el renderer original. Con pipeline activo y sin DH completo, opción segura:
   omitir LOD y mostrar diagnóstico; dibujarlos con el shader original tiene
   limitaciones visuales y su borrado de depth puede corromper el mundo.
7. Ante FAILED, no redibujar encima en el mismo fotograma: puede haber escrituras
   parciales de color. Desactivar LOD para ese pipeline y permitir recarga del pack.
8. Al desconectar/cambiar dimensión, descartar frames/meshes de sesiones anteriores;
   conservar las reglas de identidad y presupuesto. No modificar el formato de
   caché persistente sin migración; se puede reconstruir el VBO desde datos existentes.

No se modificó TFLClientMOD en esta tarea.

## Activación, instalación y recuperación

**Para probar GUI/estabilidad:** Java 25, Minecraft 1.8.9, Ornithe Gen2,
Argentum 1.0.2, Legacy LWJGL3 1.4.10 y las demás dependencias habituales.
Cerrar Minecraft, guardar el JAR anterior fuera de `mods`, y reemplazarlo por
`lucerna-0.1.5-tfl.2.jar` remapeado del artefacto de Actions del PR. Dejar un solo
JAR de Lucerna. No usar el JAR `-dev` ni cambiar Argentum/LWJGL3.
La compilación conserva Fabric Loader 0.19.5 como dependencia de desarrollo;
la validación con el loader 0.20.1 del usuario queda en la matriz manual.

**Para probar DH:** primero implementar el adaptador anterior y luego añadir
`-Dlucerna.experimentalFarTerrain=true` a los argumentos de la JVM del perfil de
prueba. Es una propiedad de arranque; reiniciar para cambiarla. Activa macros
DISTANT_HORIZONS/DH_BLOCK_*, descubrimiento y la API. No activarla en el modpack
actual sin el adaptador: el shaderpack puede cambiar sus fases por esas macros
sin recibir geometría LOD. No se define DISTANT_HORIZONS_TEXTURES.

Recuperación: quitar la propiedad y reiniciar. Si hace falta, restaurar el JAR
anterior. No borrar mundos, opciones de shaders ni cachés del usuario.

## Limitaciones explícitas

- Integración con geometría real TFL pendiente de los cambios de consumidor listados.
- Sin pruebas gráficas, mediciones FPS, captura de FBOs o certificación de GPU.
- Sin dh_shadow, terreno texturizado DH, tessellation ni formato VBO empaquetado DH.
- Se rechazan programas DH con image load/store; no se certifican SSBOs, atomics,
  gl_FragDepth, viewport/tamaños de salida personalizados o usos de atributos fuera
  del contrato. Se rechazan explícitamente scale/flip/mipmap por pasada DH. No se puede afirmar compatibilidad completa con todos los packs DH.
- Las primitivas transparentes deben llegar ordenadas desde TFL Core.
- El fallback de errores preserva el renderer y registra el problema, pero no revierte
  colores parcialmente escritos por una pasada. Por eso no se reintenta en el mismo frame.
- La inicialización excepcional de un pipeline tiene que seguir sometiéndose a pruebas
  de recursos GPU: las pruebas CPU no demuestran ausencia absoluta de fugas del driver.
