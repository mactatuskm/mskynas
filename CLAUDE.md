# Notas del proyecto mskynas

- App personal de Android TV (se usa en Fire TV) que reemplaza a la app oficial de Plex.
  Servidor Plex del dueño: "mskynas" (Unraid). No se distribuye.
- Objetivos, en orden: muy ligera y rápida, bonita, funcional. Dueño: diseñador / director de arte.
  Comunicarse en español.
- Alcance: Continuar viendo, Bibliotecas + Colecciones, Watchlist, detalle, reproductor.
  Nada social ni contenido en línea de Plex.
- Stack: Kotlin, Compose for TV (androidx.tv:tv-material), Media3 ExoPlayer, OkHttp,
  kotlinx.serialization, Coil. Sin ViewModels: estado en Compose + `Cache` en memoria.
- Reproducción: Direct Play primero; si ExoPlayer falla, transcodificación HLS del servidor
  (`Plex.hlsUrl`). El progreso se reporta con `/:/timeline` y `/:/scrobble`.
- Compilación: GitHub Actions (`.github/workflows/build.yml`) compila cada push a `main`
  y publica `mskynas.apk` en Releases. Revisar ahí los errores de compilación.
- La llave `keystore/mskynas.jks` es fija a propósito; no regenerarla.
- `minSdk 26` (Fire OS 7+). Los Fire TV con Vega OS no instalan APKs.
- Todas las TVs son Fire TV. Su launcher ignora `android:banner` en apps instaladas por fuera
  de la tienda: usa el ícono de la Activity principal. Por eso MainActivity declara
  `android:icon="@drawable/banner"` (PNG 16:9 en drawable-xhdpi/xxhdpi/xxxhdpi). Nada de
  íconos vectoriales adaptativos: en Fire TV pueden verse como un disco vacío.
- El reproductor es una Activity nativa (`player/PlayerActivity`), no Compose: dentro de
  Compose el SurfaceView quedaba tapado (audio sin imagen).
- Dirección visual (fase 1, referencia: Zerk Play para Emby): acento morado `#7B61FF`, fondo
  `#0A0A12`; barra lateral de íconos que se expande al enfocarla (`ui/Rail.kt`); hero dinámico
  arriba que cambia con el foco (`ui/Hero.kt`: fondo, logo "clearLogo" o título, ★, datos,
  progreso, sinopsis); tarjetas con borde blanco + brillo morado e insignias (visto / sin ver).
  Paleta y medidas en `ui/Theme.kt`. Íconos dibujados a mano en `ui/Icons.kt` (sin la librería
  de íconos completa, para no inflar el APK).
- Solo contenido del servidor: nada de TMDB, tendencias externas ni Overseerr por ahora
  (decisión del dueño; se puede revisar después). La Watchlist de plex.tv sí se usa.
- Fases acordadas: 1) lenguaje visual ✔ 2) detalle completo (reparto, "Más como este",
  versiones) 3) reproductor (saltar intro/créditos con marcadores de Plex, audio/subtítulos,
  versiones) 4) descubrimiento local (Porque viste…, Top 10, filas por tema).
- Autoactualización (`data/Updater.kt` + `ui/UpdateDialog.kt`): al abrir la app consulta
  `api.github.com/repos/mactatuskm/mskynas/releases/latest`; si `build-N` > `BuildConfig.VERSION_CODE`
  ofrece actualizar, descarga `mskynas.apk` al caché y abre el instalador con un FileProvider.
  Las notas que ve el usuario son el mensaje del commit: escribirlos en español, claros y para él.
  Requiere el permiso "Instalar apps desconocidas" para mskynas (se pide la primera vez).
