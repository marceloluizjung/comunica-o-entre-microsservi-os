package br.com.cursoudemy.productapi.modules.jwt.service;

import br.com.cursoudemy.productapi.config.exception.AuthenticationException;
import br.com.cursoudemy.productapi.modules.jwt.dto.JwtResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class JwtService {

    private static final String EMPTY_SPACE = " ";
    private static final Integer TOKEN_INDEX = 1;

    @Value("${app-config.secrets.api-secret}")
    private String apiSecret;

    public void validateAuthorization(String token) {
        String accessToken = extractToken(token);
        try {
            Claims claims = Jwts
                    .parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(apiSecret.getBytes()))
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();
            JwtResponse user = JwtResponse.getUser(claims);
            if (Objects.isNull(user) || Objects.isNull(user.getId())) {
                throw new AuthenticationException("The user is not valid.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new AuthenticationException("Error while trying to proccess the Access Token.");
        }
    }

    private String extractToken(String token) {
        if (token == null || token.isEmpty()) throw new AuthenticationException("The access token was not informed.");
        if (token.contains(EMPTY_SPACE)) {
            token = token.split(EMPTY_SPACE)[TOKEN_INDEX];
        }
        return token;
    }
}
