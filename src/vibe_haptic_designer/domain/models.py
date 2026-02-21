from dataclasses import dataclass


@dataclass(slots=True)
class HapticCommand:
    left_motor: int
    right_motor: int
    seconds: float


@dataclass(slots=True)
class ImuSample:
    accel_x: float
    accel_y: float
    accel_z: float
    gyro_x: float
    gyro_y: float
    gyro_z: float
