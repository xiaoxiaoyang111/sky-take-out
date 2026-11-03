package com.sky.rental;
import com.sky.rental.config.RentalProductionSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RentalProductionSettingsTest {
    @Test void developmentExampleAndSharedSecretsAreRejected(){String valid="a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6";assertThrows(IllegalArgumentException.class,()->new RentalProductionSettings("short",valid));assertThrows(IllegalArgumentException.class,()->new RentalProductionSettings(valid,valid));assertThrows(IllegalArgumentException.class,()->new RentalProductionSettings("rental-local-development-change-before-deployment-2026",valid));assertThrows(IllegalArgumentException.class,()->new RentalProductionSettings("replace-with-at-least-32-random-bytes",valid));}
    @Test void distinctLongSecretsAreAccepted(){assertDoesNotThrow(()->new RentalProductionSettings("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6","z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4"));}
}
