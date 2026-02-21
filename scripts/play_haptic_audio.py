from __future__ import annotations

import argparse
import math
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
        "--wav-dir",
        type=Path,
        required=True,
        help="haptic_audio.wav を保存したディレクトリ",
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


def main() -> None:
    args = _build_parser().parse_args()
    wav_path = args.wav_dir / args.filename

    if not wav_path.exists():
        raise FileNotFoundError(f"WAVファイルが見つかりません: {wav_path}")

    adapter = PyDualSenseAdapter()
    session = ControllerSession(adapter)

    with wave.open(str(wav_path), "rb") as wav_file:
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


if __name__ == "__main__":
    main()
