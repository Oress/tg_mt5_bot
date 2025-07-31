package org.ipan.hedging.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.controller.dto.ApiResponse;
import org.ipan.hedging.controller.dto.JwtResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/*
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public ApiResponse<JwtResponse> authenticateUser(@RequestBody LoginRequest loginRequest,
                                                     HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        JwtResponse jwtResponse = new JwtResponse(jwt, userDetails.getId(), userDetails.getUsername(), roles);

//        setHttpOnlyJwtCookie(jwtResponse.getToken(), 24 * 60 * 60, response);

        return ApiResponse.success(jwtResponse);
    }

    private void setHttpOnlyJwtCookie(String jwtResponse, int expiry, HttpServletResponse response) {
        // Create HTTP-only cookie with JWT token
        Cookie jwtCookie = new Cookie("jwt", jwtResponse);
        jwtCookie.setHttpOnly(true);  // Prevents JavaScript access
        jwtCookie.setSecure(true);    // Only send over HTTPS in production
        jwtCookie.setPath("/");       // Available for entire application
        jwtCookie.setMaxAge(expiry); // 24 hours (adjust as needed)
        response.addCookie(jwtCookie);
    }
}
*/
