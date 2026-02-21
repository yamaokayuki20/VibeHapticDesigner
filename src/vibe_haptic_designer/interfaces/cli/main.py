from __future__ import annotations

import argparse
from datetime import datetime

from vibe_haptic_designer.adapters.pydualsense_adapter import PyDualSenseAdapter
from vibe_haptic_designer.application.session import ControllerSession
from vibe_haptic_designer.domain.models import HapticCommand


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="vibe")
    sub = parser.add_subparsers(dest="command", required=True)

    cmd_vibrate = sub.add_parser("vibrate", help="DualSenseを振動させる")
    cmd_vibrate.add_argument("--left", type=int, default=180)
    cmd_vibrate.add_argument("--right", type=int, default=180)
    cmd_vibrate.add_argument("--seconds", type=float, default=1.0)

    cmd_stream = sub.add_parser("stream-imu", help="IMU情報を標準出力へ表示")
    cmd_stream.add_argument("--hz", type=int, default=30)

    return parser


def main() -> None:
    parser = _build_parser()
    args = parser.parse_args()

    adapter = PyDualSenseAdapter()
    session = ControllerSession(adapter)

    try:
        session.connect()
        if args.command == "vibrate":
            session.vibrate(HapticCommand(args.left, args.right, args.seconds))
            print("vibration completed")
            return

        if args.command == "stream-imu":
            for sample in session.stream_imu(args.hz):
                ts = datetime.now().isoformat(timespec="milliseconds")
                print(
                    f"{ts} ax={sample.accel_x:.3f} ay={sample.accel_y:.3f} az={sample.accel_z:.3f} "
                    f"gx={sample.gyro_x:.3f} gy={sample.gyro_y:.3f} gz={sample.gyro_z:.3f}"
                )

    except KeyboardInterrupt:
        pass
    finally:
        session.disconnect()


if __name__ == "__main__":
    main()
