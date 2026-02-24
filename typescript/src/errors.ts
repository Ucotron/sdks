/**
 * Base error class for Ucotron SDK errors.
 */
export class UcotronError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "UcotronError";
  }
}

/**
 * Thrown when the server responds with a non-2xx status code.
 */
export class UcotronServerError extends UcotronError {
  public readonly status: number;
  public readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(`[${status}] ${code}: ${message}`);
    this.name = "UcotronServerError";
    this.status = status;
    this.code = code;
  }
}

/**
 * Thrown when a network or connection error occurs.
 */
export class UcotronConnectionError extends UcotronError {
  public readonly cause?: Error;

  constructor(message: string, cause?: Error) {
    super(message);
    this.name = "UcotronConnectionError";
    this.cause = cause;
  }
}

/**
 * Thrown when all retry attempts are exhausted.
 */
export class UcotronRetriesExhaustedError extends UcotronError {
  public readonly attempts: number;
  public readonly lastError: Error;

  constructor(attempts: number, lastError: Error) {
    super(
      `All ${attempts} retry attempts exhausted. Last error: ${lastError.message}`
    );
    this.name = "UcotronRetriesExhaustedError";
    this.attempts = attempts;
    this.lastError = lastError;
  }
}
