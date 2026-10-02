# Cambios locales respecto al upstream (jean-voila/FeurStagram)

Branch local = fork Juan-Martin-Cerezo/antiscroll-ig-patcher (publico).
Base: upstream v446 (IG 446.0.0.49.77).

## Añadidos (anti-doomscroll propio)
1. `patches/src/main/kotlin/com/feurstagram/patches/ui/ClipsViewerNoSwipePatch.kt`
   (NUEVO) — Plan B del plan: corta el swipe entre reels.
   - Fingerprint doble: `androidx.viewpager.widget.ViewPager#onInterceptTouchEvent`
     y `#onTouchEvent` (el visor usa el ViewPager LEGACY, sin API
     setUserInputEnabled).
   - Inyección al inicio de ambos métodos (con `addInstructionsWithLabels`):
     `invoke-static {p0}, Lcom/feurstagram/extension/SwipeGuard;->shouldBlock(
     Landroid/view/View;)Z` → si true, `return false` (el pager no intercepta
     ni consume; el gesto muere y el reel no cambia).
   - Debe aplicarse SOLO al visor de reels: el resto de ViewPagers de IG
     (main tabs, stories, grids) NO son tocados porque la decisión vive en la
     extensión, que compara el resource-entry-name del pager.
2. `extensions/extension/src/main/java/com/feurstagram/extension/SwipeGuard.java`
   (NUEVO) — la clase de decisión: lista de resource entry names
   (`clips_viewer_view_pager`, `clips_swipe_refresh_container`), todo
   reflexivo (la extensión no depende de tipos androidx).
3. `extensions/extension/src/main/java/com/feurstagram/extension/Config.java`
   (MODIFICADO) — toggle nuevo `block_clip_swipe` (default true) via
   `isClipsSwipeBlocked()`; lockable con el hardcore lock (empieza con los
   demás block_* keys).
4. `.gitignore` — ignora `*.xapk` (la base de IG no se sube al fork público)
   y `keystore.env` (material de firma local).

## Por qué se movió la decisión al runtime-view
El visor de reels NO es una clase obfuscada de IG: es el androidx ViewPager
legacy. Parchear la clase androidx universal seria DEMASIADO (alcanzaria
todos los pagers del app), así que el bytecode añade solo el IF, y el
comparador del id vive en la extension (com.feurstagram), donde también hace
fácil refrescar los nombres de recurso si IG cambia (ids actuales del visor:
clips_viewer_view_pager y clips_swipe_refresh_container, verificados con
uiautomator dump; atados en SwipeGuard.java).

## TRAMPA de dexlib3/art conocida (la que costó el crash del perfil)
El `invoke-static` (35c) DIRECCIONA solo registros v0..v15. En
`onInterceptTouchEvent` el ViewPager tiene 20 registros → p0 = v18 y el
assembler DROPEA el invoke de forma silenciosa; queda `move-result` sin
fuente y ART lo tira como VerifyError en TODO subclass del ViewPager al
inflar (el perfil = NestableViewPager → crash). FIX: copiar p0 a un
registro chico antes del invoke:
`move-object/16 v1, p0` + `invoke-static {v1}, ...shouldBlock`.
AL NOMINAR una modificación bytecode SEMEJANTE: verificar SIEMPRE el dex del
output ANTES de instalar (dexdump -d classes.dex + grep del invoke), el
builder de dexlib3 no avisa. La verificación salvó esta trampa dos veces.

## Cómo verificar un build local
    . ./keystore.env && ./build.sh instagram-<version>.xapk --clone
    # en build/patch-report.json deben salir OK los 12 patches:
    # Clips viewer no-swipe, Clone, Feed item filtering, Force SDR display,
    # Install-packages permission, Limit feed to following profiles,
    # Network content blocking, Popup hiding, Restart relay,
    # Settings entry point, Signature check bypass, Debug bridge (sólo con --debug)
