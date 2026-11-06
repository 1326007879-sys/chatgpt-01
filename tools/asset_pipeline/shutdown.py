"""CLI conversion has no desktop GUI shutdown latch; process signals stop the build."""
def session_ending() -> bool:
    return False
