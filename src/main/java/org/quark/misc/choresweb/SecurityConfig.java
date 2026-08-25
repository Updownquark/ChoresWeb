package org.quark.misc.choresweb;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
	@Value("${chores.cors.allowed-origins}")
	private List<String> allowedOrigins;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http//
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))//
			.csrf(csrf -> csrf.disable()) // Enable and configure CSRF with cookies in production
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))//
			.authorizeHttpRequests(auth -> auth//
				// .requestMatchers("/api/public/**").permitAll()//
				.anyRequest().authenticated()//
			)//
				// Configures the backend strictly as a stateless Resource Server accepting JWTs
			.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))//
		;

		return http.build();
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
