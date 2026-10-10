package com.medichain.forecast.auth;

import com.medichain.forecast.auth.AuthService.Access;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Runs before every /api request and turns away callers without a valid session. */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Access access = auth.check(request.getMethod(), request.getRequestURI(),
                request.getHeader("Authorization"), request.getParameter("district"));
        if (access.allowed()) {
            return true;
        }
        response.setStatus(access.status());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + access.message().replace("\"", "'") + "\"}");
        return false;
    }
}
