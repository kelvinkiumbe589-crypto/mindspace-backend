package com.mindspace.config;
import com.mindspace.security.JwtFilter;
import com.mindspace.security.OAuth2SuccessHandler;
import com.mindspace.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public SecurityConfig(JwtFilter jwtFilter, UserDetailsServiceImpl userDetailsService, OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.userDetailsService = userDetailsService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()
                        .requestMatchers("/api/contact").permitAll()
                        .requestMatchers("/api/payments/**").permitAll()
                        .requestMatchers("/api/tips/**").permitAll()
                        .requestMatchers("/api/support/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/therapists/**").permitAll()
                        .requestMatchers("/api/therapist/**").hasRole("THERAPIST")
                        .requestMatchers("/api/bookings/**").authenticated()
                        .requestMatchers("/api/support/**").authenticated()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/oauth2/**").permitAll()
                        .requestMatchers("/api/forum/**").authenticated()
                        .requestMatchers("/api/moods/**").authenticated()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // API clients get a clean 401 (not a 302 redirect to the OAuth login)
                // so the SPA can detect an expired session and prompt re-login.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        (request, response, authEx) -> response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"),
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**")))
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureUrl(frontendUrl + "/signin?error=true")
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}