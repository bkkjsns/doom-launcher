use doom_core::wad::{scan_dir, WadKind};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::path::Path;

/// Dipanggil dari Kotlin: NativeBridge.scanWads(dir)
/// Mengembalikan baris "TIPE|path", satu baris per file.
#[no_mangle]
pub extern "system" fn Java_com_example_doomlauncher_NativeBridge_scanWads(
    mut env: JNIEnv,
    _class: JClass,
    dir: JString,
) -> jstring {
    let dir: String = match env.get_string(&dir) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let text = match scan_dir(Path::new(&dir)) {
        Ok(items) => items
            .iter()
            .map(|(path, kind)| {
                let tag = match kind {
                    Some(WadKind::Iwad) => "IWAD",
                    Some(WadKind::Pwad) => "PWAD",
                    None => "PK3",
                };
                format!("{}|{}", tag, path.display())
            })
            .collect::<Vec<_>>()
            .join("\n"),
        Err(e) => format!("ERROR|{}", e),
    };

    match env.new_string(text) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}