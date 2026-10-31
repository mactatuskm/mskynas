# Notas del proyecto mskynas

- App personal de Android TV (se usa en Fire TV) que reemplaza a la app oficial de Plex.
  Servidor Plex del dueño: "mskynas" (Unraid). No se distribuye.
- Objetivos, en orden: muy ligera y rápida, bonita, funcional. Dueño: diseñador / director de arte.
  Comunicarse en español.
- Alcance: Continuar viendo, Bibliotecas + Colecciones, Watchlist, detalle, reproductor.
  Nada social ni contenido en línea de Plex.
- Stack: Kotlin, Compose for TV (androidx.tv:tv-material), Media3 ExoPlayer, OkHttp,
  kotlinx.serialization, Coil. Sin ViewModels: estado en Compose + `Cache` en memoria + `Store` en disco.
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
- Solo contenido del servidor: nada de tendencias externas ni Overseerr por ahora. La Watchlist de
  plex.tv sí se usa. Única excepción decidida por el dueño: la sección Calendar usa TMDB para listar
  próximos episodios de las series de sus bibliotecas y la fecha de estreno en digital (TMDB
  release_dates, tipo 4) de las películas de su Watchlist; se cruza por los guid `tmdb://` de Plex.
  País de las fechas: México, con Estados Unidos como respaldo. TMDB no se usa para metadatos, pósters ni el inicio.
- Fases acordadas: 1) lenguaje visual ✔ 2) detalle completo (reparto, "Más como este",
  versiones) 3) reproductor (saltar intro/créditos con marcadores de Plex, audio/subtítulos,
  versiones) 4) descubrimiento local (Porque viste…, Top 10, filas por tema).
- Autoactualización (`data/Updater.kt` + `ui/UpdateDialog.kt`): al abrir la app consulta
  `api.github.com/repos/mactatuskm/mskynas/releases/latest`; si `build-N` > `BuildConfig.VERSION_CODE`
  ofrece actualizar, descarga `mskynas.apk` al caché y abre el instalador con un FileProvider.
  Las notas que ve el usuario son el mensaje del commit: escribirlos en español, claros y para él.
  Requiere el permiso "Instalar apps desconocidas" para mskynas (se pide la primera vez).
- Perfiles (Plex Home): al abrir la app se muestra "¿Quién está viendo?" (`ui/ProfilesScreen.kt`).
  Se listan con `plex.tv/api/v2/home/users` usando el token de la cuenta principal
  (`Session.accountToken`); al elegir uno, `POST plex.tv/api/home/users/{id}/switch[?pin=]` da su
  token y con él `api/v2/resources` da su acceso al servidor. Si la cuenta tiene un solo perfil,
  pasa directo al inicio. El avatar del perfil aparece en la barra lateral para cambiarlo.
- Atrás en el inicio: el primero lleva el foco a la barra lateral; el siguiente pregunta
  "¿Salir de mskynas?" (`RailScaffold`).
- Rendimiento del inicio: `Store` (data/Store.kt) guarda en disco el último `HomeData` (por servidor
  y perfil) y la lista de perfiles; al abrir la app se muestran al instante y se refrescan en segundo
  plano. La Watchlist (plex.tv) se carga aparte y nunca bloquea el inicio. Coil usa caché en disco
  de 300 MB con `respectCacheHeaders(false)` (MainActivity). Peticiones a plex.tv con timeouts cortos.
- Se probó un inicio estilo Disney+ (carrusel + pestañas) y al dueño no le gustó: se revirtió.
  El inicio actual es el de la fase 1 (hero dinámico + filas). Él diseñará el inicio en Illustrator.
