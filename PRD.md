# Product Requirements Document (PRD)

## PipeCounter — Aplikasi Penghitung Pipa Baja Berbasis AI & Verifikasi Visual

---

| **Dokumen** | **Keterangan** |
| --- | --- |
| **Nama Proyek** | PipeCounter |
| **Versi Dokumen** | 1.1 |
| **Tanggal** | 28 Juli 2026 |
| **Status** | Draft |
| **Tim Pengembang** | \[Diisi oleh tim\] |
| **Stakeholder** | \[Diisi oleh manajer proyek / klien\] |

---

## 1. Pendahuluan

### 1.1 Latar Belakang

Dalam industri konstruksi dan pergudangan baja, penghitungan jumlah pipa dalam satu *bundle* merupakan kegiatan yang sering dilakukan secara manual — memakan waktu, rentan kesalahan, dan tidak efisien. Keterbatasan akses ke sudut pandang atas (overhead) menyulitkan penghitungan akurat karena pipa saling menutupi.

### 1.2 Tujuan

Membangun aplikasi Android yang:

- Menghitung jumlah pipa dalam satu *bundle* secara otomatis menggunakan model YOLO yang sudah dilatih.
- Menggabungkan hasil deteksi dari **beberapa sisi** *bundle* untuk menghasilkan estimasi yang lebih akurat.
- Menyediakan fitur **verifikasi visual** berbasis overlay bounding box pada foto hasil scan agar pengguna dapat memeriksa hasil deteksi secara detail.

### 1.3 Ruang Lingkup

| **Termasuk** | **Tidak Termasuk** |
| --- | --- |
| Aplikasi Android native (Kotlin) | Versi iOS |
| Deteksi lubang pipa menggunakan YOLO + NCNN | Pelatihan ulang model YOLO |
| Pemindaian multi-sisi (kiri, kanan, depan) | Integrasi dengan sistem ERP/backend |
| Verifikasi visual: overlay bounding box pada foto hasil scan | ARCore, live camera overlay, marker AR, homography |
| Riwayat hasil pemindaian | Ekspor data ke cloud |

---

## 2. Pengguna & Persona

| **Persona** | **Deskripsi** |
| --- | --- |
| **Pekerja Gudang / Operator** | Pengguna utama. Bertugas menghitung pipa di lapangan. Tidak memiliki latar belakang teknis. Membutuhkan antarmuka yang sederhana dan cepat. |
| **Supervisor / Manajer** | Mengawasi akurasi penghitungan. Membutuhkan laporan dan riwayat untuk verifikasi. |
| **Teknisi / Developer** | Mengelola model YOLO dan melakukan pemeliharaan aplikasi. |

---

## 3. Persyaratan Fungsional

### 3.1 Modul Pemindaian Multi-Sisi (Ide 2)

| **ID** | **Fitur** | **Deskripsi** | **Prioritas** |
| --- | --- | --- | --- |
| F-01 | Pemindaian Sisi Kiri | Pengguna mengarahkan kamera ke sisi kiri *bundle*, aplikasi mengambil foto dan menjalankan deteksi YOLO. | High |
| F-02 | Pemindaian Sisi Kanan | Sama seperti F-01, untuk sisi kanan. | High |
| F-03 | Pemindaian Sisi Depan | Sama seperti F-01, untuk sisi depan (opsional). | Medium |
| F-04 | Panduan Posisi Kamera | Aplikasi menampilkan bingkai panduan di layar agar pengguna mengambil foto dari jarak dan sudut yang konsisten. | High |
| F-05 | Agregasi Hasil | Aplikasi secara otomatis mengambil **nilai maksimum** dari semua sisi yang dipindai sebagai estimasi final. | High |
| F-06 | Tampilkan Hasil per Sisi | Menampilkan jumlah pipa dari masing-masing sisi sebelum agregasi. | Medium |
| F-07 | Batalkan / Ulangi Pemindaian | Pengguna dapat membatalkan atau mengulangi pemindaian satu sisi tertentu. | Medium |

### 3.2 Modul Verifikasi Visual (Still Image)

| **ID** | **Fitur** | **Deskripsi** | **Prioritas** |
| --- | --- | --- | --- |
| F-08 | Overlay 2D pada Foto | Menampilkan kotak (bounding box) dan nomor urut pipa di atas **foto still** hasil deteksi; koordinat mengikuti resolusi foto capture. | High |
| F-09 | Layar Verifikasi | Membuka foto hasil scan + overlay dari layar hasil atau riwayat (bukan kamera live). | High |
| F-10 | Navigasi Antar Sisi | Beralih menampilkan foto + overlay sisi kiri, kanan, atau depan yang sudah disimpan. | High |
| F-11 | Zoom & Geser | Pinch/drag pada foto + overlay untuk memeriksa detail. | Medium |
| F-12 | Konfirmasi Manual | Pengguna dapat mengonfirmasi bahwa hasil hitungan sudah benar, atau mencatat ketidaksesuaian. | Low |

### 3.3 Modul Riwayat & Laporan

| **ID** | **Fitur** | **Deskripsi** | **Prioritas** |
| --- | --- | --- | --- |
| F-13 | Simpan Hasil | Menyimpan hasil pemindaian (tanggal, waktu, jumlah per sisi, estimasi final, foto) ke penyimpanan lokal. | High |
| F-14 | Lihat Riwayat | Daftar riwayat pemindaian yang sudah disimpan. | Medium |
| F-15 | Ekspor Laporan | Ekspor riwayat sebagai file CSV/PDF atau bagikan melalui email/WhatsApp. | Low |

---

## 4. Persyaratan Non-Fungsional

| **ID** | **Kategori** | **Deskripsi** | **Target** |
| --- | --- | --- | --- |
| NF-01 | Performa | Waktu inferensi YOLO per gambar | &lt; 500 ms pada perangkat mid-range |
| NF-02 | Performa | Waktu buka aplikasi | &lt; 3 detik |
| NF-03 | Akurasi | Akurasi deteksi lubang pipa | ≥ 90% (untuk pipa yang terlihat) |
| NF-04 | Usability | Antarmuka intuitif, minimal 3 kali klik untuk menyelesaikan satu siklus pemindaian | Ya |
| NF-05 | Offline | Aplikasi berfungsi sepenuhnya tanpa koneksi internet | Ya |
| NF-06 | Keamanan | Tidak ada data yang dikirim ke server eksternal | Ya |
| NF-07 | Kompatibilitas | Mendukung perangkat Android | API Level 24 (Android 7.0) ke atas |
| NF-08 | Kompatibilitas | Verifikasi still-image berjalan tanpa ARCore | Semua perangkat yang memenuhi NF-07 + kamera |
| NF-09 | Daya Tahan Baterai | Konsumsi daya saat penggunaan aktif | Tidak boros (dioptimalkan) |

---

## 5. Tech Stack (Opsi 1: Native Android)

| **Komponen** | **Teknologi** | **Keterangan** |
| --- | --- | --- |
| **Bahasa** | Kotlin | Bahasa utama untuk Android native |
| **IDE** | Android Studio | Versi terbaru dengan NDK & CMake |
| **AI Runtime** | NCNN | Framework inference ringan dari Tencent |
| **CV Library** | OpenCV-Mobile | Preprocess gambar (opsional; draw box bisa Canvas) |
| **Overlay UI** | ImageView + Canvas / custom View | Bounding box & nomor di atas foto still |
| **Kamera** | CameraX | Capture foto untuk deteksi (bukan live AR) |
| **Model Format** | `.param` + `.bin` | Format NCNN |

### 5.1 Alur Konversi Model

[<u>best.pt</u>](http://best.pt) (PyTorch) → export ONNX → onnx2ncnn → best.param + best.bin

Proyek contoh tersedia di GitHub untuk referensi.

---

## 6. Arsitektur Sistem

```
┌─────────────────────────────────────────────────────────────┐
│ Aplikasi Android                                            │
├─────────────────────────────────────────────────────────────┤
│  UI (Activity/Fragment)  ←→  ViewModel  ←→  Repository      │
│         │                                                   │
│  Scan multi-sisi · Aggregate (max) · VerifyStill (overlay)  │
│         │                                                   │
│  CameraX (capture)  ·  NCNN YOLO (JNI)  ·  Local storage    │
└─────────────────────────────────────────────────────────────┘
```

### 6.1 Komponen Native (C++)

- **detector.cpp**: Memuat model NCNN, melakukan pre-processing, inferensi, dan post-processing (NMS).
- Dihubungkan ke Kotlin melalui **JNI (Java Native Interface)**.

---

## 7. Alur Pengguna (User Flow)

### 7.1 Alur Pemindaian Multi-Sisi

\[Mulai\] → \[Pilih "Mulai Pemindaian"\]\
↓\
\[Panduan: Arahkan ke Sisi Kiri\] → \[Ambil Foto\] → \[Deteksi YOLO\] → \[Tampilkan Hasil Sisi Kiri: 50\]\
↓\
\[Panduan: Arahkan ke Sisi Kanan\] → \[Ambil Foto\] → \[Deteksi YOLO\] → \[Tampilkan Hasil Sisi Kanan: 52\]\
↓\
\[Panduan: Arahkan ke Sisi Depan\] → \[Ambil Foto\] → \[Deteksi YOLO\] → \[Tampilkan Hasil Sisi Depan: 48\]\
↓\
\[Agregasi: Maksimum = 52\] → \[Tampilkan Estimasi Final: 52 Batang\]\
↓\
\[Simpan / Verifikasi / Selesai\]

### 7.2 Alur Verifikasi Visual (Still Image)

\[Pilih "Verifikasi"\] → \[Tampilkan foto sisi aktif + bounding box + nomor\]\
↓\
\[Ganti Sisi\] → \[foto + overlay sisi lain yang tersimpan\]\
↓\
\[Zoom / Geser\] → periksa detail\
↓\
\[Konfirmasi / Catat ketidaksesuaian\] → \[Selesai\]

---

## 8. Antarmuka Pengguna (UI/UX)

### 8.1 Layar Utama (Home)

- Tombol **"Mulai Pemindaian Baru"**
- Tombol **"Riwayat"**
- Tombol **"Tentang"**

### 8.2 Layar Pemindaian

- **Bingkai panduan** (overlay kotak) untuk membantu pengguna memposisikan kamera.
- **Indikator sisi** (Kiri / Kanan / Depan) yang sedang dipindai.
- Tombol **"Ambil Foto"** (atau otomatis saat posisi pas).
- **Loading indicator** saat deteksi berjalan.
- **Hasil sementara** ditampilkan setelah setiap sisi.

### 8.3 Layar Hasil

- Tabel ringkasan:

  | Sisi | Jumlah |
  | --- | --- |
  | Kiri | 50 |
  | Kanan | 52 |
  | Depan | 48 |

- **Estimasi Final**: 52 batang (nilai maksimum)

- Tombol **"Verifikasi"** (buka foto + overlay bounding box)

- Tombol **"Simpan"**

- Tombol **"Ulangi"**

### 8.4 Layar Verifikasi (Foto + Overlay)

- **ImageView** menampilkan foto hasil scan dengan bounding box & nomor digambar di atasnya (bukan kamera live).
- Tombol / chip **"Ganti Sisi"** (Kiri / Kanan / Depan) — ganti foto + overlay tersimpan.
- Pinch **zoom** & **geser** untuk detail.
- Tombol **"Konfirmasi"** / **"Laporkan Masalah"**.
- Tombol **"Kembali"**.

---

## 9. Persyaratan Perangkat Keras

| **Komponen** | **Minimum** | **Rekomendasi** |
| --- | --- | --- |
| OS Android | 7.0 (API 24) | 10.0+ |
| RAM | 3 GB | 6 GB+ |
| Kamera | 8 MP | 12 MP+ |
| CPU | ARM64 | Snapdragon 700+ |
| GPU | OpenGL ES 2.0+ | OpenGL ES 3.0+ / Vulkan |

---

## 10. Jadwal Pengembangan (Estimasi)

| **Fase** | **Aktivitas** | **Durasi** |
| --- | --- | --- |
| **Fase 0** | Konversi model YOLO ke NCNN (.param/.bin) | 3-5 hari |
| **Fase 1** | Setup proyek Android + integrasi NCNN + deteksi dasar | 2 minggu |
| **Fase 2** | Integrasi CameraX + UI pemindaian multi-sisi | 1 minggu |
| **Fase 3** | Layar verifikasi still: draw boxes, ganti sisi, zoom/pan | 3-5 hari |
| **Fase 4** | Fitur riwayat & penyimpanan | 1 minggu |
| **Fase 5** | Pengujian (QA) & optimasi performa | 1 minggu |
| **Fase 6** | Deployment ke Google Play (internal) | 3 hari |
| **Total** |  | **\~6-8 minggu** |

---

## 11. Risiko & Mitigasi

| **Risiko** | **Dampak** | **Mitigasi** |
| --- | --- | --- |
| Model YOLO tidak kompatibel dengan NCNN | Tinggi | Gunakan skrip konversi dari proyek contoh; uji di awal |
| Performa inferensi lambat di perangkat low-end | Sedang | Gunakan model YOLO yang lebih kecil (YOLOv8n); turunkan resolusi input |
| Overlay sulit dibaca (foto gelap/blur) | Sedang | Ulangi capture (F-07); pastikan panduan bingkai & pencahayaan |
| Akurasi deteksi rendah karena pipa saling menutup | Tinggi | Gunakan agregasi multi-sisi (nilai maksimum) sebagai strategi utama |
| Pengguna kesulitan memposisikan kamera | Sedang | Tambahkan panduan visual (bingkai) dan instruksi teks |

---

## 12. Metrik Keberhasilan

| **Metrik** | **Target** |
| --- | --- |
| Akurasi estimasi final (dibandingkan hitungan manual) | ≥ 95% |
| Waktu rata-rata per siklus pemindaian (3 sisi) | &lt; 2 menit |
| User Satisfaction Score (dari uji coba lapangan) | ≥ 4.0 / 5.0 |
| Crash-free rate | ≥ 99.5% |

---

## 13. Lampiran

### 13.1 Referensi Proyek Contoh

- [ncnn-android-yolov8-seg](https://github.com/UnstoppableCurry/ncnn-android-yolov8-seg-Seal)
- [YOLOv8-Mobile](https://github.com/xforcevesa/YOLOv8-Mobile)
- [YoloMobile Library](https://github.com/wkt/YoloMobile)
- [ncnn-android-yolov8_fork](https://github.com/xuewengeophysics/ncnn-android-yolov8_fork)

### 13.2 Glosarium

| **Istilah** | **Definisi** |
| --- | --- |
| **Bundle** | Satu ikatan/paket pipa baja yang diikat bersama |
| **YOLO** | You Only Look Once — arsitektur deteksi objek real-time |
| **NCNN** | Neural Network Computing Framework — inference engine dari Tencent |
| **Still-image overlay** | Bounding box digambar di atas foto hasil scan (bukan live AR) |
| **Bounding Box** | Kotak pembatas yang mengelilingi objek yang terdeteksi |
| **NMS** | Non-Maximum Suppression — teknik untuk menghilangkan deteksi ganda          |