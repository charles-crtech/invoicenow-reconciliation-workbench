package io.github.charlescrtech.invoicenow.shared.health;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicHealthController {

    private static final PublicHealthResponse RESPONSE =
            new PublicHealthResponse("UP", "invoicenow-workbench-api");

    @GetMapping("/public")
    PublicHealthResponse health() {
        return RESPONSE;
    }

    record PublicHealthResponse(String status, String service) {
    }
}
