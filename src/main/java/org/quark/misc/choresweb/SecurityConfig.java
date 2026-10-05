package org.quark.misc.choresweb;

import java.util.List;

import org.quark.misc.choresweb.svc.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	private List<String> allowedOrigins;

	private final AuthService theAuthService;
	private final SseTokenParamFilter theSseTokenParamFilter;

	public SecurityConfig(//
		@Value("${chores.cors.allowed-origins}") List<String> allowedOrigins, //
		AuthService authService, //
		SseTokenParamFilter sseTokenParamFilter//
	) {
		this.allowedOrigins = allowedOrigins;
		theAuthService = authService;
		theSseTokenParamFilter = sseTokenParamFilter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http//
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))//
			.csrf(csrf -> csrf.disable()) // Enable and configure CSRF with cookies in production
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))//
			// Inject the query parameter translator at the very front of the security boundary
			.addFilterBefore(theSseTokenParamFilter, UsernamePasswordAuthenticationFilter.class)//
			.authorizeHttpRequests(auth -> auth//
				.requestMatchers("/api/public/**", // I don't have any public APIs at the moment
					"/api/auth/refresh", "/api/auth/logout" // No authorization for end points needed for authorization
				).permitAll()//
				.anyRequest().authenticated()//
			)//
				// Configures the backend strictly as a stateless Resource Server accepting JWTs
			.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder())))//
		;

		return http.build();
	}

	@Bean
	public JwtDecoder jwtDecoder() {
		// 1. Fetch your custom stateless symmetric key application decoder from the service
		JwtDecoder myDecoder = theAuthService.getDecoder();

		// 2. Automatically build Google's decoder using standard OIDC discovery rules.
		// This uses your application.yaml parameter context to pull down rotating certs natively!
		JwtDecoder googleDecoder = JwtDecoders.fromIssuerLocation("https://accounts.google.com");

		// 3. Functional Router Lambda: Directs keys based on unverified header structures
		return token -> {
			try {
				SignedJWT signedJWT = SignedJWT.parse(token);
				JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
				String issuer = claims.getIssuer();

				// Route tokens containing a google authority domain down the OIDC pipeline
				if (issuer != null && issuer.contains("google.com")) {
					return googleDecoder.decode(token);
				}

				// Safe local fallback: Any internal session validation runs strictly on your local secret keys
				return myDecoder.decode(token);
			} catch (Exception e) {
				throw new JwtException("Failed to route and decode incoming token payload structure", e);
			}
		};
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(true); // Crucial for sending JSESSIONID cookies back and forth

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
