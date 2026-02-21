from __future__ import annotations

import argparse
from dataclasses import dataclass

from vibe_haptic_designer.adapters.pydualsense_adapter import PyDualSenseAdapter
from vibe_haptic_designer.application.session import ControllerSession


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="PCで再生中の音声をループバック取得し、DualSense振動へ反映します"
    )
    parser.add_argument(
        "--device",
        type=str,
        default="",
        help="録音元スピーカー名の部分一致 (未指定なら既定スピーカー)",
    )
    parser.add_argument(
        "--samplerate",
        type=int,
        default=48000,
        help="ループバック取得サンプルレート (default: 48000)",
    )
    parser.add_argument(
        "--block-ms",
        type=float,
        default=20.0,
        help="1更新あたりの取得時間[ms] (default: 20)",
    )
    parser.add_argument(
        "--gain",
        type=float,
        default=2.0,
        help="振幅倍率 (default: 2.0)",
    )
    parser.add_argument(
        "--noise-gate",
        type=float,
        default=0.02,
        help="この値未満のRMSは無音扱い (0.0-0.9, default: 0.02)",
    )
    parser.add_argument(
        "--stereo-split",
        action="store_true",
        help="L/Rチャンネルを左右モーターへ別々に割り当てる",
    )
    return parser


@dataclass
class _Levels:
    left: int
    right: int


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def _to_motor(rms: float, gain: float, noise_gate: float) -> int:
    norm = _clamp01((rms - noise_gate) / max(1e-6, (1.0 - noise_gate)))
    value = int(_clamp01(norm * gain) * 255.0)
    return value


def _pick_loopback_microphone(sc, device_hint: str):
    speaker = None

    if device_hint:
        hint = device_hint.lower()
        for candidate in sc.all_speakers():
            if hint in str(candidate.name).lower():
                speaker = candidate
                break
        if speaker is None:
            raise RuntimeError(
                f"指定名に一致するスピーカーが見つかりません: {device_hint}"
            )
    else:
        speaker = sc.default_speaker()

    if speaker is None:
        raise RuntimeError("既定スピーカーが見つかりません")

    loopback = sc.get_microphone(id=str(speaker.name), include_loopback=True)
    if loopback is None:
        raise RuntimeError(
            "ループバック入力が取得できません。Windows WASAPI環境で実行してください"
        )

    return speaker, loopback


def _compute_levels(np, block, gain: float, noise_gate: float, stereo_split: bool) -> _Levels:
    if block.size == 0:
        return _Levels(0, 0)

    if block.ndim == 1:
        mono_rms = float(np.sqrt(np.mean(np.square(block, dtype=np.float64))))
        motor = _to_motor(mono_rms, gain, noise_gate)
        return _Levels(motor, motor)

    if stereo_split and block.shape[1] >= 2:
        left_rms = float(np.sqrt(np.mean(np.square(block[:, 0], dtype=np.float64))))
        right_rms = float(np.sqrt(np.mean(np.square(block[:, 1], dtype=np.float64))))
        return _Levels(
            _to_motor(left_rms, gain, noise_gate),
            _to_motor(right_rms, gain, noise_gate),
        )

    mono = block.mean(axis=1)
    mono_rms = float(np.sqrt(np.mean(np.square(mono, dtype=np.float64))))
    motor = _to_motor(mono_rms, gain, noise_gate)
    return _Levels(motor, motor)


def main() -> None:
    args = _build_parser().parse_args()

    if args.block_ms <= 0:
        raise ValueError("--block-ms は 0 より大きい値を指定してください")
    if not 0.0 <= args.noise_gate < 1.0:
        raise ValueError("--noise-gate は 0.0 以上 1.0 未満で指定してください")

    try:
        import numpy as np
        import soundcard as sc
    except Exception as exc:
        raise RuntimeError(
            "numpy / soundcard が必要です。`uv sync --extra live-audio` を実行してください"
        ) from exc

    block_frames = max(1, int(args.samplerate * (args.block_ms / 1000.0)))
    speaker, loopback = _pick_loopback_microphone(sc, args.device)

    adapter = PyDualSenseAdapter()
    session = ControllerSession(adapter)

    print(f"loopback source: {speaker.name}")
    print("Ctrl+C で終了します")

    try:
        session.connect()
        with loopback.recorder(
            samplerate=args.samplerate,
            channels=2,
            blocksize=block_frames,
        ) as recorder:
            while True:
                block = recorder.record(numframes=block_frames)
                levels = _compute_levels(
                    np=np,
                    block=block,
                    gain=args.gain,
                    noise_gate=args.noise_gate,
                    stereo_split=args.stereo_split,
                )
                adapter.set_vibration(levels.left, levels.right)
    except KeyboardInterrupt:
        pass
    finally:
        adapter.stop_vibration()
        session.disconnect()


if __name__ == "__main__":
    main()
