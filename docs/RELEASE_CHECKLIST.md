# RIDEDECIDER — PROTOCOLO Y CHECKLIST DE RELEASE PROFESIONAL

## 1. Reglas Fundamentales de Versionado

1. **`versionCode` (Entero Incremental Obligatorio)**:
   - Debe incrementarse exactamente de uno en uno en cada entrega: `1` -> `2` -> `3`...
   - El instalador de Android utiliza este valor entero para validar la precedencia de la actualización (`newVersionCode > currentVersionCode`).
2. **`versionName` (Semver Estándar `MAJOR.MINOR.PATCH`)**:
   - `MAJOR`: Cambios estructurales mayores de plataforma o arquitectura.
   - `MINOR`: Nuevas funcionalidades, mejoras del motor de cálculo o nuevos filtros.
   - `PATCH`: Correcciones de errores, ajustes cosméticos o parches de estabilidad.
   - Ejemplo: `1.0.0` -> `1.0.1` -> `1.0.2` -> `1.1.0` -> `2.0.0`.

---

## 2. Identidad Criptográfica Inmutable

- **Keystore oficial**: `ridedecider-release.jks`
- **Algoritmo**: `RSA (4096 bits)`
- **Key Alias**: `ridedecider`
- **SHA-256 del Certificado (Firma)**:
  `2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62`

> [!CRITICAL]
> **NO cambiar jamás este certificado**. Cualquier cambio de keystore romperá la compatibilidad con las aplicaciones ya instaladas en los dispositivos de los conductores, obligándoles a desinstalar y perdiendo sus bases de datos locales de Room.

---

## 3. Comandos de Verificación y Generación de Metadatos

### A. Generar Build de Producción Firmada
```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```
El archivo resultante se ubicará en:
`app\build\outputs\apk\release\app-release.apk`

### B. Obtener SHA-256 del Certificado (Firma)
```powershell
& "C:\Users\flori\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs "app\build\outputs\apk\release\app-release.apk"
```

### C. Obtener SHA-256 del Archivo APK (Integridad de Descarga)
```powershell
(Get-FileHash "app\build\outputs\apk\release\app-release.apk" -Algorithm SHA256).Hash.ToLower()
```

### D. Obtener Tamaño en Bytes del APK
```powershell
(Get-Item "app\build\outputs\apk\release\app-release.apk").Length
```

---

## 4. Estructura del Manifiesto `latest.json`

```json
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "minSupportedVersionCode": 1,
  "mandatory": false,
  "apkUrl": "https://github.com/ridedecider/releases/releases/download/v1.0.1/RideDecider-1.0.1.apk",
  "apkSha256": "<insertar_hash_sha256_del_apk_aqui>",
  "apkSizeBytes": 15485760,
  "releaseDate": "2026-09-01T12:00:00Z",
  "releaseNotes": [
    "Mejora en cálculo de rentabilidad y ritmo horario",
    "Optimización de consumo de memoria en segundo plano",
    "Corrección de lectura en pantalla"
  ]
}
```

---

## 5. Checklist Paso a Paso para Nuevas Versiones

- [ ] **1. Preparación de Código**:
  - Implementar cambios requeridos sin tocar esquemas de Room destructivos ni lógica de HUD no autorizada.
- [ ] **2. Actualización de Versión en `app/build.gradle.kts`**:
  - Incrementar `versionCode = N + 1`.
  - Actualizar `versionName = "X.Y.Z"`.
- [ ] **3. Ejecución de Tests Unitarios**:
  - Ejecutar `.\gradlew.bat testDebugUnitTest` y confirmar 100% de tests verdes.
- [ ] **4. Compilación Release**:
  - Ejecutar `.\gradlew.bat assembleRelease`.
- [ ] **5. Auditoría de Integridad del APK**:
  - Verificar firma con `apksigner verify` -> Confirmar `2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62`.
  - Obtener SHA-256 del binario APK mediante `Get-FileHash`.
  - Obtener tamaño en bytes del APK.
- [ ] **6. Renombrado para Distribución**:
  - Copiar/renombrar `app-release.apk` a `RideDecider-X.Y.Z.apk`.
- [ ] **7. Creación y Publicación del Release**:
  - Subir `RideDecider-X.Y.Z.apk` al servidor / GitHub Releases.
  - Generar y publicar `latest.json` con la URL de descarga y el `apkSha256`.
- [ ] **8. Verificación de Actualización Real**:
  - Abrir la app en el dispositivo de prueba con versión anterior -> Comprobar detección, descarga, verificación SHA-256 e instalación sin pérdida de datos en Room.
