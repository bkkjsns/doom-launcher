use std::{fs::File, io::Read, path::{Path, PathBuf}};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WadKind {
    Iwad,
    Pwad,
}

/// Membaca 12 byte header WAD. Mengembalikan None jika bukan WAD.
pub fn read_kind(path: &Path) -> std::io::Result<Option<WadKind>> {
    let mut f = File::open(path)?;
    let mut hdr = [0u8; 4];
    if f.read_exact(&mut hdr).is_err() {
        return Ok(None);
    }
    Ok(match &hdr {
        b"IWAD" => Some(WadKind::Iwad),
        b"PWAD" => Some(WadKind::Pwad),
        _ => None,
    })
}

/// Memindai satu folder tanpa rekursi.
/// Hasil: (path, jenis). Jenis None untuk PK3 atau file yang tidak dikenali.
pub fn scan_dir(dir: &Path) -> std::io::Result<Vec<(PathBuf, Option<WadKind>)>> {
    let mut out = Vec::new();
    for entry in std::fs::read_dir(dir)? {
        let path = entry?.path();
        if !path.is_file() {
            continue;
        }
        let ext = path
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| e.to_ascii_lowercase());
        match ext.as_deref() {
            Some("wad") => {
                let kind = read_kind(&path)?;
                out.push((path, kind));
            }
            Some("pk3") | Some("pk7") => out.push((path, None)),
            _ => {}
        }
    }
    out.sort_by(|a, b| a.0.cmp(&b.0));
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn kenali_iwad_dan_pwad() {
        let dir = std::env::temp_dir().join("doom_core_test");
        std::fs::create_dir_all(&dir).unwrap();

        let mut f = File::create(dir.join("a.wad")).unwrap();
        f.write_all(b"IWAD").unwrap();
        drop(f);

        let mut f = File::create(dir.join("b.wad")).unwrap();
        f.write_all(b"PWAD").unwrap();
        drop(f);

        let items = scan_dir(&dir).unwrap();
        assert_eq!(items.len(), 2);
        assert_eq!(items[0].1, Some(WadKind::Iwad));
        assert_eq!(items[1].1, Some(WadKind::Pwad));

        let _ = std::fs::remove_dir_all(&dir);
    }
}