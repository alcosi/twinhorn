package org.twins.horn.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Utility to map internal application exceptions to gRPC Status codes so that
 * clients can react appropriately (retry vs.
 * fatal error). Keeps mapping in one place to stay consistent.
 */
public final class GrpcErrorMapper {

    private GrpcErrorMapper() {
    }

    public static StatusRuntimeException toStatus(Throwable ex) {
        if (ex instanceof org.twins.horn.exception.TwinhornException tEx) {
            if (tEx.getErrorType() == org.twins.horn.exception.TwinhornException.TwinhornErrorType.UNAUTHORIZED) {
                return Status.UNAUTHENTICATED
                        .withDescription(tEx.getMessage())
                        .asRuntimeException();
            }
            return Status.INVALID_ARGUMENT
                    .augmentDescription(tEx.getErrorType().getName())
                    .withDescription(tEx.getMessage())
                    .asRuntimeException();
        } else if (ex instanceof TwinMQTemporaryException) {
            return Status.UNAVAILABLE.withDescription(ex.getMessage()).asRuntimeException();
        } else if (ex instanceof TwinMQBusinessException) {
            return Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException();
        } else {
            return Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException();
        }
    }
}
