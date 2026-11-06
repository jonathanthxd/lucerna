# Lucerna 0.1.5-tfl.2 — corrección de la unidad 0

## Causa confirmada

En `0.1.5-tfl.1`, `LucernaSamplers.addRenderTargetSamplers` registraba DH antes
de los destinos de color. En los programas de pantalla completa la unidad 0 está
libre para el sampler predeterminado: solo se reservan externamente las unidades
1 y 2. Por eso `dhDepthTex0` o `dhDepthTex1` activos recibían 0; después
`addDefaultSampler(colortex0, gcolor)` lanzaba exactamente
`IllegalStateException: Texture unit 0 is already used.` Incluso si colortex0 está
optimizado y no tiene location, el sampler predeterminado debe reservar esa unidad.

Se conserva esa protección del Builder; no se oculta la excepción. Se corrige la
secuencia real de registro: default/colortex, DH, imágenes personalizadas, resto
de samplers. El interceptor de customtex sigue añadiendo sus texturas después del
primer sampler; ahora ese primero vuelve a ser el predeterminado. Los programas
sin DH conservan sus asignaciones originales.

La misma función es usada por CompositeRenderer y FinalPassRenderer, en programas
de dibujo y compute; incluye deferred porque utiliza CompositeRenderer. Los
programas del mundo reservan 0/1/2 y mantienen esas reservas. Se encontró además
que un override personalizado de la textura externa 0 intentaba invocar
addDefaultSampler aunque 0 ya estuviera reservado: ahora se registra como un
sampler dinámico propiedad del programa, igual que los otros overrides externos.
Se prueban tanto sus unidades como los proveedores de texturas que finalmente se enlazan.

No se modificó TFL Core, las fases DH, ni la elección de renderer sin shaders.

## Límites de hardware

`SamplerLimits` consulta GL_MAX_TEXTURE_IMAGE_UNITS y
GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS y los acota también a
`GlStateManager.TEXTURES.length`. Se usa el mínimo conservador; no se sustituye
el límite de fragmentos por el límite combinado, que puede ser mucho mayor.
El driver sigue validando los límites por etapa al enlazar GLSL. No se supone que
una GPU antigua tenga 16 o 32 unidades disponibles. Reservas negativas/fuera de
rango fallan explícitamente. El agotamiento informa sampler, programa, límite
utilizable y reservas; nunca envía un índice mayor al permitido.

Pruebas CPU: capacidades de 4, 8 y 16 unidades, agotamiento exacto, uniforms
optimizados, aliases, overrides, unidades externas y límite del caché de 32 frente
a un límite combinado de 192. Una GPU insuficiente debe rechazar el pack con
ese diagnóstico, no aliasar texturas ni desactivar efectos silenciosamente.
No se probó físicamente una GPU antigua.

## Mellow original y reproducción

Archivo original del usuario: `Mellow v3.5.zip`, 1.955.122 bytes, SHA-256:
`b46c5460cae9afe4d5b68bf6ac398661c01278d1c50b99070931f1477ecf8899`.

Se expandieron includes y se preprocesaron las opciones predeterminadas con GCC,
MC_VERSION=10809, IS_IRIS, MC_GL_VERSION=450 y las definiciones DH_BLOCK_*.
Se ejecutaron dos variantes: DISTANT_HORIZONS ausente y presente. Se compilaron y
enlazaron realmente los pares vsh/fsh de los 17 programas de pantalla completa de
world0: 34 programas, sin errores en esta prueba. No se modificó el ZIP.

En Mesa 25.0.7 / llvmpipe, OpenGL 4.5 compatibility, el driver informó 32 unidades
de fragmentos, 192 combinadas y 32 de vértices. Se consultaron sus uniforms
**activos después del enlace**, no solo declaraciones encontradas por regex.
Los resultados están en `src/regression/resources/mellow-3.5-active-samplers.json`.
No contiene el shaderpack ni su implementación GLSL.

Las firmas de composite, composite1, deferred y deferred1 con DH reproducen la
colisión tfl.1 al ejecutar primero el registro DH en el Builder real de Lucerna.
La secuencia corregida se reproduce para todos los programas con presupuestos
simulados de 8 y 16 unidades, verificando que todos los samplers activos quedan
registrados, dentro de rango y sin colisiones.

Repetir el probe local (requiere GCC, libEGL, libGL y Mesa):

```bash
python3 tools/probe-mellow-samplers.py '/ruta/Mellow v3.5.zip' mellow-probe.json
```

Este probe usa GLSL del pack y GCC; **no usa los parches AST de Lucerna ni lanza
Minecraft**. Es evidencia del contrato de samplers y compilación del pack, no una
certificación de todos los efectos o perfiles.

## Regresiones automatizadas y OpenGL nativo

```bash
./gradlew build
EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1 ./gradlew samplerGlChecks
```

`samplerRegistrationChecks` ejecuta el Builder y las funciones de registro
**de producción**. Solo se inyectan la consulta de locations y la capacidad, que
en producción proceden del driver. Incluye firmas Mellow y casos con/sin DH,
aliases legacy, colortex0 optimizado, customtex, overrides externos y agotamiento.
Guarda sus asignaciones en `build/reports/sampler-cases.json`.

`samplerGlChecks` usa esas asignaciones en 45 casos de OpenGL real por EGL: crea
programas y texturas, registra uniforms, dibuja un quad, aplica una transformación
de color y compara el píxel leído contra el resultado numérico esperado. Comprueba
que la textura normal y el postprocesado no se sustituyen por profundidades DH.
Es una prueba sintética de bindings/postprocesado con las firmas reales del pack,
no un render visual de Mellow dentro de Minecraft.

El workflow ejecuta ambas tareas y conserva las asignaciones en el artefacto
`lucerna-sampler-evidence`; el artefacto `lucerna` conserva exclusivamente el JAR.
El probe de Mellow necesita el ZIP original y no se vuelve a descargar desde CI.

## Instalación y límites de la entrega

Java 25, misma instancia Ornithe 1.8.9 y mismos mods. Cerrar el juego, conservar
el JAR anterior fuera de mods y sustituirlo por `lucerna-0.1.5-tfl.2.jar` del ZIP
de Actions. Dejar una sola copia de Lucerna. No cambiar TFL Core para corregir
este fallo. El flag `-Dlucerna.experimentalFarTerrain=true` sigue siendo opt-in;
la corrección no lo activa por defecto.

La creación completa de DeferredWorldRenderingPipeline y el resultado visual de
Mellow dentro de Minecraft no se ejecutaron en esta corrección. Sigue pendiente
la prueba conjunta de dh_terrain, dh_water, profundidad y postprocesado con
geometría real del consumidor. No declarar compatibilidad LOD completa.
