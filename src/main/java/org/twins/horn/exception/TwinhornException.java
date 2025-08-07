package org.twins.horn.exception;

/**
 * Custom runtime exception providing consistent error handling across the
 * Twinhorn application. Each instance carries a {@link TwinhornErrorType} describing the
 * failure category plus optional message and/or underlying cause.
 */
public class TwinhornException extends RuntimeException {

    private final TwinhornErrorType errorType;

    public TwinhornException(TwinhornErrorType errorType) {
        super(errorType.getName());
        this.errorType = errorType;
    }

    public TwinhornException(TwinhornErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public TwinhornException(TwinhornErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public TwinhornException(TwinhornErrorType errorType, Throwable cause) {
        super(errorType.getName(), cause);
        this.errorType = errorType;
    }

    public TwinhornErrorType getErrorType() {
        return errorType;
    }

    /**
     * Enumerated error reasons recognised by the service.
     */
    public enum TwinhornErrorType {
        GENERAL_ERROR(1000, "GENERAL_ERROR", "Общая ошибка"),
        INPUT_DATA_ERROR(1001, "INPUT_DATA_ERROR", "Ошибка входных данных"),
        UNAUTHORIZED(1002, "UNAUTHORIZED", "Отсутствует авторизация"),
        RABBITMQ_CONNECTION_ERROR(1003, "RABBITMQ_CONNECTION_ERROR", "Ошибка соединения с RabbitMQ"),
        INTROSPECT_SERVICE_CONNECTION_ERROR(1004, "INTROSPECT_SERVICE_CONNECTION_ERROR", "Ошибка соединения с сервисом интроспекции"),
        STREAMING_PROCESSING_ERROR(1005, "STREAMING_PROCESSING_ERROR", "Ошибка обработки gRPC-потока"),
        DB_DATA_PROCESSING_ERROR(1006, "DB_DATA_PROCESSING_ERROR", "Ошибка работы с данными в БД"),;

        private final int errorCode;
        private final String name;
        private final String localizedName;

        TwinhornErrorType(int errorCode, String name, String localizedName) {
            this.errorCode = errorCode;
            this.name = name;
            this.localizedName = localizedName;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public String getName() {
            return name;
        }

        public String getLocalizedName() {
            return localizedName;
        }
    }
}
