package com.dandimate.durooduroo.common.auth;

import com.dandimate.durooduroo.user.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revokes sessions on every device after account withdrawal. */
@Component
public class WithdrawnAccountFilter extends OncePerRequestFilter {
    private final UserAccountRepository users;

    public WithdrawnAccountFilter(UserAccountRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal principal
                && users.existsByIdAndWithdrawnAtIsNotNull(principal.getName())) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"탈퇴한 계정입니다. 다시 로그인해 주세요.\"}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
