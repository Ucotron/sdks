"""Error types for Ucotron SDK."""

from __future__ import annotations


class UcotronError(Exception):
    """Base exception for all Ucotron SDK errors."""


class UcotronServerError(UcotronError):
    """Raised when the server returns a 4xx or 5xx response."""

    def __init__(self, status: int, message: str, code: str = ""):
        self.status = status
        self.code = code
        super().__init__(f"Server error {status}: {message}")


class UcotronConnectionError(UcotronError):
    """Raised when the SDK cannot connect to the server."""

    def __init__(self, message: str, cause: Exception | None = None):
        self.cause = cause
        super().__init__(message)


class UcotronRetriesExhaustedError(UcotronError):
    """Raised when all retry attempts have been exhausted."""

    def __init__(self, attempts: int, last_error: Exception):
        self.attempts = attempts
        self.last_error = last_error
        super().__init__(
            f"All {attempts} retry attempts exhausted. Last error: {last_error}"
        )
