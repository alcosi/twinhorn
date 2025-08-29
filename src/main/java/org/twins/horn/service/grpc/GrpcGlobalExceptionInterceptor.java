package org.twins.horn.service.grpc;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.twins.horn.exception.GrpcErrorMapper;

/**
 * Global gRPC interceptor that catches any unchecked/declared exception thrown by
 * service implementations and converts it into a {@link StatusRuntimeException}
 * produced by {@link GrpcErrorMapper}. Ensures the client always receives a well
 * defined error without the stream hanging.
 */
@Slf4j
@Component
public class GrpcGlobalExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);

        return new SimpleForwardingServerCallListener<>(delegate) {

            private void handle(Throwable t) {
                log.error("Unhandled gRPC exception", t);
                StatusRuntimeException statusEx = GrpcErrorMapper.toStatus(t);
                call.close(statusEx.getStatus(), new Metadata());
            }

            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (Throwable t) {
                    handle(t);
                }
            }

            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Throwable t) {
                    handle(t);
                }
            }

            @Override
            public void onCancel() {
                try {
                    super.onCancel();
                } catch (Throwable t) {
                    handle(t);
                }
            }

            @Override
            public void onComplete() {
                try {
                    super.onComplete();
                } catch (Throwable t) {
                    handle(t);
                }
            }

            @Override
            public void onReady() {
                try {
                    super.onReady();
                } catch (Throwable t) {
                    handle(t);
                }
            }
        };
    }
}
