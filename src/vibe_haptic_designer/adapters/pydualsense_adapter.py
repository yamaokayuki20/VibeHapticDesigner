from __future__ import annotations

from dataclasses import dataclass

from vibe_haptic_designer.domain.models import ImuSample
from vibe_haptic_designer.domain.ports import ControllerPort


def _clamp_motor(value: int) -> int:
    return max(0, min(255, int(value)))


@dataclass
class PyDualSenseAdapter(ControllerPort):
    def __post_init__(self) -> None:
        try:
            from pydualsense import pydualsense  # type: ignore
        except Exception as exc:
            raise RuntimeError(
                "pydualsense の import に失敗しました。サブモジュール初期化後に uv sync を実行してください。"
            ) from exc

        self._controller = pydualsense()
        self._connected = False

    def connect(self) -> None:
        if not self._connected:
            self._controller.init()
            self._connected = True

    def disconnect(self) -> None:
        if self._connected:
            self.stop_vibration()
            close = getattr(self._controller, "close", None)
            if callable(close):
                close()
            self._connected = False

    def set_vibration(self, left_motor: int, right_motor: int) -> None:
        left = _clamp_motor(left_motor)
        right = _clamp_motor(right_motor)
        self._controller.setLeftMotor(left)
        self._controller.setRightMotor(right)

    def stop_vibration(self) -> None:
        self._controller.setLeftMotor(0)
        self._controller.setRightMotor(0)

    def read_imu(self) -> ImuSample:
        state = self._controller.state
        return ImuSample(
            accel_x=float(getattr(state, "accelerometerX", 0.0)),
            accel_y=float(getattr(state, "accelerometerY", 0.0)),
            accel_z=float(getattr(state, "accelerometerZ", 0.0)),
            gyro_x=float(getattr(state, "gyroscopeX", 0.0)),
            gyro_y=float(getattr(state, "gyroscopeY", 0.0)),
            gyro_z=float(getattr(state, "gyroscopeZ", 0.0)),
        )
