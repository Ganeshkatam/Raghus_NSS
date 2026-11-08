package edu.college.nss.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CustomUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
            ROLE_ADMIN > VOLUNTEERS_MANAGE
            ROLE_ADMIN > VOLUNTEERS_VIEW
            ROLE_ADMIN > UNITS_MANAGE
            ROLE_ADMIN > UNITS_VIEW
            ROLE_ADMIN > EVENTS_MANAGE
            ROLE_ADMIN > EVENTS_VIEW
            ROLE_ADMIN > EVENTS_REGISTER
            ROLE_ADMIN > ATTENDANCE_MANAGE
            ROLE_ADMIN > ATTENDANCE_VIEW
            ROLE_ADMIN > ATTENDANCE_CHECKIN
            ROLE_ADMIN > SERVICE_HOURS_MANAGE
            ROLE_ADMIN > SERVICE_HOURS_VIEW
            ROLE_ADMIN > SERVICE_HOURS_LOG
            ROLE_ADMIN > ANNOUNCEMENTS_MANAGE
            ROLE_ADMIN > ANNOUNCEMENTS_VIEW
            ROLE_ADMIN > REPORTS_VIEW
            ROLE_ADMIN > REPORTS_EXPORT
            ROLE_ADMIN > USERS_VIEW
            ROLE_ADMIN > ROLE_FACULTY_COORDINATOR
            ROLE_FACULTY_COORDINATOR > VOLUNTEERS_MANAGE
            ROLE_FACULTY_COORDINATOR > VOLUNTEERS_VIEW
            ROLE_FACULTY_COORDINATOR > UNITS_MANAGE
            ROLE_FACULTY_COORDINATOR > UNITS_VIEW
            ROLE_FACULTY_COORDINATOR > EVENTS_MANAGE
            ROLE_FACULTY_COORDINATOR > EVENTS_VIEW
            ROLE_FACULTY_COORDINATOR > EVENTS_REGISTER
            ROLE_FACULTY_COORDINATOR > ATTENDANCE_MANAGE
            ROLE_FACULTY_COORDINATOR > ATTENDANCE_VIEW
            ROLE_FACULTY_COORDINATOR > SERVICE_HOURS_MANAGE
            ROLE_FACULTY_COORDINATOR > SERVICE_HOURS_VIEW
            ROLE_FACULTY_COORDINATOR > SERVICE_HOURS_LOG
            ROLE_FACULTY_COORDINATOR > ANNOUNCEMENTS_MANAGE
            ROLE_FACULTY_COORDINATOR > ANNOUNCEMENTS_VIEW
            ROLE_FACULTY_COORDINATOR > REPORTS_VIEW
            ROLE_FACULTY_COORDINATOR > REPORTS_EXPORT
            ROLE_FACULTY_COORDINATOR > USERS_VIEW
            ROLE_FACULTY_COORDINATOR > ROLE_PROGRAMME_OFFICER
            ROLE_PROGRAMME_OFFICER > VOLUNTEERS_MANAGE
            ROLE_PROGRAMME_OFFICER > VOLUNTEERS_VIEW
            ROLE_PROGRAMME_OFFICER > UNITS_MANAGE
            ROLE_PROGRAMME_OFFICER > UNITS_VIEW
            ROLE_PROGRAMME_OFFICER > EVENTS_MANAGE
            ROLE_PROGRAMME_OFFICER > EVENTS_VIEW
            ROLE_PROGRAMME_OFFICER > EVENTS_REGISTER
            ROLE_PROGRAMME_OFFICER > ATTENDANCE_MANAGE
            ROLE_PROGRAMME_OFFICER > ATTENDANCE_VIEW
            ROLE_PROGRAMME_OFFICER > SERVICE_HOURS_MANAGE
            ROLE_PROGRAMME_OFFICER > SERVICE_HOURS_VIEW
            ROLE_PROGRAMME_OFFICER > SERVICE_HOURS_LOG
            ROLE_PROGRAMME_OFFICER > ANNOUNCEMENTS_MANAGE
            ROLE_PROGRAMME_OFFICER > ANNOUNCEMENTS_VIEW
            ROLE_PROGRAMME_OFFICER > REPORTS_VIEW
            ROLE_PROGRAMME_OFFICER > REPORTS_EXPORT
            ROLE_PROGRAMME_OFFICER > ROLE_STUDENT_LEADER
            ROLE_STUDENT_LEADER > ATTENDANCE_MANAGE
            ROLE_STUDENT_LEADER > ATTENDANCE_VIEW
            ROLE_STUDENT_LEADER > ATTENDANCE_CHECKIN
            ROLE_STUDENT_LEADER > SERVICE_HOURS_LOG
            ROLE_STUDENT_LEADER > SERVICE_HOURS_VIEW
            ROLE_STUDENT_LEADER > EVENTS_VIEW
            ROLE_STUDENT_LEADER > EVENTS_REGISTER
            ROLE_STUDENT_LEADER > UNITS_VIEW
            ROLE_STUDENT_LEADER > VOLUNTEERS_VIEW
            ROLE_STUDENT_LEADER > ANNOUNCEMENTS_VIEW
            ROLE_STUDENT_LEADER > REPORTS_VIEW
            ROLE_STUDENT_LEADER > ROLE_VOLUNTEER
            ROLE_VOLUNTEER > ATTENDANCE_CHECKIN
            ROLE_VOLUNTEER > ATTENDANCE_VIEW
            ROLE_VOLUNTEER > EVENTS_REGISTER
            ROLE_VOLUNTEER > EVENTS_VIEW
            ROLE_VOLUNTEER > UNITS_VIEW
            ROLE_VOLUNTEER > VOLUNTEERS_VIEW
            ROLE_VOLUNTEER > SERVICE_HOURS_VIEW
            ROLE_VOLUNTEER > ANNOUNCEMENTS_VIEW
            ROLE_VOLUNTEER > REPORTS_VIEW
        """);
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy);
        return expressionHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Please sign in to continue.\"}}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"You do not have permission to do this.\"}}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
