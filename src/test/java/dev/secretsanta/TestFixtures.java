package dev.secretsanta;

import dev.secretsanta.auth.AuthService;
import dev.secretsanta.core.SecretSantaConfiguration.AuthConfiguration;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class TestFixtures {
    private TestFixtures() {}

    public static AuthService authService() {
        var configuration = new AuthConfiguration();
        configuration.setIssuer("test-issuer");
        configuration.setSecret("test-secret");
        return new AuthService(configuration);
    }

    public static ContainerRequestContext requestContext(
            String path, String authorization, Map<String, Object> properties,
            AtomicReference<Response> abortedResponse) {
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(), new Class<?>[]{UriInfo.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPath")) return path;
                    throw new UnsupportedOperationException(method.getName());
                });
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUriInfo" -> uriInfo;
                    case "getHeaderString" -> "Authorization".equals(args[0]) ? authorization : null;
                    case "setProperty" -> {
                        properties.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getProperty" -> properties.get(args[0]);
                    case "abortWith" -> {
                        abortedResponse.set((Response) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    public static ContainerRequestContext requestContext(String path, String authorization) {
        return requestContext(path, authorization, new HashMap<>(), new AtomicReference<>());
    }
}
