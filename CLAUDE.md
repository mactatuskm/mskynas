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
  `#0A0A12`; (fase 1, ya reemplazada por la dirección Silo, ver abajo) barra lateral de íconos; hero dinámico
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
  pasa directo al inicio.
- Atrás en el inicio: el primero lleva el foco a la barra lateral; el siguiente pregunta
  "¿Salir de mskynas?" (`AppScaffold`, en `ui/TopBar.kt`). Si se está en una fila de abajo, Atrás primero regresa a la primera fila.
- Rendimiento del inicio: `Store` (data/Store.kt) guarda en disco el último `HomeData` (por servidor
  y perfil) y la lista de perfiles; al abrir la app se muestran al instante y se refrescan en segundo
  plano. La Watchlist (plex.tv) se carga aparte y nunca bloquea el inicio. Coil usa caché en disco
  de 300 MB con `respectCacheHeaders(false)` (MainActivity). Peticiones a plex.tv con timeouts cortos.
- Se probó un inicio estilo Disney+ (carrusel + pestañas) y al dueño no le gustó: se revirtió.
  El inicio actual es el de la fase 1 (hero dinámico + filas). Él diseñará el inicio en Illustrator.
- Estructura base de la app = artifact "Mapa de pantallas mskynas" (decisión del dueño):
  Selección de perfil → Home. Desde Home: Search (películas y series del servidor), Movies (Recommended, Browse, Collections), Series
  (Recommended, Browse, Collections), Calendar (próximos estrenos: episodios de sus series y estreno
  digital de películas de su Watchlist, vía TMDB). La sección For you se descartó.
  Menú del perfil (al elegir el avatar; referencia: menú de perfil de otra app de Plex con encabezado
  avatar + nombre + "ADMIN · servidor"): Switch Profile, Watchlist, Lists, History (historial de lo
  visto), Settings, Switch Server, Sign Out. Desde cualquier póster: Detalle → Reproductor
  (diseño por confirmar).
- Implementación del mapa: barra lateral = avatar (abre `ui/ProfileMenu.kt`), Search (`ui/SearchScreen.kt`,
  `/hubs/search`), Home, Movies, Series, Calendar. Movies/Series = `SectionScreen` (`ui/SectionScreens.kt`, ruta `section/{movie|show}`) con
  pestañas Recommended (filas solo del servidor: continuar, recientes, mejor calificadas, por ver),
  Browse (cuadrícula de la biblioteca) y Collections; si hay varias bibliotecas del tipo, se elige con
  botones. Menú del perfil: Switch Profile, Watchlist, Lists (playlists de video), History
  (`/status/sessions/history/all`, accountID 1 para la cuenta principal), Settings (Servidor, Acerca
  de), Switch Server, Sign Out (con confirmación). Etiquetas en inglés como en el mapa.
  Calendar: `data/Tmdb.kt` (token de lectura de TMDB desde el secreto de GitHub `TMDB_TOKEN` →
  `BuildConfig.TMDB_TOKEN`; NUNCA escribir el token en el repo, que es público). Series: guid
  `tmdb://` con `includeGuids=1` → `/tv/{id}` next_episode_to_air. Watchlist: `/movie/{id}/release_dates`
  tipo 4 (MX, luego US); si falta el guid, búsqueda por título y año. Agrupado por día.
  El diseño visual de todas estas pantallas es provisional hasta que el dueño entregue sus diseños.
- Rediseño estilo Silo (pedido del dueño, nov 2026; referencia: capturas de la app Silo): barra superior
  (`ui/TopBar.kt`: logo provisional a la izquierda; búsqueda, Home, Movies, Series, Calendar al centro;
  avatar a la derecha) en lugar de la barra lateral. Fondo neutro `#0D0D10` teñido con el color del arte
  (`rememberTint` en `ui/Hero.kt`). Hero: logo, clasificación con contorno, calificación (IMDb/RT/TMDB),
  datos, sinopsis, reparto e insignias de calidad (4K, DOLBY VISION, ATMOS). Medidas = capturas a 1080p ÷ 2.
  "For You" de la referencia no se incluyó (el dueño la había descartado).
- Home = las mismas filas del Home de Plex, en su orden, incluidas las colecciones de Kometa promovidas
  al inicio (`Plex.homeHubs`: `/hubs/promoted` con respaldo `/hubs`). Continuar viendo en tarjetas
  horizontales; si Plex no lo trae, se agrega con On Deck. Se guarda en `HomeData.hubs` (Store).
- Detalle: línea VERSION / AUDIO / SUBTITLES, píldora blanca "Resume S1:E1", botones redondos
  (reiniciar, audio, subtítulos con `Plex.selectStreams`, marcar visto con `Plex.setWatched`), temporadas
  en píldoras y episodios "S01E01 · Título". PlayerActivity usa como idioma preferido las pistas elegidas.
- Browse: Sort (fecha agregada, título, año, calificación), Filter (sin ver), Shuffle y salto A–Z
  (solo con orden por título). Ordenar y filtrar se hace en la TV, sin pedir de nuevo el catálogo.
- Rendimiento en Fire TV (prioridad del dueño: desplazamiento tan suave como Plex):
  release con R8 (`isMinifyEnabled`, reglas en `app/proguard-rules.pro`: conservar `data/**`),
  `profileinstaller` + `app/src/main/baseline-prof.txt` (precompila la app aunque se instale por fuera).
  Desplazamiento por foco con `LocalBringIntoViewSpec` global (`PivotScroll` en `ui/Theme.kt`: el
  enfocado queda a `ContentStart` del borde, resorte sin rebote); el detalle usa `MinimalScrolling`
  en su lista y `PivotScrolling` en sus filas. No usar `animateScrollToItem` al enfocar.
  El hero del Home lee el foco desde `HeroState` (las filas no se recomponen al moverse), el fondo
  cambia hasta que la imagen ya se descargó (`prefetchImage`), el tinte se lee al dibujar (`State`).
  JSON del servidor se decodifica en `Dispatchers.Default`; el Home guardado se lee en IO.
  Tarjetas sin sombra (glow) al enfocar: solo borde blanco + escala.
