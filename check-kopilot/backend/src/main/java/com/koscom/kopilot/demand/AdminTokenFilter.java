package com.koscom.kopilot.demand;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인 인증이 없는 MVP에서 Admin 조회 화면을 보호하는 최소 장치.
 * 실패 시 401이 아니라 404를 반환해 엔드포인트 존재 자체를 노출하지 않는다.
 * (정식 인증·권한은 스펙 6절 MVP 제외 항목 — 로드맵)
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private final String token;

    public AdminTokenFilter(@Value("${admin.token}") String token) { this.token = token; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-Admin-Token");
        if (provided == null) provided = request.getParameter("token");
        if (token == null || token.isBlank() || !token.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
