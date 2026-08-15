package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015AccountActionAbuseProtectionContractTest {

    private static final Path MAIN = Path.of(
        "src", "main", "java", "com", "nursena", "payflow"
    );

    @Test
    void shouldResolveTrustedClientAtBothHttpBoundaries()
        throws IOException {

        assertThat(source(
            "user/adapter/in/web/EmailVerificationController.java"
        )).contains(
            "ClientAddressResolver",
            "clientAddressResolver.resolve(servletRequest)",
            "clientAddress.address()"
        );
        assertThat(source(
            "user/adapter/in/web/PasswordRecoveryController.java"
        )).contains(
            "ClientAddressResolver",
            "clientAddressResolver.resolve(servletRequest)",
            "clientAddress.address()"
        );
    }

    @Test
    void shouldEvaluateBeforeEligibilityAndSuppressBlockedWork()
        throws IOException {

        for (String service : new String[] {
            "RequestEmailVerificationService.java",
            "RequestPasswordRecoveryService.java"
        }) {
            String content = source(
                "user/application/service/" + service
            );
            assertThat(content).contains(
                "AbuseProtectionEnforcementPort",
                "AbuseProtectionRequest",
                "decision.isAllowed()",
                "catch (AbuseProtectionUnavailableException"
            );
            assertThat(content.indexOf("if (!isAllowed("))
                .isLessThan(
                    content.indexOf("findByEmailForUpdate")
                );
        }
    }

    @Test
    void shouldRecordCompletedIncrementAndRegistrationDeferral()
        throws IOException {

        String roadmap = Files.readString(
            Path.of("docs", "roadmap.md")
        );
        String guide = Files.readString(
            Path.of("docs", "abuse-protection.md")
        );
        String threatModel = Files.readString(
            Path.of(
                "docs", "security",
                "abuse-protection-threat-model.md"
            )
        );

        assertThat(roadmap).contains(
            "Increment 3 is implemented by issue [#155]",
            "- [x] Protect email-verification requests",
            "- [x] Protect password-recovery requests",
            "- [x] Evaluate registration protection",
            "- [x] Verify concurrent requests"
        );
        assertThat(guide).contains(
            "empty `202 Accepted` response",
            "Registration was evaluated but is not wired"
        );
        assertThat(threatModel).contains(
            "Real-Redis HTTP concurrency tests",
            "Registration protection remains deferred"
        );
    }

    private static String source(String relative)
        throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}