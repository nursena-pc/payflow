package com.nursena.payflowtest.configuration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityProbeController {

    private final ProbeService probeService;

    public SecurityProbeController(
        ProbeService probeService
    ) {
        this.probeService = probeService;
    }

    @GetMapping("/api/v1/operations/probe")
    public String operationsProbe() {
        probeService.operationsAccessed();
        return "operations";
    }

    @GetMapping("/api/v1/users/me")
    public String customerProbe() {
        probeService.customerAccessed();
        return "customer";
    }

    @GetMapping("/api/v1/internal/probe")
    public String unknownProbe() {
        probeService.unknownAccessed();
        return "unknown";
    }

    public interface ProbeService {

        void operationsAccessed();

        void customerAccessed();

        void unknownAccessed();
    }
}
