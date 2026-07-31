package com.nursena.payflow.clientcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.nursena.payflow.clientcontext.domain.ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain.ClientAddressSource;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ServletClientAddressResolverObservationTest {

    @Test
    void shouldObserveResolvedForwardedDecision() {
        RecordingObserver observer =
            new RecordingObserver();

        ServletClientAddressResolver resolver =
            resolver(observer);

        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "Forwarded",
            "for=203.0.113.9"
        );

        resolver.resolve(request);

        assertThat(observer.source)
            .isEqualTo(
                ClientAddressSource.FORWARDED
            );

        assertThat(observer.outcome)
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .RESOLVED
            );
    }

    @Test
    void shouldObserveFallbackWithoutReceivingAddress() {
        RecordingObserver observer =
            new RecordingObserver();

        ServletClientAddressResolver resolver =
            resolver(observer);

        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "Forwarded",
            "for=unknown"
        );

        resolver.resolve(request);

        assertThat(observer.source)
            .isEqualTo(
                ClientAddressSource.FORWARDED
            );

        assertThat(observer.outcome)
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .MALFORMED_HEADER
            );
    }

    private static ServletClientAddressResolver resolver(
        ClientAddressResolutionObserver observer
    ) {
        return new ServletClientAddressResolver(
            new TrustedProxyProperties(
                List.of(
                    "10.0.0.0/8"
                ),
                256,
                4
            ),
            observer
        );
    }

    private static MockHttpServletRequest request(
        String remoteAddress
    ) {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.setRemoteAddr(remoteAddress);

        return request;
    }

    private static final class RecordingObserver
        implements ClientAddressResolutionObserver {

        private ClientAddressSource source;
        private ClientAddressResolutionOutcome outcome;

        @Override
        public void record(
            ClientAddressSource source,
            ClientAddressResolutionOutcome outcome
        ) {
            this.source =
                source;

            this.outcome =
                outcome;
        }
    }
}
