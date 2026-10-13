# 📱 Status HD - Optimizador de Estados de WhatsApp (Android)

Aplicación nativa para Android que comprime videos con calidad nítida evitando la compresión destructiva de WhatsApp en Android.

---

### ✨ Características
1. **Integración con el menú "Compartir":** Ve a tu Galería, pulsa *Compartir*, elige **Status HD** y listo.
2. **Bitrate Dinámico Inteligente:**
   - **Videos $\le$ 35 segundos:** 1080×1920 con hasta 4.8 Mbps.
   - **Videos de 35 a 60 segundos:** 720×1280 ajustado a ~14.5 MB.
3. **División Automática:** Si el video dura más de 60 segundos, lo divide en partes de hasta 60s ya comprimidas.
4. **Directo a WhatsApp:** Genera el intent para publicar directamente como Estado.

---

### 🚀 Cómo compilar tu APK gratis con GitHub (Sin instalar Android Studio en tu PC)

Como no tienes el Android SDK instalado en tu máquina, la forma más rápida y limpia de obtener tu archivo `.apk` es con **GitHub Actions**:

1. Crea un repositorio nuevo (puede ser público o privado) en tu cuenta de [GitHub](https://github.com/).
2. Sube esta carpeta (`Android_App`) a tu repositorio:
   ```bash
   git init
   git add .
   git commit -m "Initial commit Status HD"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/TU_REPOSITORIO.git
   git push -u origin main
   ```
3. En la pestaña **Actions** de tu repositorio en GitHub, verás el flujo *Build Android APK* ejecutándose automáticamente.
4. En 2 o 3 minutos terminará. Entra a la ejecución y en la sección **Artifacts** descarga el archivo zip que contiene `app-debug.apk`.
5. Pasa el `.apk` a tu teléfono o descárgalo directamente desde el navegador de tu celular e instálalo.

---

### 💻 Opción local (si prefieres usar Android Studio):
Solo abre esta carpeta `Android_App` con **Android Studio** y presiona `Run` o `Build > Build APK`.
