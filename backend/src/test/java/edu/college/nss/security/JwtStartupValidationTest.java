package edu.college.nss.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtStartupValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(JwtTokenProvider.class);

    @Test
    void whenJwtSecretMissing_contextFailsToStart() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }

    @Test
    void whenJwtSecretTooShort_contextFailsToStart() {
        contextRunner
            .withPropertyValues("app.jwt.secret=too-short")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("JWT secret key must be configured and at least 32 characters (256 bits) long.");
            });
    }

    @Test
    void whenJwtSecretValid_contextStartsSuccessfully() {
        contextRunner
            .withPropertyValues("app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(JwtTokenProvider.class);
            });
    }
}
