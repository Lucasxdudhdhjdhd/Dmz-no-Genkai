# Fase 7 — Todo lo que se tocó de interfaz

Documento de referencia rápida: qué se agregó, dónde vive, y qué NO se tocó de la UI
original de DragonMineZ. Nada de esto usa mixins.

## 1. Panel integrado de verdad al menú de Stats (tecla V)

**Archivo:** `client/KiAspectsScreenIntegration.java`
**Mecanismo:** `ScreenEvent.Render.Post`, filtrando `event.getScreen() instanceof CharacterStatsScreen`
(nombre de clase real de DMZ, confirmado decompilando el jar con CFR).

Esto cambió de diseño a mitad de la fase: la primera versión dibujaba texto en coordenadas de
pantalla fijas (quedaba como texto flotante estilo F3, no como parte del menú). La versión
final en cambio:

- **Lee por reflection** los métodos `protected` que DMZ usa internamente para su propio
  sistema de escala de UI (`ScaledScreen#getUiWidth/getUiHeight/getUiScale`,
  `BaseMenuScreen#getRightPanelSwitchOffset`) — son protegidos y no extendemos esa clase, así
  que no hay forma de llamarlos directo sin mixin. Se cachean una sola vez.
- Con esos valores, reproduce el mismo `PoseStack.pushPose()+scale(uiScale)+popPose()` que usa
  DMZ, y dibuja en las mismas coordenadas "virtuales" que usa el resto del menú — no en píxeles
  de pantalla fijos. Resultado: nuestro panel se mueve, escala y desliza exactamente igual que
  el resto de la pantalla (zoom del menú, animación de cambio de panel, etc.).
- **Reutiliza la textura real de DMZ** (`textures/gui/menu/menusmall.png`, el mismo panel chico
  de 145x58 que DMZ usa para su propio panel superior de info del jugador) en vez de un fondo
  nuestro inventado.
- **Posición:** pegado justo debajo del panel derecho de Estadísticas de DMZ (141x213, arranca
  en `uiWidth-158, centerY-105`), con el mismo offset de animación — así entra y sale junto con
  ese panel al cambiar de pestaña.
- Contenido: título, Presencia + Poder Real, Límite Actual + Potencial Oculto, y una cuarta
  línea que alterna entre Kaioken (si está activo, en rojo `§c`) o Ki de Combate/Completo.
- **Red de seguridad:** si la reflection falla (por ejemplo, un update de DMZ renombra esos
  métodos), se detecta una sola vez y a partir de ahí se usa automáticamente un *fallback*: una
  caja de tooltip vanilla de Minecraft (`GuiGraphics#renderTooltip`) en coordenadas de pantalla
  normales. Nunca crashea el juego por esto, en el peor caso vuelve a verse como un panel
  flotante prolijo en vez de integrado.
- **No se instancia, extiende, ni modifica `CharacterStatsScreen`** en ningún momento — ni
  siquiera con la reflection, que solo *lee* valores, nunca escribe ni intercepta nada de DMZ.
  No se tocan sus botones, pestañas (Party/Skills/Quests/Minigames/Config) ni su textura de
  fondo.

## 2. HUD compacto opcional durante el juego (fuera de menús)

**Archivos:** `client/KiAspectsHudOverlay.java` (el overlay), `client/ClientSetup.java`
(lo registra), `client/KiAspectsKeybinds.java` + `client/ClientTickHandler.java` (la tecla
que lo prende/apaga).

- **Apagado por defecto** (`KiAspectsHudOverlay.enabled = false`). DMZ ya tiene su propio HUD
  configurable (Alternative HUD / Xenoverse HUD, elegibles desde el menú de Config de DMZ);
  este addon no se superpone a esa elección a menos que el jugador lo pida explícitamente.
- Se registra como un `IGuiOverlay` nuevo vía `RegisterGuiOverlaysEvent#registerAboveAll`
  ("ki_aspects_hud") — es una capa de HUD adicional, no un reemplazo de ninguna existente.
- Posición fija: esquina superior izquierda, `x=4, y=4`, texto con sombra.
- Mismo contenido que el panel de la pantalla de Stats, más compacto (Presencia, Poder Real,
  Límite Actual, Potencial Oculto + % usado, y una línea de Kaioken si está activo, coloreada
  según el peligro: naranja `0xFFAA00` si es moderado, rojo `0xFF5555` si es alto).
- Se prende/apaga con una tecla **nueva, sin bind por defecto**
  (`key.dmzkiaspects.toggle_hud`, categoría **"DMZ Ki Aspects"** en el menú de Controles de
  Minecraft). El jugador tiene que asignarla a mano si la quiere — no ocupa ninguna tecla que
  ya use DMZ ni vanilla.
- Textos del nombre de la tecla y su categoría, en `assets/dmzkiaspects/lang/en_us.json` y
  `es_es.json`.

## 3. Comandos de chat nuevos en esta fase

Ya existían comandos de fases anteriores (`/dmzkiaspects`, `potencial`, `kaioken`, `zenkai`).
Fase 7 agrega:
- `/dmzkiaspects debug <jugador> recalcular`
- `/dmzkiaspects debug <jugador> set kicombate|kicompleto|poderreal <valor>`
- `/dmzkiaspects debug <jugador> set presencia <0-100>`

Todos requieren permiso de operador (nivel 2). No reemplazan ni ocultan ningún comando de DMZ.

## 4. Red (no es UI, pero es lo que permite mostrar algo en el cliente)

**Archivos:** `network/KiAspectsNetwork.java`, `network/SyncKiAspectsS2C.java`.
Paquete servidor→cliente, 5 veces por segundo, con los 5 valores + estado de Kaioken. No hay
paquetes cliente→servidor: el addon nunca necesita que el jugador le mande nada.

## 5. Qué NO se tocó (verificado explícitamente)

- **Ningún `DMZEvent` se cancela** (`event.setCanceled(true)`) en ningún archivo del proyecto
  — repasado línea por línea. V, X y todo el resto de los menús, animaciones y controles de
  DMZ se comportan exactamente igual que sin este addon.
- **Ninguna clase de DMZ fue extendida, mixineada, ni sobreescrita.** Toda la integración
  visual es aditiva: overlays que dibujan encima, nunca reemplazos.
- **Ninguna tecla de DMZ fue reasignada ni interceptada.** La única tecla nueva
  (`toggle_hud`) nace sin bind.
- El HUD normal de DMZ (Alternative/Xenoverse) sigue siendo el que el jugador eligió en su
  config; este addon no lo cambia ni lo fuerza a otro.

## 6. Riesgo pendiente de verificar en el juego real

La API de overlays de Forge (`net.minecraftforge.client.gui.overlay.IGuiOverlay` /
`RegisterGuiOverlaysEvent`) tuvo cambios de forma durante 1.20.1. A diferencia de todo lo que
toca DMZ (verificado método por método con `javap` contra tu jar), esto no lo pude compilar
contra Forge real en este entorno. Si tu build de Forge 47.x difiere, `ClientSetup.java` y
`KiAspectsHudOverlay.java` son los únicos dos archivos que dependen de esa API específica.
