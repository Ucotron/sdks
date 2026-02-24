package ucotron

import "fmt"

// UcotronServerError represents a 4xx/5xx HTTP error from the server.
type UcotronServerError struct {
	StatusCode int
	Code       string
	Message    string
}

func (e *UcotronServerError) Error() string {
	return fmt.Sprintf("ucotron server error %d (%s): %s", e.StatusCode, e.Code, e.Message)
}

// UcotronConnectionError represents a network/connection failure.
type UcotronConnectionError struct {
	Message string
	Cause   error
}

func (e *UcotronConnectionError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("ucotron connection error: %s: %v", e.Message, e.Cause)
	}
	return fmt.Sprintf("ucotron connection error: %s", e.Message)
}

func (e *UcotronConnectionError) Unwrap() error {
	return e.Cause
}

// UcotronRetriesExhaustedError is returned when all retry attempts fail.
type UcotronRetriesExhaustedError struct {
	Attempts  int
	LastError error
}

func (e *UcotronRetriesExhaustedError) Error() string {
	return fmt.Sprintf("ucotron retries exhausted after %d attempts: %v", e.Attempts, e.LastError)
}

func (e *UcotronRetriesExhaustedError) Unwrap() error {
	return e.LastError
}
