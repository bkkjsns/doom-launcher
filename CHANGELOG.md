# Doom Launcher - changelog

Versi kita sendiri. Nomor ada di file `VERSION`; tiap build menampilkannya sebagai
`<VERSION>-r<nomor run>` di Settings > About dan di header layar Instances.
Catatan: label 1.0.0 sampai 1.2.0 diberikan belakangan, jadi build lama masih
menampilkan 0.50.0.

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
