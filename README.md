# Alerta Wear

Proyecto académico para la materia **Desarrollo de Dispositivos Inteligentes**.

**Alerta Wear** es un sistema de asistencia y aviso temprano compuesto por una aplicación para reloj inteligente **Wear OS** (`:wear`) y una aplicación compañera para **Android móvil** (`:app`). Permite iniciar una solicitud SOS desde el reloj (o desde el propio teléfono) con una cuenta regresiva cancelable de 5 segundos; al concluir, el teléfono intenta obtener su ubicación actual y envía un **SMS de prueba** al contacto de confianza configurado localmente.

> **Aviso importante:** Este proyecto es un prototipo escolar con fines demostrativos y educativos. **No es un sistema de emergencias certificado para uso crítico en la vida real.** Todos los mensajes enviados por la aplicación incluyen explícitamente la leyenda `"Alerta de prueba de Alerta Wear"`.

---

## 1. Funciones actuales

### Aplicación de reloj Wear OS (`:wear`)
- **Detección de conexión real:** Verifica mediante **Wear OS Data Layer** (`NodeClient`) si existe un teléfono Android vinculado y cercano (`● Conectado` o `● Sin conexión`).
- **Botón SOS con pulsación prolongada:** Evita activaciones accidentales exigiendo mantener presionado el botón central para iniciar la alerta.
- **Cuenta regresiva cancelable (5 segundos):** Permite abortar el aviso en cualquier momento antes de transmitir la solicitud al teléfono.
- **Transmisión y reintento sin duplicados:** Envía la solicitud SOS (`MessageClient`) con un identificador único (`requestId`). Si no hay conexión al terminar la cuenta regresiva, muestra la pantalla **Sin conexión** y permite reintentar conservando el mismo `requestId`.
- **Seguimiento de estado en tiempo real y límite de espera (*timeout*):** Recibe confirmaciones (*ACK*) desde el teléfono para reflejar cada etapa (*Solicitud recibida*, *Enviando SMS con/sin ubicación*, *SMS enviado por el dispositivo*, *SMS entregado* o errores específicos). Si el teléfono no responde o se pierde la confirmación, tras un tiempo límite el reloj muestra **Sin confirmación ("No se pudo confirmar el resultado")** sin afirmar que el SMS se envió ni que falló.

### Aplicación móvil Android (`:app`)
- **Gestión local del contacto de confianza:** Permite guardar y actualizar nombre y número telefónico en almacenamiento local (`SharedPreferences`).
- **Recepción de solicitudes SOS:** Escucha mensajes provenientes del reloj mediante `PhoneWearableListenerService` (`WearableListenerService`), tanto con la aplicación abierta como mediante notificación de alta prioridad si la interfaz no está en primer plano.
- **Obtención de ubicación:** Consulta `FusedLocationProviderClient` (última ubicación conocida reciente o lectura rápida de alta precisión con límite de tiempo) y construye un enlace directo a Google Maps (`https://maps.google.com/?q=LATITUD,LONGITUD`). Si el permiso fue denegado o el GPS está desactivado, continúa con el envío del SMS indicando claramente que la ubicación no está disponible.
- **Envío de SMS y distinción estricta de estados:** Utiliza `SmsManager` junto con `PendingIntent`s (`ACTION_SMS_SENT` y `ACTION_SMS_DELIVERED`) procesados por `SmsStatusBroadcastReceiver`:
  - Nunca marca un SMS como enviado solo por haber iniciado el proceso.
  - Distingue **Enviando SMS** (`SENDING_SMS`), **Enviado por el dispositivo (entrega sin confirmar)** (`SENT_BY_DEVICE`) y **Entregado** (`DELIVERED`, solo si la red celular devuelve confirmación de entrega).
- **Prevención de envíos duplicados:** Deduplica solicitudes por `requestId` para que un reintento desde el reloj no dispare múltiples SMS para la misma alerta.
- **Historial local de eventos:** Conserva la bitácora local de alertas preparadas, enviadas, canceladas o con error.

---

## 2. Tecnologías, módulos y requisitos del proyecto

### Estructura de módulos
- **`:app`** — Aplicación móvil Android (`com.example.alertawear`).
- **`:wear`** — Aplicación para reloj Wear OS (`com.example.alertawear`).
  - *Nota:* Ambos módulos comparten el mismo `applicationId` (`com.example.alertawear`) para que **Wear OS Data Layer** enrute mensajes entre el reloj y el teléfono.

### Stack tecnológico y versiones
| Componente | Versión / Configuración |
| :--- | :--- |
| **Lenguaje** | Kotlin `2.2.10` (JVM Target `11`) |
| **Android Gradle Plugin (AGP)** | `9.3.3` |
| **Gradle Wrapper** | `9.5.0` |
| **SDK de compilación (`compileSdk`)** | `37` |
| **SDK objetivo (`targetSdk`)** | `37` (`:app`) / `35` (`:wear`) |
| **SDK mínimo (`minSdk`)** | `26` (Android 8.0 en `:app`) / `30` (Wear OS 3.0 en `:wear`) |
| **UI Móvil** | Jetpack Compose (BOM `2025.02.00`) + Material 3 |
| **UI Reloj** | Compose for Wear OS (`androidx.wear.compose:compose-material:1.4.1`) |
| **Comunicación Reloj ↔ Teléfono** | Google Play Services Wearable (`play-services-wearable:19.0.0`) |
| **Ubicación en Teléfono** | Google Play Services Location (`play-services-location:21.3.0`) |
| **Envío de SMS** | Android Telephony `SmsManager` + `BroadcastReceiver` |

### Requisitos del entorno de desarrollo
- **Android Studio** (versión reciente compatible con AGP 9.x) con **JDK 17 o superior** (puedes usar el JBR incluido en Android Studio).
- **Android SDK Platform 37** instalado desde *Tools > SDK Manager*.
- Para pruebas con emulador de reloj: imagen de sistema **Wear OS Round** con Google Play / Google APIs.
- Para pruebas de envío real de SMS: un **teléfono Android físico con tarjeta SIM activa** y señal móvil (los emuladores no envían SMS reales a la red celular).

---

## 3. Cómo abrir y compilar el proyecto en Android Studio

1. Abre **Android Studio** y selecciona **Open**.
2. Elige la carpeta raíz del repositorio (`ALERTAWEAR`).
3. Espera a que termine la sincronización de **Gradle** (*Gradle Sync*).
4. Para compilar ambos módulos desde el menú de Android Studio, selecciona **Build > Make Project**, o bien ejecuta en la terminal integrada (PowerShell / CMD en Windows):

```powershell
.\gradlew.bat :app:assembleDebug :wear:assembleDebug
```

---

## 4. Dónde se generan los APK de depuración

Al compilar en modo `debug`, los archivos instalables se generan en las siguientes rutas relativas a la raíz del proyecto:

- **APK Móvil (`:app`):**
  `app\build\outputs\apk\debug\app-debug.apk`
- **APK Wear OS (`:wear`):**
  `wear\build\outputs\apk\debug\wear-debug.apk`

---

## 5. Cómo ejecutar los módulos móvil (`:app`) y Wear OS (`:wear`)

El proyecto incluye configuraciones de ejecución para ambos módulos:

1. **Ejecutar la app móvil (`:app`):**
   - En la barra superior de Android Studio, selecciona la configuración **`app`**.
   - En el selector de dispositivos, elige tu **teléfono Android** (físico o emulador de teléfono).
   - Presiona **Run (▶)**.
2. **Ejecutar la app del reloj (`:wear`):**
   - En la barra superior de Android Studio, cambia la configuración de ejecución a **`wear`**.
   - En el selector de dispositivos, elige el **reloj Wear OS** (por ejemplo, `Wear OS XL Round`).
   - Presiona **Run (▶)**.

> **Importante:** No instales la configuración `app` dentro del emulador del reloj, ya que ambos comparten `applicationId` y reemplazarías la interfaz circular de Wear OS por la interfaz de teléfono. Si esto ocurre, vuelve a ejecutar la configuración `wear` apuntando al reloj.

También puedes instalar ambos APK directamente por consola mediante `adb`:

```powershell
# Instalar en el teléfono (reemplaza <SERIAL_TELEFONO> por el ID de adb devices)
adb -s <SERIAL_TELEFONO> install -r app\build\outputs\apk\debug\app-debug.apk

# Instalar en el reloj (reemplaza <SERIAL_RELOJ> por el ID de adb devices, ej. emulator-5554)
adb -s <SERIAL_RELOJ> install -r wear\build\outputs\apk\debug\wear-debug.apk
```

---

## 6. Cómo vincular los dispositivos (Reloj y Teléfono)

Para que Wear OS Data Layer transmita las solicitudes SOS entre `:wear` y `:app`, ambos dispositivos deben estar emparejados:

### Opción A: Teléfono físico + Emulador Wear OS (o dos emuladores) desde Android Studio
1. Abre **Device Manager** en Android Studio y asegúrate de que tanto el teléfono como el emulador de Wear OS aparezcan conectados.
2. Haz clic en el menú de tres puntos (`⋮`) junto al dispositivo en **Device Manager** y selecciona **Pair Wearable**.
3. Sigue el asistente de **Wear OS emulator pairing assistant** hasta que muestre el estado conectado.
4. *(Nota dependiente del entorno):* Si conectas un teléfono físico por primera vez para emparejarlo con un emulador de reloj, asegúrate de tener instalada la app **Wear OS by Google** en el teléfono si el asistente de Android Studio lo solicita.

### Comprobación rápida de vinculación en la app
- Abre **Alerta Wear** en el reloj: el indicador inferior debe mostrar **`● Conectado`**.
- Abre **Alerta Wear** en el teléfono: la tarjeta superior en **Inicio** debe indicar **`Reloj conectado`** junto con el nombre del nodo.

---

## 7. Cómo configurar el contacto de confianza y los permisos

1. **Permisos en el teléfono:**
   - Al abrir la aplicación móvil por primera vez, Android solicitará permisos de **Ubicación** (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`), **Envío de SMS** (`SEND_SMS`) y **Notificaciones** (`POST_NOTIFICATIONS` en Android 13+).
   - Si alguno fue rechazado inicialmente, en las pantallas **Inicio** y **Contacto** aparecen botones directos (**Autorizar ubicación** / **Autorizar permiso de SMS**) para volver a solicitarlos.
2. **Configurar el contacto:**
   - En la barra de navegación inferior del teléfono, entra a **Contacto**.
   - Escribe el **nombre** y el **número telefónico** del contacto de confianza.
   - Pulsa **Guardar contacto**. El contacto queda almacenado localmente en el teléfono.

---

## 8. Instrucciones para pruebas con un contacto con consentimiento

Antes de realizar una prueba real de envío de SMS en un teléfono con tarjeta SIM activa:

1. **Obtén consentimiento previo:** Configura únicamente tu propio número telefónico o el número de una persona que **haya aceptado previamente recibir mensajes SMS de prueba** durante tus prácticas.
2. **Contenido del mensaje que recibirá:**
   - Si la ubicación está disponible:
     ```text
     Alerta de prueba de Alerta Wear. Necesito ayuda.
     Mi ubicación: https://maps.google.com/?q=LATITUD,LONGITUD
     ```
   - Si la ubicación no está disponible:
     ```text
     Alerta de prueba de Alerta Wear. Necesito ayuda.
     Ubicación no disponible (<motivo>).
     ```
3. **Flujo recomendado de prueba:**
   - Verifica primero la cancelación manteniendo presionado **SOS** en el reloj y tocando **Cancelar** antes de los 5 segundos (no se enviará ningún SMS).
   - Después, mantén presionado **SOS** y deja concluir la cuenta regresiva para observar la transición de estados en el reloj, en la pantalla **Alerta** del teléfono y en la pestaña **Historial**.

---

## 9. Flujo de una alerta y significado de sus estados

| Estado interno (`AlertStatus`) | Etiqueta en interfaz | Significado real |
| :--- | :--- | :--- |
| **`RECEIVED`** | *Solicitud recibida* | El teléfono recibió la solicitud SOS desde el reloj. |
| **`PREPARING_LOCATION`** | *Preparando* | El teléfono está consultando `FusedLocationProviderClient` y preparando el mensaje. |
| **`SENDING_SMS`** | *Enviando SMS* | Se invocó `SmsManager` para despachar el SMS, pero el módem/operador aún no confirma su salida. |
| **`SENT_BY_DEVICE`** | *Enviado por el dispositivo (entrega sin confirmar)* | El sistema Android confirmó (`ACTION_SMS_SENT` = `RESULT_OK`) que el SMS salió del teléfono, sin confirmación de entrega al destinatario final. |
| **`DELIVERED`** | *Entregado* | La red celular devolvió un reporte de entrega (`ACTION_SMS_DELIVERED` = `RESULT_OK`). Solo se muestra si llega dicha confirmación. |
| **`CANCELLED`** | *Cancelada* | El usuario canceló la cuenta regresiva o detuvo la preparación antes de enviar el SMS. |
| **`ERROR`** | *Error* | No se pudo enviar el SMS (falta de contacto configurado, permiso `SEND_SMS` denegado, ausencia de tarjeta SIM o fallo de red/señal). |

---

## 10. Cómo ejecutar las pruebas automatizadas desde Windows

Desde PowerShell o símbolo del sistema en la raíz del proyecto (`c:\...\ALERTAWEAR`), ejecuta:

```powershell
# Compilar ambos módulos y ejecutar todas las pruebas unitarias (:app y :wear)
.\gradlew.bat :app:assembleDebug :wear:assembleDebug :app:testDebugUnitTest :wear:testDebugUnitTest
```

Las pruebas unitarias (`MobileAlertViewModelTest` y `WearAlertViewModelTest`) utilizan dobles de prueba (`FakePhoneSmsSender`, `FakeLocationProvider`, `FakeWearPhoneBridge`, `InMemoryAlertStorage`), por lo que **nunca envían SMS reales** ni modifican datos reales al ejecutarse.

- Reporte HTML de `:app`: `app\build\reports\tests\testDebugUnitTest\index.html`
- Reporte HTML de `:wear`: `wear\build\reports\tests\testDebugUnitTest\index.html`

### Prueba instrumentada en dispositivo (`AlertStorageInstrumentedTest`)
Para ejecutar la prueba instrumentada de persistencia (`SharedPrefsAlertStorage`) únicamente en el teléfono sin que Gradle instale `:app` en el emulador del reloj ni colisione si el teléfono está conectado por USB y Wi-Fi ADB simultáneamente:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <SERIAL_TELEFONO> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <SERIAL_TELEFONO> install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s <SERIAL_TELEFONO> shell am instrument -w -e class com.example.alertawear.AlertStorageInstrumentedTest com.example.alertawear.test/androidx.test.runner.AndroidJUnitRunner
```

---

## 11. Limitaciones conocidas

1. **Ejecución en segundo plano y pantalla bloqueada o apagada (Android 8+ / 12+ / 14+):**
   - Cuando el reloj envía un SOS, `PhoneWearableListenerService` es vinculado temporalmente por Google Play Services y procesa la solicitud en su hilo de trabajo antes de retornar (`runBlocking` con límite de 14 segundos) para evitar que el servicio se desvincule prematuramente.
   - **Límite de ubicación en segundo plano:** Si el usuario solo otorgó el permiso de ubicación *"Mientras la app está en uso"* (`ACCESS_FINE_LOCATION` sin `ACCESS_BACKGROUND_LOCATION`) y la app móvil está en segundo plano o con la pantalla apagada/bloqueada, Android bloquea la lectura del GPS. En ese caso, la aplicación **envía el SMS de prueba sin coordenadas**, indicando en el texto que la ubicación no estuvo disponible en segundo plano, y muestra una notificación de alta prioridad.
   - **Por qué no se agregan `ACCESS_BACKGROUND_LOCATION` ni servicios persistentes por defecto, y por qué tampoco garantizan ejecución:**
     - Solicitar ubicación en segundo plano (`ACCESS_BACKGROUND_LOCATION`) o mantener un *Foreground Service* permanente conlleva restricciones estrictas del sistema operativo y de políticas de plataforma, además de mayor consumo de batería.
     - Más importante aún: **ni siquiera un *Foreground Service* ni `ACCESS_BACKGROUND_LOCATION` garantizan ejecución continua al 100 % en Android moderno**. A partir de Android 12/14+, el sistema restringe el inicio de *Foreground Services* desde segundo plano, el modo *Doze* con pantalla apagada retrasa entregas de red/sensores, las capas de ahorro de batería de distintos fabricantes (OEM) pueden terminar procesos en segundo plano y, si el usuario fuerza el cierre (*Force Stop*) de la aplicación, ningún servicio o receptor se ejecutará hasta que la aplicación vuelva a abrirse manualmente.
2. **Pérdida de confirmación hacia el reloj (*ACK Timeout*):**
   - Si el reloj envía la solicitud SOS pero el teléfono no devuelve confirmación (por desconexión Bluetooth posterior al envío o interrupción del proceso en segundo plano), el reloj deja de mostrar *"Preparando"* tras 12 segundos y pasa a **Sin confirmación (*"No se pudo confirmar el resultado"*)**, indicando revisar el teléfono sin afirmar que el SMS se envió ni que falló.
3. **Confirmación de entrega (`DELIVERED`):**
   - El broadcast `ACTION_SMS_DELIVERED` depende del soporte de reportes de entrega (*Delivery Reports*) de cada operadora telefónica. En redes que no notifican entrega al dispositivo origen, el estado permanecerá en **Enviado por el dispositivo (entrega sin confirmar)**.
4. **Teléfono sin señal, modo avión o sin tarjeta SIM:**
   - Si el teléfono no tiene servicio celular o está en modo avión, el envío falla con estado **Error** y no se encola para reintento automático posterior cuando regrese la señal; el usuario debe iniciar o reintentar el aviso.
5. **Alcance de conexión del reloj:**
   - La comunicación entre el reloj y el teléfono depende del enlace activo de **Wear OS Data Layer** (Bluetooth / red local emparejada). Si el reloj pierde conexión con el teléfono, no puede enviar el SMS por cuenta propia y muestra la pantalla **Sin conexión**.
