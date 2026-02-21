# VibeHapticDesigner

DualSense (PS5コントローラ) を Python から操作し、振動制御とセンサ取得を行うプロジェクトです。  
依存管理は `uv`、コントローラ制御は `pydualsense`（submodule）を使います。

## 必要環境

- Python 3.11+
- `uv`
- Git
- DualSense（USB/Bluetooth接続）

## セットアップ

### Windows

```powershell
./scripts/bootstrap.ps1
```

追加依存（リアルタイム音声 + ffmpeg同梱）を使う場合:

```powershell
uv sync --all-extras
```

### macOS / Linux

```bash
bash ./scripts/bootstrap.sh
```

追加依存（リアルタイム音声 + ffmpeg同梱）を使う場合:

```bash
uv sync --all-extras
```

## クイックスタート

### 振動テスト

```bash
uv run vibe vibrate --left 180 --right 220 --seconds 1.0
```

### IMU（加速度/ジャイロ）表示

```bash
uv run vibe stream-imu --hz 30
```

### WAVから振動再生

```bash
uv run python play_haptic_audio.py --wav-path data/haptic_audio/test1.wav
```

互換オプション（ディレクトリ指定）:

```bash
uv run python play_haptic_audio.py --wav-dir data/haptic_audio --filename test1.wav
```

### PC再生音をリアルタイム振動へ反映（MVP）

まず任意依存を導入:

```bash
uv sync --all-extras
```

実行:

```bash
uv run python play_system_audio_haptics.py
```

デバイス名を指定する場合:

```bash
uv run python play_system_audio_haptics.py --device "Realtek"
```

## WAV入力仕様

- `--wav-path` で任意ファイルを直接指定可能
- `--wav-dir` はディレクトリ指定 + `--filename` で解決
- 非RIFF/WAV入力（拡張子だけ `.wav` など）は、`uv sync --all-extras` 済みなら自動でPCM WAVへ変換して再生

## ffmpeg（任意）

`uv sync --all-extras` 済みなら、`imageio-ffmpeg` の同梱バイナリを自動利用します。

```bash
uv sync --all-extras
```

手動変換したい場合（16bit PCM / 44.1kHz / mono）:

```bash
ffmpeg -i input.webm -ac 1 -ar 44100 -c:a pcm_s16le data/haptic_audio/haptic_audio.wav
```

## トラブルシュート

### `can't open file ... play_haptic_audio.py`

ルートから次を実行:

```bash
uv run python play_haptic_audio.py --wav-path data/haptic_audio/test1.wav
```

### `wave.Error: file does not start with RIFF id`

- 入力がRIFF/WAVではありません
- `ffmpeg` が使える環境なら自動変換されます
- 自動変換に失敗する場合は `ffmpeg` の導入確認、または手動変換を実施してください

### `ffmpeg` / `ffprobe` が見つからない

- `uv sync --all-extras` を再実行
- それでも不可の場合は、`uv run python -c "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"` で同梱バイナリの取得を確認

### `numpy / soundcard が必要です`

- `uv sync --all-extras` を実行
- `uv` が `numpy<2` を自動選択します（互換性のため固定）
- Windowsでループバック取得できない場合は、再生デバイス変更やUSB接続で再試行

## 構成

- アプリ本体: [src/vibe_haptic_designer](src/vibe_haptic_designer)
- オーディオ再生スクリプト: [scripts/play_haptic_audio.py](scripts/play_haptic_audio.py)
- ルート実行ラッパー: [play_haptic_audio.py](play_haptic_audio.py)
- システム音ループバックスクリプト: [scripts/play_system_audio_haptics.py](scripts/play_system_audio_haptics.py)
- ループバック実行ラッパー: [play_system_audio_haptics.py](play_system_audio_haptics.py)
- WAV保存先: [data/haptic_audio](data/haptic_audio)
- 外部依存サブモジュール: [third_party/pydualsense](third_party/pydualsense)
