# Production Readiness Audit Report: Sumbing Companion

Audit menyeluruh terhadap project Sumbing Companion untuk memastikan stabilitas, performa, keamanan, dan kualitas sebelum rilis ke publik/Play Store.

## 1. Stability & Crash (CRITICAL)

### **A. Media Projection Resource Management**
- **Masalah**: `ImageReader.acquireLatestImage()` sering kali mengembalikan `null` jika tidak ditangani dengan benar, dan `Image` object **WAJIB** di-close. Jika tidak, `ImageReader` akan berhenti menghasilkan frame baru karena buffer penuh.
- **Fix**: Pastikan blok `finally { image.close() }` selalu dieksekusi di `FloatingWindowService.captureScreen()`. (Sudah ada di kode, tapi pastikan tidak ada kebocoran di loop utama).
- **Potensi ANR**: Loop `while (isActive)` di `FloatingWindowService` menggunakan `delay(1000)`. Ini aman untuk threading, tapi pemrosesan bitmap berat sebaiknya tetap di `Dispatchers.Default` bukan `IO`.

### **B. Context Memory Leak**
- **Masalah**: `SettingsRepository(this)` dipanggil di `Activity` dan `Service`.
- **Fix**: Selalu gunakan `context.applicationContext` di dalam constructor `SettingsRepository` untuk menghindari kebocoran `Activity`.

---

## 2. Performance (WARNING)

### **A. Recomposition Berlebihan (Jetpack Compose)**
- **Masalah**: `FloatingWindowService` mengirim update `uiState` setiap 200ms melalui `stateManager.updateTick()`. Hal ini memicu recomposition di seluruh `MiniPanelView` setiap 1/5 detik.
- **Fix**: Gunakan `derivedStateOf` atau bagi UI menjadi komponen yang lebih kecil agar hanya bagian countdown saja yang re-render, bukan seluruh panel.

### **B. Bitmap Recycling**
- **Masalah**: Pemrosesan bitmap di `DetectionEngine` dan `DataCollectionManager` membuat instance `Bitmap` baru secara terus menerus.
- **Fix**: Panggil `bitmap.recycle()` segera setelah bitmap tidak lagi dibutuhkan (setelah di-crop atau disimpan).

---

## 3. Security (WARNING)

### **A. Insecure Data Storage**
- **Masalah**: DataStore digunakan untuk menyimpan setting. Secara default ini tidak terenkripsi.
- **Fix**: Untuk aplikasi ini, data yang disimpan (posisi bubble, region) tidak sensitif. Namun jika nanti ada API Key atau User Data, gunakan **EncryptedSharedPreferences**.

### **B. Exported Components**
- **Masalah**: `MainActivity` bertanda `android:exported="true"`. Ini benar karena itu launcher.
- **Fix**: Pastikan `RegionCalibrationActivity` tidak bisa dipanggil oleh aplikasi luar dengan menambahkan `android:exported="false"`.

---

## 4. Code Quality (MINOR)

### **A. Logging di Production**
- **Masalah**: Masih banyak `Log.d` dan `Log.e` yang tersebar di `DetectionEngine` dan `FloatingWindowService`.
- **Fix**: Gunakan **Timber** yang dikonfigurasi hanya log di mode debug, atau bungkus dengan `if (BuildConfig.DEBUG)`.

---

## 5. Build & Release (CRITICAL)

### **A. Obfuscation (R8/Proguard)**
- **Masalah**: `isMinifyEnabled = false` di `build.gradle.kts`. Ini akan membuat kode mudah didecompile.
- **Fix**: Aktifkan `isMinifyEnabled = true` dan tambahkan aturan Proguard untuk **TensorFlow Lite** agar model tetap bisa diload.

### **B. Target SDK**
- **Status**: Sudah menggunakan SDK 35 (Android 15), sangat bagus.

---

## 6. UX & Production Quality

### **A. Edge Cases: Permission**
- **Masalah**: Jika user mencabut izin `SYSTEM_ALERT_WINDOW` saat service jalan, aplikasi akan crash.
- **Fix**: Tambahkan check berkala atau handle exception saat `windowManager.addView`.

---

## Final Checklist Sebelum Upload

- [ ] Ubah `isMinifyEnabled` menjadi `true`.
- [ ] Versi final model TFLite sudah masuk ke folder `assets`.
- [ ] Hapus atau nonaktifkan semua `Log.d` yang sensitif.
- [ ] Update `versionCode` dan `versionName`.
- [ ] Generate Signed APK (jangan gunakan debug keystore).
- [ ] Test di device dengan Android 10, 12, dan 14 (perubahan permission Foreground Service cukup drastis di versi ini).
