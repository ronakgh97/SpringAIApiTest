package com.AI4Java.BackendAI.config.filter;

import com.AI4Java.BackendAI.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        final String authorizationHeader = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();

        log.trace("Processing request to: {}", requestURI);

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            log.trace("JWT found in Authorization header.");
            try {
                username = jwtUtil.extractUsername(jwt);
                log.trace("Extracted username '{}' from JWT.", username);
            } catch (ExpiredJwtException e) {
                log.warn("JWT token is expired: {} for request to {}", e.getMessage(), requestURI);
            } catch (MalformedJwtException e) {
                log.warn("JWT token is malformed: {} for request to {}", e.getMessage(), requestURI);
            } catch (SignatureException e) {
                log.warn("JWT signature validation failed: {} for request to {}", e.getMessage(), requestURI);
            } catch (Exception e) {
                log.error("Error parsing JWT token for request to {}: {}", requestURI, e.getMessage());
            }
        } else {
            log.trace("No JWT found in Authorization header for request to: {}", requestURI);
        }


        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("Security context is null for user '{}'. Attempting to authenticate.", username);
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    log.debug("JWT is valid for user '{}'. Setting authentication in security context.", username);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("Successfully authenticated user '{}' for request to {}.", username, requestURI);
                } else {
                    log.warn("JWT validation failed for user '{}'.", username);
                }
            } catch (Exception e) {
                log.error("Error during JWT authentication for user '{}': {}", username, e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}
