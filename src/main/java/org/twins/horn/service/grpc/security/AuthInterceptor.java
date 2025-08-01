package org.twins.horn.service.grpc.security;

import io.grpc.*;
import org.twins.horn.service.auth.TwinsTokenIntrospectService;
import org.twins.horn.service.auth.dto.TokenIntrospectRsDTOv1;

/**
 * gRPC {@link io.grpc.ServerInterceptor} that validates incoming OAuth2 Bearer tokens.
 * <p>
 * Each call is inspected for an <code>Authorization: Bearer &lt;token&gt;</code> metadata entry.
 * The token is verified through {@link TwinsTokenIntrospectService}.
 * If the token is absent
 * or invalid the interceptor terminates the call with {@link Status#UNAUTHENTICATED}.
 * <p>
 * On successful validation the resulting {@link TokenIntrospectRsDTOv1 token information}
 * is stored in the gRPC {@link Context} using {@link #TOKEN_INFO_CTX_KEY}.  Down-stream
 * service implementations can retrieve the data without performing additional network
 * round-trips to the introspection endpoint.
 */
public class AuthInterceptor implements ServerInterceptor {

        /**
     * Metadata key containing the <code>Authorization</code> header.  Lower-case is used because
     * gRPC metadata keys are ASCII and case-insensitive by specification.
     */
    private static final Metadata.Key<String> AUTH_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Context.Key<TokenIntrospectRsDTOv1> TOKEN_INFO_CTX_KEY =
            Context.key("token-info");

    private final TwinsTokenIntrospectService twinsTokenIntrospectService;

    public AuthInterceptor() {

        //this.expectedToken = System.getProperty("grpc.auth.valid-token", "test-token");
        twinsTokenIntrospectService = new TwinsTokenIntrospectService();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String authHeader = headers.get(AUTH_HEADER);
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing bearer token"), new Metadata());
            return new ServerCall.Listener<>() {
            }; // empty listener
        }
        String token = authHeader.substring(7);
        TokenIntrospectRsDTOv1 introspectRsDTOv1 = twinsTokenIntrospectService.validateToken(token);
        if (introspectRsDTOv1 == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        Context ctxWithToken = Context.current().withValue(TOKEN_INFO_CTX_KEY, introspectRsDTOv1);

      //  return next.startCall(call, headers);
        return Contexts.interceptCall(ctxWithToken, call, headers, next);

    }
}
