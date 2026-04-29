package medimatch;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.*;
import org.springframework.security.core.context.*;
import org.springframework.stereotype.*;
import org.springframework.web.filter.*;

import java.io.*;
import java.security.*;
import java.util.*;

// Runs on every request and checks for a valid JWT token

@Component
class JwtAuthFilter extends OncePerRequestFilter {
    @Autowired JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                if (jwtUtil.validate(token)) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        jwtUtil.getEmail(token),
                        jwtUtil.getUserId(token),
                        List.of(new SimpleGrantedAuthority("ROLE_" + jwtUtil.getRole(token)))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}

// Creates and validates JWT tokens

@Component
class JwtUtil {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration}") private long expiration;

    private Key key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // Create a new JWT token for a user
    String generate(String email, String role, String userId) {
        return Jwts.builder()
            .setSubject(email)
            .claim("role", role)
            .claim("userId", userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(key(), SignatureAlgorithm.HS256)
            .compact();
    }

    // Read data from a token (returns null if token is invalid)
    private Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

    String getEmail(String token)  { return getClaims(token).getSubject(); }
    String getRole(String token)   { return (String) getClaims(token).get("role"); }
    String getUserId(String token) { return (String) getClaims(token).get("userId"); }

    // Check if token is valid and not expired
    boolean validate(String token) {
        Claims claims = getClaims(token);
        return claims != null && claims.getExpiration().after(new Date());
    }
}
