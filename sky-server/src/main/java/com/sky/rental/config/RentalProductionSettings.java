package com.sky.rental.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
@Component
@Profile("prod")
public class RentalProductionSettings {
    public RentalProductionSettings(@Value("${rental.jwt-secret}") String rental,@Value("${sky.jwt.admin-secret-key}") String staff){
        if(unsafe(rental)||unsafe(staff)||rental.equals(staff))throw new IllegalArgumentException("Production requires two different random signing secrets of at least 32 characters; development and example values are forbidden");
    }
    private static boolean unsafe(String value){return value==null||value.length()<32||value.contains("replace-with")||value.contains("development")||value.contains("ci-only");}
}
