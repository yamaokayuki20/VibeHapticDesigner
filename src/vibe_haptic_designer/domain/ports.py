from __future__ import annotations

from abc import ABC, abstractmethod

from .models import ImuSample


class ControllerPort(ABC):
    @abstractmethod
    def connect(self) -> None:
        ...

    @abstractmethod
    def disconnect(self) -> None:
        ...

    @abstractmethod
    def set_vibration(self, left_motor: int, right_motor: int) -> None:
        ...

    @abstractmethod
    def stop_vibration(self) -> None:
        ...

    @abstractmethod
    def read_imu(self) -> ImuSample:
        ...
