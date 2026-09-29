package org.quark.misc.choresweb.svc;

import java.text.ParseException;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.qommons.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Service
public class AuthService {
	private static final JWSHeader JWS_HEADER = new JWSHeader(JWSAlgorithm.HS256);

	private final long theAccessTokenExpiry;
	private final long theRefreshTokenExpiry;
	private final String theIssuerUri;

	private JWSSigner theSigner;
	private JWSVerifier theVerifier;
	private JwtDecoder theDecoder;

	public AuthService(//
		@Value("${chores.auth.accessTokenExpiry}") String rawAccessExpiry, //
		@Value("${chores.auth.refreshTokenExpiry}") String rawRefreshExpiry, //
		@Value("${chores.auth.issuer}") String issuerUri, //
		@Value("${chores.auth.secret}") String secret) {
		try {
			theAccessTokenExpiry = TimeUtils.parseDuration(rawAccessExpiry).asDuration().toMillis();
			theRefreshTokenExpiry = TimeUtils.parseDuration(rawRefreshExpiry).asDuration().toMillis();
		} catch (ParseException e) {
			throw new IllegalStateException("Bad token expiration time configuration", e);
		}
		this.theIssuerUri = issuerUri;

		SecretKey secretKey = new SecretKeySpec(secret.getBytes(), "HMACSHA256");
		try {
			theSigner = new MACSigner(secretKey);
			theVerifier = new MACVerifier(secretKey);
		} catch (JOSEException e) {
			throw new IllegalStateException("Failed to generate secure signer/verifier", e);
		}
		NimbusJwtDecoder customAppDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
		customAppDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(theIssuerUri));
		theDecoder = customAppDecoder;
	}

	public JwtDecoder getDecoder() {
		return theDecoder;
	}

	public long getAccessTokenExpiry() {
		return this.theAccessTokenExpiry;
	}

	public long getRefreshTokenExpiry() {
		return this.theRefreshTokenExpiry;
	}

	public String generateToken(String email, long durationMs) {
		try {
			JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()//
				.subject(email)//
				.issuer(theIssuerUri)// Not critical though
				.issueTime(new Date())//
				.expirationTime(new Date(System.currentTimeMillis() + durationMs))//
				.build();

			SignedJWT signedJWT = new SignedJWT(JWS_HEADER, claimsSet);
			signedJWT.sign(theSigner);

			return signedJWT.serialize();
		} catch (JOSEException e) {
			throw new RuntimeException("Failed to generate token string", e);
		}
	}

	public String validateRefreshToken(String token) {
		try {
			SignedJWT signedJWT = SignedJWT.parse(token);

			JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
			if (new Date().after(claims.getExpirationTime())) {
				throw new SecurityException("Token expired");
			}

			if (!signedJWT.verify(theVerifier)) {
				throw new SecurityException("Invalid signature");
			}

			return claims.getSubject();
		} catch (ParseException | JOSEException e) {
			throw new SecurityException("Failed to parse or validate refresh token", e);
		}
	}
}
