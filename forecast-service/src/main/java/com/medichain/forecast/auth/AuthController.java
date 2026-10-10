package com.medichain.forecast.auth;

import com.medichain.forecast.auth.AuthService.LoginRequest;
import com.medichain.forecast.auth.AuthService.Session;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** State name and districts for the sign-in page. Open to everyone. */
    @GetMapping("/regions")
    public Map<String, Object> regions() {
        return auth.regions();
    }

    @PostMapping("/login")
    public Session login(@RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public Session me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        Session s = auth.session(authorization);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
        }
        return s;
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
        return Map.of("status", "signed out");
    }
}
