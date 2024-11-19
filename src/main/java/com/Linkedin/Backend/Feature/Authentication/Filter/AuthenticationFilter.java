package com.Linkedin.Backend.Feature.Authentication.Filter;


import com.Linkedin.Backend.Feature.Authentication.Model.AuthenticationUser;
import com.Linkedin.Backend.Feature.Authentication.Service.AuthenticationService;
import com.Linkedin.Backend.Feature.Authentication.Uitility.JsonWebToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class AuthenticationFilter extends HttpFilter {

    private final List<String> unsecuredEndpoints = Arrays.asList(
            "/api/v1/authentication/login",
            "/api/v1/authentication/register",
            "/api/v1/authentication/send-password-reset-token",
            "/api/v1/authentication/reset-password"
    );

    //we will check or graph email in database or not
    private final JsonWebToken jsonWebTokenService;
    private final AuthenticationService authenticationService;

    //we are autowiring by creating constructor of both above autowire
    public AuthenticationFilter(JsonWebToken jsonWebTokenService, AuthenticationService authenticationService) {
        this.jsonWebTokenService = jsonWebTokenService;
        this.authenticationService = authenticationService;
    }

    //we are overriding a doFilter method of HttpFilter for our purpose of getting response back
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Method", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Header", "Content-Type, Authorization");

        //if client is requesting for access api and want some data from the server then server will allow and send Ok back
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())){
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        //we are going to graph a path a user requesting for
        String path = request.getRequestURI();
        //if the path is not secured then this will send to a user
        if(unsecuredEndpoints.contains(path)){
            chain.doFilter(request, response);
            return;
        }

        //when the path is secured then this will be triggered
        try {
            String authorization = request.getHeader("Authorization");
            //if authorization is null or doesn't start with token
            //Note: Error were thrown will not catch by backend Controller bcuz filter will run before Controller
            if (authorization == null || !authorization.startsWith("Bearer ")){
                throw new ServletException("Token missing.");
            }

            //hum sirf is pure hisse k -> "Authorization: Bearer <token>" Bearer ka count le rahe h
            String token = authorization.substring(7);

            //if token throws time expired exception this will handle it
            if (jsonWebTokenService.isTokenExpired(token)){
                throw new ServletException("Invalid token");
            }

            //after that we are going to grab the email then we are going to use authentication service
            //user sends a valid token that's giving us an exception
            String email = jsonWebTokenService.getEmailFromToken(token);
            AuthenticationUser user = authenticationService.getUser(email);
            request.setAttribute("authenticatedUser", user);
            chain.doFilter(request, response);

        }catch (Exception e){ //we are going to add response here
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\": \"Invalid Authentication token, or token missgin.\"}");
        }
    }

}
