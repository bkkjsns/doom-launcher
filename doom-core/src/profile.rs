use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LaunchProfile {
    pub name: String,
    pub iwad: PathBuf,
    /// Urutan penting: mod yang paling bawah menimpa yang di atas.
    pub mods: Vec<PathBuf>,
    pub extra_args: Vec<String>,
}

impl LaunchProfile {
    /// Menyusun argumen untuk UZDoom, misalnya:
    /// -iwad DOOM1.WAD -file mod1.pk3 mod2.wad
    pub fn to_args(&self) -> Vec<String> {
        let mut args = vec!["-iwad".to_string(), self.iwad.display().to_string()];
        if !self.mods.is_empty() {
            args.push("-file".to_string());
            args.extend(self.mods.iter().map(|m| m.display().to_string()));
        }
        args.extend(self.extra_args.iter().cloned());
        args
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum Action {
    Key(u16),
    Hold(u16),
    Weapon(u8),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TouchButton {
    pub id: String,
    /// 0.0..1.0, relatif terhadap lebar layar
    pub x: f32,
    /// 0.0..1.0, relatif terhadap tinggi layar
    pub y: f32,
    /// Proporsi terhadap lebar layar
    pub size: f32,
    pub opacity: f32,
    pub action: Action,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct TouchLayout {
    pub name: String,
    pub buttons: Vec<TouchButton>,
}

impl TouchLayout {
    pub fn to_json(&self) -> serde_json::Result<String> {
        serde_json::to_string_pretty(self)
    }

    pub fn from_json(s: &str) -> serde_json::Result<Self> {
        serde_json::from_str(s)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn urutan_argumen_benar() {
        let p = LaunchProfile {
            name: "test".into(),
            iwad: "DOOM1.WAD".into(),
            mods: vec!["a.pk3".into(), "b.wad".into()],
            extra_args: vec!["-nomusic".into()],
        };
        let args = p.to_args();
        assert_eq!(args[0], "-iwad");
        assert!(args.contains(&"-file".to_string()));
        assert_eq!(args.last().unwrap(), "-nomusic");
    }

    #[test]
    fn layout_bolak_balik_json() {
        let layout = TouchLayout {
            name: "default".into(),
            buttons: vec![TouchButton {
                id: "fire".into(),
                x: 0.8,
                y: 0.7,
                size: 0.1,
                opacity: 0.8,
                action: Action::Key(29),
            }],
        };
        let json = layout.to_json().unwrap();
        let back = TouchLayout::from_json(&json).unwrap();
        assert_eq!(back.buttons[0].id, "fire");
    }
}
