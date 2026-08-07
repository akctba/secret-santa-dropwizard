package dev.secretsanta;

import dev.secretsanta.auth.AuthFilter;
import dev.secretsanta.auth.AuthService;
import dev.secretsanta.core.ApiExceptionMapper;
import dev.secretsanta.core.SecretSantaConfiguration;
import dev.secretsanta.repository.InMemorySecretSantaRepository;
import dev.secretsanta.resource.GroupResource;
import dev.secretsanta.resource.UserResource;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Environment;

public final class SecretSantaApplication extends Application<SecretSantaConfiguration> {
    public static void main(String[] args) throws Exception {
        new SecretSantaApplication().run(args.length == 0
                ? new String[]{"server", "config.yml"}
                : args);
    }

    @Override
    public void run(SecretSantaConfiguration configuration, Environment environment) {
        var repository = new InMemorySecretSantaRepository();
        var authService = new AuthService(configuration.getAuth());

        environment.jersey().register(new UserResource(repository, authService));
        environment.jersey().register(new GroupResource(repository));
        environment.jersey().register(new AuthFilter(authService));
        environment.jersey().register(new ApiExceptionMapper());
    }
}
