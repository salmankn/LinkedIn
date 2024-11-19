package com.Linkedin.Backend.Configuration;

import com.Linkedin.Backend.Feature.Authentication.Model.AuthenticationUser;
import com.Linkedin.Backend.Feature.Authentication.Repository.AuthenticationUserRepository;
import com.Linkedin.Backend.Feature.Authentication.Uitility.Encoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoadDatabaseConfiguration {

    @Autowired
    private final Encoder encoder;

    public LoadDatabaseConfiguration(Encoder encoder){
        this.encoder = encoder;
    }

    //hum email or password ko submit or return kara rehe h or
    //commandlinerunner k jariye password ko encrypt ker rahe h
    //client ko pata hoga ki uska password encrypted h kisi ko uske password ki jankari nahi hogi agar koi encrypt karta h tabbhi
    @Bean
    public CommandLineRunner initDatabase(AuthenticationUserRepository authenticationUserRepository){
        return args -> {
            AuthenticationUser authenticationUser = new AuthenticationUser("salman@example.com", encoder.encode("Salman")); //hame random password a code milega jo ki sha-256 hase code "U2FsbWFu"
            authenticationUserRepository.save(authenticationUser);
        };
    }
}
