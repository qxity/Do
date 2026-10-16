# VidSaver 📥

Aplikasi Android untuk mengunduh video & audio dari YouTube, TikTok, Instagram, Facebook, X/Twitter, dan ratusan situs lain (mesin: yt-dlp + ffmpeg).

## Fitur
- Tempel link atau **Bagikan → VidSaver** langsung dari aplikasi lain
- Deteksi otomatis link di clipboard
- Pratinjau (thumbnail, judul, durasi) sebelum unduh
- Pilih format: Terbaik, 1080p, 720p, 480p, 360p, MP3, M4A
- **Unduh Cepat** dengan kualitas default
- Antrean multi-unduhan (1–4 bersamaan), progres, kecepatan, sisa waktu, batal & coba lagi
- Tetap jalan di latar belakang + notifikasi progres & selesai (tap untuk memutar)
- Riwayat dengan pencarian, putar, bagikan, salin link, unduh ulang
- Unduh playlist, sematkan subtitle, cover pada audio, lewati sponsor YouTube (SponsorBlock)
- Update yt-dlp dari dalam aplikasi (penting saat situs berubah)
- Material You + mode gelap
- File tersimpan di `Movies/VidSaver` dan `Music/VidSaver` (langsung muncul di galeri)

## Build lewat GitHub
1. Buat repository baru di GitHub.
2. Upload **seluruh isi** folder ini (termasuk folder `.github`).
3. Buka tab **Actions** → workflow **Build APK** berjalan otomatis (±5–10 menit).
4. Setelah hijau ✓, buka run-nya → bagian **Artifacts** → unduh `VidSaver-apk`.
5. Pilih APK sesuai HP:
   - `app-arm64-v8a-release.apk` → hampir semua HP modern (pilih ini kalau ragu)
   - `app-armeabi-v7a-release.apk` → HP 32-bit lama
   - `app-x86_64-release.apk` → emulator

Rilis otomatis: buat tag `v1.0.0` lalu push → APK dilampirkan ke halaman Releases.

## Catatan
- Pertama kali dibuka, aplikasi butuh beberapa detik untuk menyiapkan mesin unduh.
- Jika unduhan gagal, coba **Setelan → Perbarui yt-dlp**.
- Konten privat (akun terkunci, story privat) tidak bisa diunduh.
- Gunakan untuk konten milik sendiri atau yang diizinkan pemiliknya.
