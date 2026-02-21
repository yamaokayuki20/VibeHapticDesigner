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

### macOS / Linux

```bash
bash ./scripts/bootstrap.sh
```

## 主要コマンド

### 振動テスト

```bash
uv run vibe vibrate --left 180 --right 220 --seconds 1.0
```

### IMU（加速度/ジャイロ）表示

```bash
uv run vibe stream-imu --hz 30
```

### 音声ファイルから振動再生

`haptic_audio.wav` を `data/haptic_audio/` に置いて実行:

```bash
uv run python play_haptic_audio.py --wav-dir data/haptic_audio
```

オプション:

```bash
uv run python play_haptic_audio.py --wav-dir data/haptic_audio --filename haptic_audio.wav --gain 1.2 --hz 80
```

## よくあるエラー

### `can't open file ... play_haptic_audio.py`

ルート実行であれば次を使ってください。

```bash
uv run python play_haptic_audio.py --wav-dir data/haptic_audio
```

### `wave.Error: file does not start with RIFF id`

`haptic_audio.wav` の中身がWAV形式ではありません（拡張子だけ `.wav` のケース）。  
RIFF/WAVE の正しいPCM WAVに変換してから再実行してください。

## WAV変換手順（ffmpegあり/なし）

### ffmpeg あり

1. 入力ファイル（例: `input.webm`）を用意
2. 以下を実行（16bit PCM / 44.1kHz / モノラル）

```bash
ffmpeg -i input.webm -ac 1 -ar 44100 -c:a pcm_s16le data/haptic_audio/haptic_audio.wav
```

3. 再生確認

```bash
uv run python play_haptic_audio.py --wav-dir data/haptic_audio
```

### ffmpeg なし

`play_haptic_audio.py` は標準ライブラリ `wave` を使うため、入力は **RIFF/WAVE (PCM)** が必須です。  
`webm/mp3/m4a` などはそのまま読めません。

- 方法A: Audacity で開いて `WAV (Microsoft) signed 16-bit PCM` として書き出し
- 方法B: VLC などのGUIツールで `WAV` に変換
- 変換後ファイル名を `data/haptic_audio/haptic_audio.wav` にして実行

```bash
uv run python play_haptic_audio.py --wav-dir data/haptic_audio
```

## 構成

- アプリ本体: [src/vibe_haptic_designer](src/vibe_haptic_designer)
- オーディオ再生スクリプト: [scripts/play_haptic_audio.py](scripts/play_haptic_audio.py)
- ルート実行ラッパー: [play_haptic_audio.py](play_haptic_audio.py)
- WAV保存先: [data/haptic_audio](data/haptic_audio)
- 外部依存サブモジュール: [third_party/pydualsense](third_party/pydualsense)
