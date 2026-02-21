from __future__ import annotations

import time
from collections.abc import Iterator

from vibe_haptic_designer.domain.models import HapticCommand, ImuSample
from vibe_haptic_designer.domain.ports import ControllerPort


class ControllerSession:
    def __init__(self, controller: ControllerPort) -> None:
        self._controller = controller

    def connect(self) -> None:
        self._controller.connect()

    def disconnect(self) -> None:
        self._controller.disconnect()

    def vibrate(self, command: HapticCommand) -> None:
        self._controller.set_vibration(command.left_motor, command.right_motor)
        time.sleep(max(0.0, command.seconds))
        self._controller.stop_vibration()

    def stream_imu(self, hz: int) -> Iterator[ImuSample]:
        interval = 1.0 / max(1, hz)
        while True:
            yield self._controller.read_imu()
            time.sleep(interval)
