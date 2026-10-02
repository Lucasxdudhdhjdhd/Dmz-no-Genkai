# DMZ Ki Aspects

Supuestos tomados (ajustables): modId `dmzkiaspects`, paquete `com.brochacho.dmzkiaspects`,
siguiendo el estilo de nombres de tus otros addons DMZ.

## Para compilar localmente
1. Poné `dragonminez-2_1_3.jar` en `libs/` (creá la carpeta si no existe).
2. Ajustá la dependencia en `build.gradle` según el comentario ahí (nombre exacto del jar).
3. `./gradlew build` (Linux/Mac) o `gradlew.bat build` (Windows). El wrapper ya está
   commiteado (Gradle 8.1.1, la versión que pide ForgeGradle 6.0.x), no hace falta tener
   Gradle instalado aparte.

## CI — GitHub Actions
`.github/workflows/build.yml` compila el addon en cada push/PR a `main`/`master` y en
`workflow_dispatch` (botón "Run workflow" manual), y sube la `.jar` resultante como artifact
de la corrida (pestaña Actions -> la corrida -> Artifacts).

**Antes de que el primer build funcione**, resolvé la jar de DragonMineZ (no se puede commitear
en el repo, no es nuestra para redistribuir) de una de estas dos formas:

- **Opción A — commitearla vos:** sacá `libs/*.jar` del `.gitignore` y agregá
  `dragonminez-2_1_3.jar` al repo. Más simple, pero infla el repo y solo tiene sentido si es
  privado.
- **Opción B — secret `DMZ_JAR_URL`** (recomendado si el repo es público): subí la jar a algún
  lado que vos controles (un Drive con link directo, un Nexus/servidor propio, un release de
  otro repo privado tuyo) y en este repo andá a **Settings → Secrets and variables → Actions →
  New repository secret**, nombralo `DMZ_JAR_URL` y pegá la URL de descarga directa. El
  workflow la baja sola en cada corrida.

Si no hacés ninguna de las dos, el build falla en el paso "Resolver dependencia de
DragonMineZ" con un mensaje explicando exactamente qué falta — no como un error de
compilación confuso más adelante.

**Si el build falla por otro motivo** (error de compilación, etc.), el workflow guarda el log
completo en `ci-logs/last-failure.log`, commiteado de vuelta al repo automáticamente. Un
`git pull` alcanza para leerlo — no hace falta entrar a la pestaña Actions ni bajar nada de ahí.

## Fase 1
- `capability/KiAspectsData.java` — los 5 aspectos + fórmulas placeholder + NBT.
- `capability/KiAspectsProvider.java` — ICapabilityProvider (patrón de StatsProvider).
- `capability/KiAspectsCapability.java` — registro, ciclo de vida, y listeners de
  `DMZEvent.StatChangeEvent` / `FormChangeEvent` / `PlayerDataLoadEvent` para recalcular.
- `command/KiAspectsDebugCommand.java` — `/dmzkiaspects [jugador]`.
- Sin mixins. Todo contra la API pública de DMZ (`StatsCapability`, `DMZEvent`).

## Fase 2
- Presencia se superpone al Power Release real de DMZ (`Resources#getPowerRelease`/`setRelease`, 0-100 <-> 0.0-1.0):
  se sincroniza al loguear/cargar datos y después la controla este addon.
- Cargar Ki (C) = `Status#isChargingKi()`: de pie sube Presencia, agachado la baja.
  Eventos propios: `KiChargeEvent`, `PresenceChangeEvent`.
- `getVisibleBattlePower()` = `kiDeCombate * presencia`. Sin mixins no reemplaza aún la lectura del
  Scouter/Ki Sense de DMZ (`ScouterHUD`, `KiSenseScan`).

## Fase 3
- `calc/KiAspectsCalculator` — fórmulas puras:
  - Ki de Combate = STR*fmult("STR") + SKP*fmult("SKP") + PWR*fmult("PWR"), con `StatsData#getFormMultiplier(stat)`.
    (Corrige la aproximación de Fase 2: el String que recibe es la clave de stat, no la forma.)
    Excluye formas apiladas (Kaioken/Ultimate) y efectos a propósito: son acceso a potencial oculto (Fase 4).
  - Ki Completo = Ki de Combate / `COMBAT_KI_FRACTION` (0.65).
  - Poder Real = mejor forma DESBLOQUEADA (`TransformationsHelper#getUnlockedForms` sobre todos los grupos de la
    raza), nunca menor al Ki Completo de la forma activa.
- `config/KiAspectsConfig` — constantes de balance (fracción de combate, tipos de expulsión, tope de bonus, ventana).
- Expulsión = `KiType` WAVE, BEAM, GIANT_BALL, EXPLOSION. Una expulsión sube de 65% a 100% del Ki Completo a medida
  que se sobrecarga (`chargeMultiplier` de DMZ, 1.0 -> 1.75). Las demás técnicas se quedan en la parte de combate.
- Hooks: `KiAspectsEvent.ExpulsionCastEvent` y `ExpulsionFireEvent` (este permite `setDamageBonus`).
- `technique/ExpulsionTracker` + `DMZEvent.DamageModifyEvent` (fuente KI): el bonus se aplica sobre el daño que DMZ ya calculó.
- `/dmzkiaspects` muestra además la forma que da el Poder Real y la última expulsión.

## Fase 4
- `KiAspectsData` ya no calcula el Límite Actual como derivado de Poder Real: es un valor **persistente**
  (se guarda en NBT junto a `lastKnownLevel` y `ticksSinceTimeGrowth`) que solo crece por estas vías:
  - **Ruptura de límite** (`applyBreakthroughIfNeeded`): si el Poder Real alcanza o supera el Límite Actual
    registrado, el Límite salta a `Poder Real * POTENTIAL_BREAKTHROUGH_MARGIN` (1.15 por defecto). Este mismo
    mecanismo cubre el caso inicial (Límite arranca en 0).
  - **Nivel** (`applyLevelUpGrowth`): compara `StatsData#getLevel()` contra el último visto; por cada nivel
    ganado, el Límite crece `LEVEL_UP_LIMIT_GROWTH_PERCENT` (5%) de sí mismo. Ojo: en DMZ el "nivel" es un
    derivado de stats totales vs. el máximo configurado (`getConfiguredMaxValue`), no un sistema de XP aparte;
    no encontré ningún concepto de "edad" expuesto públicamente, así que usé el nivel como proxy.
  - **Tiempo jugado** (`tickTimeGrowth`, llamado cada tick de jugador): cada `TIME_GROWTH_INTERVAL_TICKS`
    (24000 = 1 día de Minecraft) acumulados, el Límite crece `TIME_GROWTH_PERCENT` (1%) de sí mismo.
  - **Eventos especiales / otorgamiento manual**: `KiAspectsCapability.grantPotentialFlat(player, cantidad, razon)`
    y `grantPotentialPercent(player, porcentaje, razon)`, pensados para que una quest, un admin, o Kaioken/Zenkai
    (Fase 5) le den Potencial Oculto real a un jugador.
- Todas esas vías disparan `KiAspectsEvent.PotentialLimitChangeEvent` (con la razón: `breakthrough`, `level_up`,
  `time`, o la que pases a un grant manual).
- Comandos nuevos:
  - `/dmzkiaspects potencial [jugador]` — desglose de Poder Real, Límite Actual, Potencial Oculto, % usado,
    y de dónde sale cada tipo de crecimiento.
  - `/dmzkiaspects potencial <jugador> grant <cantidad>` (permiso 2) — otorga Límite Actual fijo.
  - `/dmzkiaspects potencial <jugador> grantpercent <porcentaje>` (permiso 2, 0-1000) — otorga en %.
  - `/dmzkiaspects potencial <jugador> set <valor>` (permiso 2) — fuerza el Límite Actual (debug/QA); nunca
    lo deja por debajo del Poder Real actual.
  - `/dmzkiaspects` (el comando principal) ahora también muestra `%` de Potencial usado.
- **Corregido un bug de la Fase 3**: había quedado código huérfano en `KiAspectsData.java` (un `return` y una
  llave de más, resto del viejo `safeRatio`) que rompía la compilación. Ya está limpio desde este zip en adelante.

## Fase 5 — Kaioken y Zenkai

Diseño final (hubo un cambio de planes a mitad de esta fase; esto es lo que quedó):

### Kaioken
- Es un multiplicador real (x2, x3, x10, x20, x100...), leído directamente del
  stack form "kaioken" de DMZ (`Character#getActiveStackFormGroup/Form`). DMZ ya
  aplica ese multiplicador a los stats de combate reales (STR/SKP/PWR vía su propio
  `getStackFormMultiplier`); este addon no lo toca ni lo duplica ahí.
- Lo que el addon agrega es el **costo** y el **reporte**, usando el Potencial Oculto
  de la Fase 4 (`calc/KiAspectsCalculator#evaluateKaioken`):
  - **benefitFactor** (0.25 a 1.0): qué fracción del "extra" del multiplicador se
    obtiene de verdad. Con margen amplio de Potencial Oculto (≥50% del Límite Actual)
    es 1.0 (beneficio completo); si no queda margen cae hasta 0.25 — nunca a 0, el uso
    nunca se bloquea, solo se vuelve poco rentable.
  - **effectiveMultiplier** = 1 + (multiplicador-1) × benefitFactor. Es el número que
    se aplica al poder que se está usando en ese momento: se multiplica directamente
    sobre el bonus de daño de una expulsión (Fase 3, `onDmzKiAttackFire`) cuando se
    dispara en pleno Kaioken — así se apila con Super Saiyan Blue, Ultimate, etc., que
    ya están adentro del Ki de Combate/Completo calculado.
  - **dangerFactor/dangerMultiplier**: combina cuánto Potencial Oculto queda y qué tan
    cerca está el Poder Real del Límite Actual. Alimenta tanto la velocidad de
    acumulación de desgaste (`kaiokenStrainPerTick`) como el daño de cada pulso
    (`kaiokenPulseDamageFraction`, con tope de seguridad `KAIOKEN_MAX_DAMAGE_PERCENT_MAX_HEALTH`).
  - Cuanto más alto el multiplicador Y más cerca del límite/sin potencial, más
    frecuentes y más fuertes los pulsos de daño real (`player.hurt(...)`).
- Estado efímero (no persistido) en `kaioken/KaiokenTracker`. Eventos:
  `KiAspectsEvent.KaiokenStateEvent` (activar/desactivar, con la evaluación completa)
  y `KaiokenStrainEvent` (cada pulso de daño).
- `/dmzkiaspects kaioken [jugador]` muestra multiplicador elegido, efectivo, beneficio
  y peligro actuales.

### Zenkai
- Exclusivo Saiyan (`Character#getRaceName().equalsIgnoreCase("saiyan")`) — es un
  sistema propio del addon, no el racial "Zenkai" que DMZ ya trae (ese sigue
  funcionando en paralelo, sin relación).
- Se dispara tras `ZENKAI_REQUIRED_TICKS` (8s) seguidos bajo `ZENKAI_HEALTH_THRESHOLD`
  (15% de vida), vigilado en `zenkai/ZenkaiTracker` (efímero: perder la racha al
  desconectar no es explotable, solo obliga a repetir la espera).
- Convierte `ZENKAI_CONVERSION_FRACTION` (15%) del Potencial Oculto restante en Poder
  Real **permanente**, guardado en `zenkaiBonusPower` (persistente en NBT, se suma al
  Poder Real recalculado en cada `recalculateFrom` para que sobreviva a todos los
  recálculos futuros). También cura al jugador (`ZENKAI_HEAL_FRACTION`).
- Si la vida mínima durante la racha fue ≤`ZENKAI_EXTREME_HEALTH_THRESHOLD` (5%,
  "herida extrema"), además empuja el Límite Actual hacia arriba en una fracción del
  Potencial Oculto restante (`ZENKAI_EXTREME_LIMIT_GROWTH_FRACTION`).
- Cooldown persistente (`lastZenkaiTick`) para que no se pueda abusar reconectando.
- Evento `KiAspectsEvent.ZenkaiBoostEvent` (trae ganancia de Poder Real, si hubo
  crecimiento de Límite, y si fue extremo).
- `/dmzkiaspects zenkai [jugador]` muestra disparos totales, bonus permanente
  acumulado, cooldown y la racha de vida baja en curso.

Todas las constantes de balance de ambos sistemas están en `config/KiAspectsConfig`,
listas para ajustar sin tocar la lógica.

## Fase 6 — corrección del Límite Actual / Potencial Oculto

Esta fase reemplaza las vías de crecimiento del Límite Actual que había en la Fase 4
(nivel de DMZ, tiempo jugado pasivo) por las correctas, y separa claramente "convertir
potencial existente" de "hacer crecer el techo".

### Guru / Anciano Kaioshin / desbloqueos de potencial — SOLO convierten, nunca suben el Límite
- Se detectan automáticamente como una subida del Release Limit de DMZ
  (`Resources#getReleaseLimit()`, 0-100 — es el stat que sube el skill "Potential Unlock" de
  DMZ y que Guru/Anciano Kaioshin destraban en el diálogo). No hay un DMZEvent dedicado para
  esto, así que se detecta comparando contra el último valor visto en cada `recalculate()`
  (mismo patrón que ya usábamos para nivel en Fase 4).
- Cada punto de Release Limit ganado convierte `POTENTIAL_UNLOCK_FRACTION_PER_RELEASE_POINT`
  (1%) del Potencial Oculto restante en Poder Real permanente (`unlockedPotentialPower`,
  persistente, separado del bonus de Zenkai para que el comando pueda mostrarlos distinto).
  **Nunca toca el Límite Actual.**
- También hay API pública para desbloqueos que no pasen por Release Limit:
  `KiAspectsCapability.unlockPotential(player, fraccion, origen)`.
- Evento: `KiAspectsEvent.PotentialUnlockEvent`.
- Para evitar regalar de golpe el progreso que un personaje ya traía antes de instalar el
  addon, tanto esto como el viejo tracking de nivel usan un sentinel (-1 = "todavía no visto"):
  la primera observación solo fija la base, sin conversión retroactiva.

### Límite Actual — ahora sube lento y "ganado", por dos vías
1. **Presión de Adaptación** (`tickAdaptationPressure`, evaluada cada tick): se acumula
   mientras el jugador entrena en la zona de gravedad "Pushing Hard" u "Overloaded" de DMZ
   (`GravityLogic#getTrainingZone`, un stat que DMZ ya calcula solo a partir de gravedad +
   peso) **y** ya usa ≥85% de su Límite Actual (`ADAPTATION_MIN_USAGE_FRACTION`). Si no se
   cumplen las condiciones, decae mucho más despacio de lo que sube (no se resetea de golpe).
   Al llenarse (tarda ~1h de entrenamiento sostenido con los valores por defecto), el Límite
   sube `ADAPTATION_LIMIT_GROWTH_PERCENT` (4%) y la presión se reinicia.
2. **Maestría máxima de una forma** (`applyMasteryBreakthroughIfNeeded`, evaluada en cada
   `recalculate()`): cuando `Character#getFormMasteries().hasMaxMastery(...)` confirma que la
   forma activa llegó a su `FormData#getMaxMastery()`, el Límite sube `MASTERY_LIMIT_GROWTH_PERCENT`
   (5%) una única vez por forma (`masteryRewardsGranted`, persistente).
3. La ruptura de límite de la Fase 4 (`applyBreakthroughIfNeeded`) se mantiene, pero pasó a
   ser una **red de seguridad**, no una vía "oficial": solo actúa si el Poder Real recalculado
   desde los stats crudos de DMZ organicamente superó el Límite registrado.

Subir el Límite automáticamente genera nuevo Potencial Oculto: como `Potencial Oculto =
Límite Actual - Poder Real` y estas vías tocan el Límite sin tocar el Poder Real, el nuevo
espacio aparece solo. Al revés, un desbloqueo grande de Guru/Kaioshin reduce mucho el
Potencial Oculto disponible (sin llegar a 0 salvo que se convierta el 100%), tal como pedían.

**Retirado de la Fase 4**: el crecimiento por nivel de DMZ y por tiempo jugado pasivo. Ya no
son parte de la lógica "correcta" según esta fase, así que se sacaron del código en vez de
dejarlos como rutas muertas.

Comandos actualizados: `/dmzkiaspects potencial [jugador]` ahora muestra Presión de
Adaptación, el bonus de desbloqueos vs. el de Zenkai por separado, y cómo sube realmente el
Límite.

## Fase 7 — Integración final, HUD y comandos de admin

Detalle completo de cada cambio de interfaz (qué se agregó, dónde, y qué se dejó intacto de
DMZ): ver [`docs/FASE7_CAMBIOS_DE_INTERFAZ.md`](docs/FASE7_CAMBIOS_DE_INTERFAZ.md).

Última fase: cablea todo lo anterior a algo que se ve y se prueba en el juego, reusando la
UI de DMZ en vez de inventar una propia.

### Red: servidor -> cliente
Todos los cálculos viven en el servidor (capability). Para que el cliente pueda mostrar algo,
`network/KiAspectsNetwork` + `network/SyncKiAspectsS2C` mandan una foto de los 5 valores
principales + estado de Kaioken, 5 veces por segundo (`KiAspectsCapability#syncToClient`,
llamado desde `onPlayerTick` y al loguear). No hay paquetes cliente -> servidor: este addon
solo observa, nunca necesita que el cliente le diga nada.

### HUD — reutilizando la UI de DMZ, no reemplazándola
- **Panel integrado al menú de Stats (tecla V)**: `client/KiAspectsScreenIntegration` no solo
  dibuja sobre la `CharacterStatsScreen` real de DMZ (confirmé el nombre de clase decompilando
  el jar con CFR) — lee por reflection el sistema de escala interno `protected` de DMZ
  (`ScaledScreen`/`BaseMenuScreen`: ancho/alto virtuales, escala, offset de animación del panel
  derecho) y reproduce ese mismo transform, así nuestro panel queda pegado al panel derecho de
  DMZ, se mueve y escala exactamente con el resto del menú, y usa su misma textura de panel
  (`menusmall.png`). Si esa reflection falla en algún build de DMZ, cae solo a un panel tooltip
  vanilla de respaldo — nunca rompe nada. Detalle completo en
  [`docs/FASE7_CAMBIOS_DE_INTERFAZ.md`](docs/FASE7_CAMBIOS_DE_INTERFAZ.md). No se toca
  `CharacterStatsScreen` en ningún momento (ni mixin ni subclase): los botones, pestañas y
  controles originales de V siguen exactamente igual.
- **HUD compacto opcional** (`client/KiAspectsHudOverlay`, registrado como `IGuiOverlay` vía
  `RegisterGuiOverlaysEvent`): mismo contenido resumido, visible durante el juego normal (no
  solo en el menú). **Apagado por defecto** — DMZ ya tiene su propio HUD configurable
  (Alternative/Xenoverse) y este addon no se le impone. Se prende con una tecla nueva y sin
  bind por defecto (`key.dmzkiaspects.toggle_hud`, categoría "DMZ Ki Aspects" en el menú de
  Controles de Minecraft) — el jugador la asigna si la quiere. No pisa ninguna tecla de DMZ.
- **Nada de esto cancela ni intercepta eventos de DMZ** (repasé todo el proyecto: no hay un
  solo `setCanceled` en ningún handler), así que V, X y el resto de los controles/menús de DMZ
  funcionan exactamente igual que sin este addon instalado.

### Comandos de admin/debug (nuevo: `debug`)
- `/dmzkiaspects debug <jugador> recalcular` — fuerza un recálculo inmediato (sin esperar al
  próximo DMZEvent o al tick 100), para probar cambios al toque.
- `/dmzkiaspects debug <jugador> set kicombate|kicompleto|poderreal <valor>` — fuerza cualquiera
  de esos tres valores directamente, para QA. Puede autocorregirse parcialmente en el próximo
  recálculo (p. ej. la ruptura de límite de seguridad si forzás un Poder Real por encima del
  Límite) — es una herramienta de "probar ahora", no un override permanente.
- `/dmzkiaspects debug <jugador> set presencia <0-100>` — igual, como porcentaje.
- Sumado a lo que ya había: `potencial` (grant/grantpercent/set del Límite), `kaioken`,
  `zenkai` — entre todos cubren los 5 aspectos, Kaioken, Zenkai y Potencial Oculto completos.

### Limpieza y balance de esta fase
- Repasé todo el proyecto por consistencia de nombres, comentarios que quedaron obsoletos de
  fases anteriores, y balance de llaves archivo por archivo.
- No cambié ninguna constante de balance en esta fase (ya se ajustaron en las Fases 5/6); esta
  fase es integración, no rebalanceo.

### Riesgo a verificar (no pude compilar contra Forge real en este entorno)
La API de overlays de Forge 1.20.1 (`net.minecraftforge.client.gui.overlay.IGuiOverlay`/
`ForgeGui`, `net.minecraftforge.client.event.RegisterGuiOverlaysEvent`) tuvo una reescritura
durante el ciclo de 1.20.1. Usé la forma que mejor recuerdo como correcta para Forge 47.x, pero
a diferencia de las clases de DMZ (que pude decompilar y verificar con `javap` desde el jar que
me pasaste), no tengo el jar de Forge acá para confirmar esto de la misma manera. Si tu versión
exacta de Forge difiere, `client/ClientSetup.java` (registro) y `client/KiAspectsHudOverlay.java`
(la interfaz `IGuiOverlay`) son los dos archivos a ajustar — el resto del proyecto no depende de
esta API. `CharacterStatsScreen` y el resto de las clases de DMZ sí las verifiqué contra el jar.

## Limitaciones conocidas (final)
- No hay un DMZEvent dedicado para "Guru/Anciano Kaioshin desbloquearon tu potencial"; se
  infiere comparando `Resources#getReleaseLimit()` entre recálculos — es una heurística, no una
  garantía 1:1 con el diálogo de Guru/Kaioshin.
- El bonus de daño (expulsiones y Kaioken sobre ellas) se atribuye por jugador+tiempo, no por
  proyectil (dos técnicas dentro de 100 ticks se pisan). Atribución exacta = mixin sobre el proyectil.
- El multiplicador de Kaioken sobre combate normal (no-expulsión) es el que DMZ ya aplica nativamente
  a STR/SKP/PWR; este addon no lo puede escalar de nuevo sin mixin sobre `getMeleeDamage`/etc.
- El Poder Real usa los multiplicadores de config de cada forma, sin ajuste de maestría (DMZ solo lo calcula para la activa).
- Scouter/Ki Sense reales siguen leyendo el Battle Power completo hasta que exista el mixin.
- El Zenkai racial nativo de DMZ (Saiyan) sigue activo en paralelo y es independiente de este sistema.
- No pude compilar contra Forge en mi entorno: verifiqué con `javap` que cada método de DMZ usado existe con esa firma.
