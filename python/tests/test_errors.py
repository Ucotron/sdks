"""Tests for error types and error hierarchy in Ucotron Python SDK."""

from ucotron_sdk.errors import (
    UcotronError,
    UcotronServerError,
    UcotronConnectionError,
    UcotronRetriesExhaustedError,
)


class TestUcotronError:
    def test_base_error(self):
        err = UcotronError("something went wrong")
        assert str(err) == "something went wrong"
        assert isinstance(err, Exception)

    def test_is_base_of_server_error(self):
        err = UcotronServerError(404, "Not found")
        assert isinstance(err, UcotronError)

    def test_is_base_of_connection_error(self):
        err = UcotronConnectionError("timeout")
        assert isinstance(err, UcotronError)

    def test_is_base_of_retries_error(self):
        cause = Exception("fail")
        err = UcotronRetriesExhaustedError(3, cause)
        assert isinstance(err, UcotronError)


class TestUcotronServerError:
    def test_status_and_message(self):
        err = UcotronServerError(400, "Bad request", code="VALIDATION")
        assert err.status == 400
        assert err.code == "VALIDATION"
        assert "400" in str(err)

    def test_404_not_found(self):
        err = UcotronServerError(404, "Not found")
        assert err.status == 404
        assert err.code == ""

    def test_500_server_error(self):
        err = UcotronServerError(500, "Internal error")
        assert err.status == 500


class TestUcotronConnectionError:
    def test_with_cause(self):
        original = ConnectionRefusedError("refused")
        err = UcotronConnectionError("Cannot connect", cause=original)
        assert err.cause is original
        assert "Cannot connect" in str(err)

    def test_without_cause(self):
        err = UcotronConnectionError("timeout")
        assert err.cause is None


class TestUcotronRetriesExhaustedError:
    def test_attempts_and_last_error(self):
        cause = UcotronServerError(503, "Unavailable")
        err = UcotronRetriesExhaustedError(3, cause)
        assert err.attempts == 3
        assert err.last_error is cause
        assert "3" in str(err)

    def test_single_attempt(self):
        cause = UcotronConnectionError("refused")
        err = UcotronRetriesExhaustedError(1, cause)
        assert err.attempts == 1
