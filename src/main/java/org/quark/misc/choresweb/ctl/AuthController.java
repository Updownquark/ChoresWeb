package org.quark.misc.choresweb.ctl;

import java.util.HashMap;
import java.util.Map;

import org.quark.misc.choresweb.svc.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/init")
	public ResponseEntity<?> login(@AuthenticationPrincipal Jwt user) {
		if (user == null)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing valid token payload context.");

		String userEmail = user.getClaimAsString("email");
		// 2. Fallback: If Spring's parser shifted keys, inspect the raw unmapped claims map directly
		if (userEmail == null && user.getClaims() != null) {
			userEmail = (String) user.getClaims().get("email");
		}

		// 3. Fallback: Check if the email was placed in the Subject ('sub') field by a mapping property
		if (userEmail == null) {
			userEmail = user.getSubject();
		}
		Boolean isEmailVerified = user.getClaimAsBoolean("email_verified");

		if (userEmail == null || Boolean.FALSE.equals(isEmailVerified)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google account email is invalid or unverified.");
		}

		// Generate tokens using the dynamically configured durations
		String accessToken = authService.generateToken(userEmail, authService.getAccessTokenExpiry());
		String refreshToken = authService.generateToken(userEmail, authService.getRefreshTokenExpiry());

		Map<String, String> response = new HashMap<>();
		response.put("accessToken", accessToken);

		return ResponseEntity.ok()//
			.header(HttpHeaders.SET_COOKIE, getRefreshCookie(refreshToken, authService.getRefreshTokenExpiry()))//
			.body(response);
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refreshAccessToken(HttpServletRequest request) {
		String refreshToken = null;
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if ("refresh_token".equals(cookie.getName())) {
					refreshToken = cookie.getValue();
					break;
				}
			}
		}

		if (refreshToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token missing");
		}

		try {
			String userEmail = authService.validateRefreshToken(refreshToken);
			String newAccessToken = authService.generateToken(userEmail, authService.getAccessTokenExpiry());

			Map<String, String> response = new HashMap<>();
			response.put("accessToken", newAccessToken);

			return ResponseEntity.ok(response);
		} catch (SecurityException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletRequest request) {
		return ResponseEntity.ok()//
			.header(HttpHeaders.SET_COOKIE, getRefreshCookie("", 0) // Instructs the browser to delete the cookie immediately
				.toString())//
			.body("Logged out successfully");
	}

	private static String getRefreshCookie(String token, long ageMs) {
		return ResponseCookie.from("refresh_token", token)//
			.httpOnly(true)//
			.secure(true)//
			.path("/api/auth/refresh")//
			.maxAge(ageMs / 1000)//
			.sameSite("Strict")//
			.build()//
			.toString();
	}
}
