package com.pm.apigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class JwtValidationException {
    @ExceptionHandler(WebClientResponseException.Unauthorized.class)
    // So, when api-gateway calls auth-service and is unauthorized, this method intercepts
    // and will return this unauthorized error instead of a 500.
    public Mono<Void> handleUnauthorizedException(ServerWebExchange exchange) {
        // Mono used by filter chain to tell Spring that the current filter is finished.
        // Mono<Void> emits a signal that tell spring that the current execution is finished.
        // We use this return type because it's how filter chains work.
        // So, this method re
        exchange.getResponse().setRawStatusCode(HttpStatus.UNAUTHORIZED.value());
        return exchange.getResponse().setComplete();
    }

}
