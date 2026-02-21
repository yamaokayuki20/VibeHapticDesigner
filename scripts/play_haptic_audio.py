from __future__ import annotations

import argparse
import math
import os
import shutil
import subprocess
import tempfile
import time
import wave
from pathlib import Path

from vibe_haptic_designer.adapters.pydualsense_adapter import PyDualSenseAdapter
from vibe_haptic_designer.application.session import ControllerSession


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="haptic_audio.wav を読み込み、振幅をDualSense振動として再生します"
    )
    parser.add_argument(
        "--wav-path",
        type=Path,
        help="再生するWAVファイルのパス（任意の .wav を指定可能）",
    )
    parser.add_argument(
        "--wav-dir",
        type=Path,
        help="WAVを保存したディレクトリ（互換オプション）",
    )
    parser.add_argument(
        "--filename",
        type=str,
        default="haptic_audio.wav",
        help="再生するWAVファイル名 (default: haptic_audio.wav)",
    )
    parser.add_argument(
        "--hz",
        type=int,
        default=60,
        help="振動更新レート (default: 60)",
    )
    parser.add_argument(
        "--gain",
        type=float,
        default=1.0,
        help="振幅倍率 (default: 1.0)",
    )
    return parser


def _resolve_wav_path(args: argparse.Namespace) -> Path:
    if args.wav_path is not None:
        return args.wav_path

    if args.wav_dir is not None:
        if args.wav_dir.suffix.lower() == ".wav":
            return args.wav_dir
        return args.wav_dir / args.filename

    return Path("data/haptic_audio/haptic_audio.wav")


def _sample_to_normalized(sample_bytes: bytes, sample_width: int) -> float:
    if sample_width == 1:
        value = sample_bytes[0] - 128
        return max(-1.0, min(1.0, value / 127.0))

    max_int = float((1 << (8 * sample_width - 1)) - 1)
    value = int.from_bytes(sample_bytes, byteorder="little", signed=True)
    return max(-1.0, min(1.0, value / max_int))


def _chunk_rms(chunk: bytes, sample_width: int) -> float:
    if not chunk:
        return 0.0

    sample_count = len(chunk) // sample_width
    if sample_count == 0:
        return 0.0

    squared = 0.0
    for i in range(0, len(chunk), sample_width):
        normalized = _sample_to_normalized(chunk[i : i + sample_width], sample_width)
        squared += normalized * normalized

    return math.sqrt(squared / sample_count)


def _to_motor_value(rms: float, gain: float) -> int:
    scaled = rms * gain * 255.0
    return max(0, min(255, int(scaled)))


def _resolve_ffmpeg_executable() -> str | None:
    from_path = shutil.which("ffmpeg")
    if from_path:
        return from_path

    try:
        import imageio_ffmpeg  # type: ignore

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        pass

    local_app_data = os.environ.get("LOCALAPPDATA")
    if not local_app_data:
        return None

    winget_packages = Path(local_app_data) / "Microsoft" / "WinGet" / "Packages"
    if not winget_packages.exists():
        return None

    matches = sorted(winget_packages.glob("**/ffmpeg.exe"))
    if not matches:
        return None

    return str(matches[0])


def _convert_with_ffmpeg(src_path: Path) -> tuple[Path, Path]:
    temp_dir = Path(tempfile.mkdtemp(prefix="vhd_audio_"))
    converted_path = temp_dir / "converted.wav"
    ffmpeg_executable = _resolve_ffmpeg_executable()
    if ffmpeg_executable is None:
        shutil.rmtree(temp_dir, ignore_errors=True)
        raise RuntimeError(
            "入力がRIFF/WAVではなく自動変換が必要ですが、ffmpeg が見つかりません。"
        )

    command = [
        ffmpeg_executable,
        "-y",
        "-i",
        str(src_path),
        "-ac",
        "1",
        "-ar",
        "44100",
        "-c:a",
        "pcm_s16le",
        str(converted_path),
    ]

    try:
        subprocess.run(command, check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as exc:
        shutil.rmtree(temp_dir, ignore_errors=True)
        stderr = (exc.stderr or "").strip()
        raise RuntimeError(f"ffmpeg 変換に失敗しました: {stderr}") from exc

    return converted_path, temp_dir


def main() -> None:
    args = _build_parser().parse_args()
    wav_path = _resolve_wav_path(args)

    if not wav_path.exists():
        raise FileNotFoundError(f"WAVファイルが見つかりません: {wav_path}")

    playback_path = wav_path
    temp_dir: Path | None = None

    try:
        with wave.open(str(playback_path), "rb"):
            pass
    except wave.Error:
        playback_path, temp_dir = _convert_with_ffmpeg(wav_path)
        print(f"非RIFF入力を検出したため ffmpeg で変換して再生します: {wav_path}")

    adapter = PyDualSenseAdapter()
    session = ControllerSession(adapter)

    try:
        with wave.open(str(playback_path), "rb") as wav_file:
            sample_width = wav_file.getsampwidth()
            frame_rate = wav_file.getframerate()

            if sample_width not in (1, 2, 3, 4):
                raise ValueError(f"未対応のサンプル幅です: {sample_width}")

            updates_per_sec = max(1, args.hz)
            frames_per_update = max(1, frame_rate // updates_per_sec)
            seconds_per_update = frames_per_update / frame_rate

            try:
                session.connect()
                while True:
                    chunk = wav_file.readframes(frames_per_update)
                    if not chunk:
                        break

                    rms = _chunk_rms(chunk, sample_width)
                    motor = _to_motor_value(rms, args.gain)
                    adapter.set_vibration(motor, motor)
                    time.sleep(seconds_per_update)
            finally:
                adapter.stop_vibration()
                session.disconnect()
    finally:
        if temp_dir is not None:
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
