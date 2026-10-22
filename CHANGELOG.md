# Doom Launcher - changelog

Versi kita sendiri. Nomor ada di file `VERSION`; tiap build menampilkannya sebagai
`<VERSION>-r<nomor run>` di Settings > About dan di header layar Instances.
Catatan: label 1.0.0 sampai 1.2.0 diberikan belakangan, jadi build lama masih
menampilkan 0.50.0.

## 1.7.0 - editor layout ditulis ulang, perbaikan tombol hilang
- Perbaikan penting: file layout engine menyimpan posisi sebagai 0 sampai 1 (dibagi 26 dan 16),
  tetapi editor 1.5.0 dan 1.6.0 menganggapnya unit grid. Akibatnya semua tombol menumpuk di
  pojok kiri atas saat dibuka, dan tombol hilang di game setelah disimpan. Sekarang dikonversi
  dengan benar di kedua arah.
- File yang sudah telanjur rusak dikenali dan diperbaiki otomatis saat dibuka. Tekan Simpan.
- Editor menjadi halaman layar penuh sendiri: daftar tombol di kiri, kanvas di tengah,
  panel pengaturan di kanan (di layar sempit, panel ada di bawah).
- Kanvas digambar sekali (bukan satu komponen per tombol), jadi lebih ringan.
- Mudah dipakai: zoom dan geser kanvas, titik ubah ukuran yang besar, tombol kecil tetap
  bisa dipilih, snap ke grid, slider X Y W H dengan tombol -/+ untuk ketelitian.
- Undo dan redo, cadangan otomatis (.bak) sebelum menyimpan, pulihkan cadangan, kembalikan
  ke bawaan engine, dan cermin kiri-kanan untuk pengguna kidal.
- Penyimpanan diperiksa dulu: kalau ada tombol yang hilang di hasil tulis, tidak ditulis.
- Sumber GitHub di downloader sekarang mencari repositori yang menandai dirinya sebagai mod
  dan menyaring tools. Ada chip "Mods only" untuk mematikan saringan.

## 1.6.0 - perbaikan build, Home ala Zalith, build lebih cepat
- Perbaikan: build 1.5.0 gagal karena AnimatedVisibility bentrok dengan Row di MainScreen.
  Semua pemakaian diganti dengan SlimeOverlay.
- Home sekarang punya kartu besar untuk instance terpilih: nama, IWAD, jumlah mod dan map,
  tombol PLAY, pintasan ke Mods dan pengaturan, info RAM bebas dan layout.
- Ganti instance lewat chip di kiri atas atau dengan mengetuk kartu di bawahnya.
- Latar kartu bisa diganti dengan gambar sendiri (tombol gambar di kanan atas).
- Side rail bergaya Zalith: penanda kuning di tab yang aktif.
- Workflow: pilihan ABI. Bawaan hanya arm64-v8a, jadi build lebih cepat dan APK lebih kecil.
  Pilih "all" kalau perlu HP 32-bit. Gradle diatur paralel dan memakai cache.

## 1.5.0 - instance ala Zalith, downloader mod dan map, editor layout
- Animasi slime sekarang juga saat pindah tab dan pindah halaman.
- Kartu instance punya tombol Play, Mods, Maps, dan Pengaturan. Mods dan Maps membuka
  pengaturan instance langsung di bagian itu.
- Pengaturan instance: Overview, Mods, Maps, Multiplayer, Controls. Mod dan map disimpan
  di folder instance sendiri (instances/<id>/mods dan /maps), terpisah antar instance.
- Mods dan Maps punya tombol sendiri: Import dari file, atau Download.
- Halaman download mod dan map: cari, lihat info, unduh dengan progres, pasang langsung
  ke instance. Sumber: idgames (map dan mod) dan GitHub (mod gameplay).
- Pencarian idgames mencoba beberapa host API, termasuk host cadangan, kalau host utama
  meminta verifikasi manusia.
- Tab Controls: Edit layout langsung dari launcher tanpa masuk game. Geser, ubah ukuran,
  tampilkan atau sembunyikan tombol. Perlu satu kali menjalankan game supaya engine
  membuat file layout-nya.

## 1.4.0 - changelog di dalam aplikasi
- Changelog tampil otomatis sekali setiap kali ada versi baru.
- Tombol Changelog di layar Instances menampilkan seluruh riwayat versi.
- Isinya diambil dari CHANGELOG.md yang sama dengan repo, disalin saat build.

## 1.3.0 - widget dihapus, layout touch tersimpan
- Perbaikan: layout dan pengaturan touch tidak pernah tersimpan karena folder
  `user_files/touch_layouts` tidak pernah dibuat (TinyXML tidak bisa membuat folder).
  Sekarang dibuat sebelum engine jalan.
- Preset layout sekarang membaca dan menulis folder yang benar.
- Widget layar utama dihapus.
- Build memberi penanda versi otomatis dan berhenti dengan pesan jelas kalau file
  overlay kurang atau usang.

## 1.2.0 - Controls dan tampilan
- Tab Controls menggantikan editor map: preset layout (simpan, terapkan, ekspor, impor).
- Navigasi adaptif: side rail di layar lebar, bar bawah di layar sempit.
- Animasi slime di kartu, tombol, dan ikon tab.
- Tombol mata global dihapus.

## 1.1.0 - Instances dan multiplayer
- Instance: mod, IWAD, argumen, config, dan save terpisah per instance.
- Multiplayer host/join per instance (argumen netgame bawaan engine).
- Pencarian mod menampilkan pesan jelas saat Doomworld meminta verifikasi.

## 1.0.0 - dasar
- Basis GZDoom-Android-2026 dengan overlay kita.
- Tombol Import mod (.wad, .pk3, .pk7, .deh, .bex).
- Archive idgames: fallback ke semua mirror, status koneksi, tombol Retry.
- Layar utama landscape, kontrol touch tampil secara default.
- Ikon mata per baris di daftar kontrol.
