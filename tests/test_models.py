from vibe_haptic_designer.domain.models import HapticCommand


def test_haptic_command_init() -> None:
    cmd = HapticCommand(left_motor=10, right_motor=20, seconds=0.5)
    assert cmd.left_motor == 10
    assert cmd.right_motor == 20
    assert cmd.seconds == 0.5
