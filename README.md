# mskynas

Cliente ligero de Plex para las Fire TV de la casa. Solo lo esencial:
**Continuar viendo**, **Bibliotecas** (con sus **Colecciones**) y **Watchlist**.
Sin reseñas, amigos ni contenido en línea de Plex.

## Instalar o actualizar en una Fire TV

La versión más reciente siempre está en:

```
https://github.com/mactatuskm/mskynas/releases/latest/download/mskynas.apk
```

1. En la TV, abre **Downloader**.
2. Escribe esa URL, o el código numérico de aftv.news que apunte a ella.
3. Presiona **Instalar**. Se instala encima de la versión anterior, sin perder la sesión.

Primera vez en una TV: activa *Ajustes → Mi Fire TV → Opciones de desarrollador →
Instalar apps desconocidas → Downloader*.

## Cómo se actualiza

Cada cambio que llega a la rama `main` dispara una compilación automática en GitHub
(pestaña **Actions**). Al terminar, se publica una nueva versión en **Releases** con el
número `1.0.N`. No hace falta compilar en ninguna computadora.

## Estructura

```
app/src/main/java/com/mskynas/tv/
├── MainActivity.kt        Punto de entrada
├── data/
│   ├── Plex.kt            Cliente de la API de Plex (inicio de sesión, bibliotecas, URLs de video)
│   ├── Models.kt          Modelos de datos y ayudantes de presentación
│   ├── Session.kt         Cuenta y servidor guardados en el dispositivo
│   └── Cache.kt           Caché en memoria para volver a pantallas al instante
└── ui/
    ├── Theme.kt           Colores de la app
    ├── Components.kt      Tarjetas, botones, filas y utilidades de foco
    ├── Nav.kt             Rutas entre pantallas
    ├── LoginScreen.kt     Código para plex.tv/link
    ├── ServerScreen.kt    Elegir servidor
    ├── HomeScreen.kt      Inicio
    ├── LibraryScreen.kt   Biblioteca, colecciones y cuadrícula de pósters
    ├── DetailScreen.kt    Detalle de película o serie
    └── PlayerScreen.kt    Reproductor
```

## Compilar en Android Studio (opcional)

Solo hace falta para probar cambios al momento: *File → Open* la carpeta del repositorio
y presiona ▶ con la Fire TV conectada por ADB.

## Firma

`keystore/mskynas.jks` es la llave con la que se firman todas las versiones, para que cada
una se instale encima de la anterior. Es una app casera que no se distribuye, por eso la
llave vive en el repositorio. No la cambies o habrá que desinstalar la app en cada TV.
