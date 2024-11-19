package com.Linkedin.Backend.Feature.Authentication.Service;

import com.Linkedin.Backend.Feature.Authentication.DTO.AuthenticationRequestBody;
import com.Linkedin.Backend.Feature.Authentication.DTO.AuthenticationResponseBody;
import com.Linkedin.Backend.Feature.Authentication.Model.AuthenticationUser;
import com.Linkedin.Backend.Feature.Authentication.Repository.AuthenticationUserRepository;
import com.Linkedin.Backend.Feature.Authentication.Uitility.EmailService;
import com.Linkedin.Backend.Feature.Authentication.Uitility.Encoder;
import com.Linkedin.Backend.Feature.Authentication.Uitility.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthenticationService {

    private static final Logger logger =  LoggerFactory.getLogger(AuthenticationService.class);
    private final int durationInMinutes = 1;

    private final AuthenticationUserRepository authenticationUserRepository;

    private final Encoder encoder;

    private final JsonWebToken jsonWebToken;

    private final EmailService emailService;

    public AuthenticationService(AuthenticationUserRepository authenticationUserRepository, Encoder encoder, JsonWebToken jsonWebToken, EmailService emailService) {
        this.authenticationUserRepository = authenticationUserRepository;
        this.encoder = encoder;
        this.jsonWebToken = jsonWebToken;
        this.emailService = emailService;
    }

    public static String generateEmailVerificationToken(){
        SecureRandom random = new SecureRandom();
        StringBuilder token = new StringBuilder(5);
        for (int i = 0; i < 5; i++){
            token.append(random.nextInt(10)); // Appending random digit from 0 to 9
        }
        return token.toString();
    }

    public void sendEmailVerificationToken(String email){
        Optional<AuthenticationUser> user = authenticationUserRepository.findByEmail(email);
        if (user.isPresent() && !user.get().getEmailVerified()) {
            String emailVerificationToken = generateEmailVerificationToken();
            String hashedToken = encoder.encode(emailVerificationToken);
            user.get().setEmailVerificationToken(hashedToken);
            user.get().setEmailVerificationTokenExpiryDate(LocalDateTime.now().plusMinutes(durationInMinutes));
            authenticationUserRepository.save(user.get());
            String subject = "Email Verification";
            String body = String.format("Only one step to take full advantage of LinkedIn.\n\n"
            + "Enter this code to verify your email: " + "%s\n\n" + "The code will expire in " + "%s" + " minutes.",
                    emailVerificationToken, durationInMinutes);
            try {
                emailService.sendMail(email, subject, body);
            }catch (Exception e){
                logger.info("Error while sending email: {}", e.getMessage());
            }
        }else {
            throw new IllegalArgumentException("Email verification token failed, or email is already verified.");
        }
    }

    public void validateEmailVerificationToken(String token, String email){
        Optional<AuthenticationUser> user = authenticationUserRepository.findByEmail(email);
        if (user.isPresent() && encoder.matches(token, user.get().getEmailVerificationToken()) && !user.get().getEmailVerificationTokenExpiryDate().isBefore(LocalDateTime.now())) {
            user.get().setEmailVerified(true);
            user.get().setEmailVerificationToken(null);
            user.get().setEmailVerificationTokenExpiryDate(null);
            authenticationUserRepository.save(user.get());
        } else if (user.isPresent() && encoder.matches(token, user.get().getEmailVerificationToken()) && user.get().getEmailVerificationTokenExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Email verification token expired.");
        } else {
            throw new IllegalArgumentException("Email verification token failed.");
        }
    }

    public AuthenticationUser getUser(String email){
        return authenticationUserRepository.findByEmail(email).orElseThrow(()-> new IllegalArgumentException("User not Found"));
    }

    public AuthenticationResponseBody register(AuthenticationRequestBody registerRequestBody) {
        AuthenticationUser user = authenticationUserRepository.save(new AuthenticationUser(registerRequestBody.getEmail(), encoder.encode(registerRequestBody.getPassword())));

        //we generate a token simply after saving into the database
        String emailVerificationToken = generateEmailVerificationToken();
        String hashedToken = encoder.encode(emailVerificationToken);
        user.setEmailVerificationToken(hashedToken);
        user.setEmailVerificationTokenExpiryDate(LocalDateTime.now().plusMinutes(durationInMinutes));

        authenticationUserRepository.save(user);

        String subject = "Email verification";
        String body = String.format("""
                Only one step to take full advantage of LinkedIn.
                
                Enter this code to verify your email: %s. The code will expire in %s minutes """,
                emailVerificationToken, durationInMinutes //include the token in a message body
        );
        try {
            emailService.sendMail(registerRequestBody.getEmail(), subject, body);
        } catch (Exception e){
            logger.info("Error while sending email: {}", e.getMessage());
        }
        String authToken = jsonWebToken.generateToken(registerRequestBody.getEmail());
        return new AuthenticationResponseBody(authToken, "User register successfully");
    }

    // Password reset logic
    public void sendPasswordResetToken(String email) {
        Optional<AuthenticationUser> user = authenticationUserRepository.findByEmail(email);
            if (user.isPresent()){
                String passwordResetToken = generateEmailVerificationToken();
                String hashedToken = encoder.encode(passwordResetToken);
                user.get().setPasswordResetToken(hashedToken);
                user.get().setPasswordResetTokenExpiryDate(LocalDateTime.now().plusMinutes(durationInMinutes));
                authenticationUserRepository.save(user.get());
                String subject = "Password Reset";
                String body = String.format("""
                        Your requested a password reset.
                        
                        Enter this code to reset your password: %s. This code will expires in %s minutes.""",
                        passwordResetToken, durationInMinutes
                );
                try {
                    emailService.sendMail(email, subject, body);
                } catch (Exception e) {
                    logger.info("Error while sending email: {}", e.getMessage());
                }
            } else {
                throw new IllegalArgumentException("User not found.");
            }
    }

    // Reset Password logic
    public void resetPassword(String email, String newPassword, String token){
        Optional<AuthenticationUser> user = authenticationUserRepository.findByEmail(email);
            if (user.isPresent() && encoder.matches(token, user.get().getPasswordResetToken()) && !user.get().getPasswordResetTokenExpiryDate().isBefore(LocalDateTime.now())) {
                user.get().setPasswordResetToken(token);
                user.get().setPasswordResetTokenExpiryDate(null);
                user.get().setPassword(encoder.encode(newPassword));
                authenticationUserRepository.save(user.get());
            } else if (user.isPresent() && encoder.matches(token, user.get().getPasswordResetToken()) && user.get().getPasswordResetTokenExpiryDate().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Password reset token expired.");
            } else {
                throw new IllegalArgumentException("Password reset token failed.");
            }
    }

    public AuthenticationResponseBody login(AuthenticationRequestBody loginRequestBody) {
        AuthenticationUser user = authenticationUserRepository.findByEmail(loginRequestBody.getEmail()).orElseThrow(() -> new IllegalArgumentException("User not Found"));
        if (!encoder.matches(loginRequestBody.getPassword(), user.getPassword())){
            throw new IllegalArgumentException("Password is incorrect");
        }
        String token = jsonWebToken.generateToken(loginRequestBody.getEmail());
        return new AuthenticationResponseBody(token, "Authentication succeeded.");
    }

}
