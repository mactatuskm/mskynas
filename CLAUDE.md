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
