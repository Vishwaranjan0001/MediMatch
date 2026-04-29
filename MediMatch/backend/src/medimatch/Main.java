package medimatch;

import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.config.annotation.web.builders.*;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.config.http.*;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.*;
import org.springframework.web.cors.*;

import java.util.*;

@SpringBootApplication
@EnableAsync
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        System.out.println("Server started at http://localhost:8080");
    }
}

// Security setup - JWT filter, CORS, password encoder

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Autowired JwtAuthFilter jwtAuthFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();
        corsSource.registerCorsConfiguration("/**", cors);

        return http
            .csrf(c -> c.disable())
            .cors(c -> c.configurationSource(corsSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/api/public/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

@Component
class GeoUtils {

    // Blood group compatibility map (who can donate to whom)
    private static final HashMap<String, List<String>> COMPATIBLE = new HashMap<>();

    static {
        COMPATIBLE.put("A+",  List.of("A+", "A-", "O+", "O-"));
        COMPATIBLE.put("A-",  List.of("A-", "O-"));
        COMPATIBLE.put("B+",  List.of("B+", "B-", "O+", "O-"));
        COMPATIBLE.put("B-",  List.of("B-", "O-"));
        COMPATIBLE.put("AB+", List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"));
        COMPATIBLE.put("AB-", List.of("A-", "B-", "AB-", "O-"));
        COMPATIBLE.put("O+",  List.of("O+", "O-"));
        COMPATIBLE.put("O-",  List.of("O-"));
    }

    // Calculate distance between two lat/lng points in kilometers (Haversine formula)
    double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // Check if two points are within given radius (in km)
    boolean withinRadius(double lat1, double lon1, double lat2, double lon2, double km) {
        return distance(lat1, lon1, lat2, lon2) <= km;
    }

    // Get blood groups that can donate to the given recipient group
    List<String> compatibleDonorGroups(String recipientGroup) {
        List<String> result = COMPATIBLE.get(recipientGroup);
        if (result == null) return new ArrayList<>();
        return result;
    }

    String formatDistance(double km) {
        if (km < 1) {
            return ((int) (km * 1000)) + " m away";
        } else {
            return String.format("%.1f km away", km);
        }
    }
}
