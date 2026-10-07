# Publicar en Steam

Qué falta para subir el juego a Steam y cómo encaja con lo que ya hay. En el
MVP no hay nada de Steam dentro del juego (`NoOpPlatformServices`); esta guía
es el plan para cuando toque.

## 1. Lo que ya está preparado

- **Paquetes con su propio Java** (fase 7): `./gradlew :lwjgl3:packageWinX64`
  (y `LinuxX64`, `MacM1`, `MacX64`) deja en `build/dist/` un zip por sistema
  con el juego y un Java 21 recortado. Steam sube esas carpetas tal cual: el
  jugador no necesita instalar Java.
- **Guardado compatible con Auto-Cloud**: la partida vive en la carpeta de
  usuario del sistema, no junto al juego, y se escribe de forma atómica con
  copia `.bak` (ARCHITECTURE §7). Steam la puede sincronizar sin tocar código.
- **Un único punto de entrada para Steam**: todo lo de la tienda pasa por
  `PlatformServices` (módulo `platform`). Hoy solo tiene
  `unlockAchievement(id)`.

## 2. Cuenta y aplicación

1. Cuenta de Steamworks (Steam Direct): pago por aplicación, datos fiscales y
   bancarios. Steam da un **App ID**.
2. En Steamworks, crear un **depot por sistema**: Windows (el principal),
   Linux y macOS si se quieren vender ahí.
3. **Opciones de lanzamiento** (una por depot):

   | Sistema | Ejecutable | Carpeta |
   |---|---|---|
   | Windows | `java-wave-defense.exe` | raíz del depot |
   | Linux | `java-wave-defense` | raíz del depot |
   | macOS | `Java Wave Defense.app` | raíz del depot |

## 3. Steam Cloud (Auto-Cloud, sin código)

En *Steam Cloud* → *Auto-Cloud*, una regla por sistema. Se sincronizan la
partida y los ajustes; los logs y las copias rotas (`.corrupt-*`) no.

| Sistema | Raíz | Subcarpeta | Patrón |
|---|---|---|---|
| Windows | `WinAppDataRoaming` | `JavaWaveDefense` | `save.json*` y `settings.json*` |
| Linux | `LinuxXdgDataHome` | `java-wave-defense` | `save.json*` y `settings.json*` |
| macOS | `MacAppSupport` | `JavaWaveDefense` | `save.json*` y `settings.json*` |

Con *Root overrides*, las tres rutas pueden compartir la misma partida entre
sistemas. Tamaño: la partida ocupa unos pocos KB; un cupo de 1 MB y 10
ficheros sobra.

## 4. Steamworks dentro del juego (logros)

Cuando haga falta algo más que la nube:

1. Biblioteca: **steamworks4j** (`com.code-disaster.steamworks4j`), la que usan
   los juegos de libGDX. Lleva sus nativos para los tres sistemas.
2. Un módulo nuevo, `platform-steam`, con `SteamPlatformServices implements
   PlatformServices`. Solo lo usa `lwjgl3`; `game` y `core-sim` no se enteran.
3. Al arrancar: `SteamAPI.init()`. Si falla (Steam cerrado, o el juego no se
   lanzó desde Steam), se sigue con `NoOpPlatformServices`: el juego nunca
   depende de Steam para funcionar.
4. Cada frame hay que llamar a `SteamAPI.runCallbacks()`: añadir a
   `PlatformServices` un método `update()` (vacío en el NoOp) y llamarlo desde
   `WaveDefenseGame.render`.
5. En desarrollo, un `steam_appid.txt` con el App ID junto al ejecutable. No
   se sube al depot.
6. Logros propuestos (los decide Yeray): superar la oleada 1, primera oleada
   sin fugas, primer reto cumplido, las 3 estrellas de una oleada, derrotar al
   Jefe 1 y las 30 estrellas. Se disparan desde lo que ya calcula `WaveOutcome`
   (estrellas nuevas, trofeo).

## 5. Subir una versión (SteamPipe)

1. Descargar los zips de Actions (*Package*, ver README) o generarlos en
   local, y descomprimir cada uno en su carpeta: `content/windows`,
   `content/linux`, `content/macos`.
2. Un `app_build_<AppID>.vdf` con un depot por carpeta y subir con
   `steamcmd +login <usuario> +run_app_build ../scripts/app_build_<AppID>.vdf +quit`.
3. En Steamworks, poner la build en la rama `beta` para probarla y luego en
   `default`.

## 6. Antes de publicar

- **Firmas**: el `.exe` de Windows no está firmado; SmartScreen avisa si se
  descarga fuera de Steam (desde Steam no). La app de macOS no está firmada ni
  notarizada: para venderla en Mac hace falta una cuenta de Apple Developer.
- **Icono**: el `.exe` y la `.app` llevan el icono por defecto. Construo admite
  `icon` (Windows, `.ico`) y `macIcon` (`.icns`) cuando haya uno.
- **Steam Deck**: el juego se programa con teclado; sin teclado físico el
  editor es incómodo. Marcarlo como «no compatible» o probar con el teclado en
  pantalla antes de pedir la verificación.
- Página de la tienda: cápsulas, capturas, tráiler, descripción, edad y
  precio. No dependen del código.
