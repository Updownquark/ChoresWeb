package org.quark.misc.choresweb;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SseTokenParamFilter extends OncePerRequestFilter {
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		// Only intercept our dedicated streaming endpoint layout
		if (request.getRequestURI().startsWith("/api/sync/")) {
			String tokenParam = request.getParameter("token");
			if (tokenParam != null && !tokenParam.isBlank()) {
				// Wrap it inside the format Spring Security's BearerTokenAuthenticationFilter expects
				HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request) {
					@Override
					public String getHeader(String name) {
						if ("Authorization".equalsIgnoreCase(name)) {
							return "Bearer " + tokenParam;
						}
						return super.getHeader(name);
					}
				};
				filterChain.doFilter(wrappedRequest, response);
				return;
			}
		}
		filterChain.doFilter(request, response);
	}
}