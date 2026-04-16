package com.pm.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    // 7:24:00 mark in video
    // First thing we'll do in our class is to configure Spring Security
    // Out of the box Spring Security is rather restrictive
    // Though we want to use it for some things, we don't want to use it for everything

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Configure SpringSecurity to be a little less secure than we need it to be.
        http.authorizeHttpRequests(
                // Let all requests through without adding additional security checks.
                // We don't need to block requests at the AuthService level b/c the only requests recieved
                //  are from the API Gateway, which we control.
                // Also, we don't expose our auth service to the internet, further reducing risks of bad requests
                //  from bad actors.
                authorize -> authorize.anyRequest().permitAll())
                // Disable the C_ross S_ite R_equest F_orgery (CSRF) protection, as request is coming from
                //  the API GATEWAY, which we control, and csrf would typically be used to protect against any
                //  front end client requests, that have a hacked auth token. We don't need it because our auth
                //  service again is secured by our API GATEWAY.
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // Now create password encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
