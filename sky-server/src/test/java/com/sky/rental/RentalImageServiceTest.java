package com.sky.rental;

import com.sky.exception.BaseException;
import com.sky.rental.service.RentalImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RentalImageServiceTest {
    @TempDir Path directory;

    @Test
    void validatesActualImageAndGeneratesServerFilename() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        String url = new RentalImageService(directory.toString()).upload(
                new MockMultipartFile("file", "../../client.jpg", "image/jpeg", output.toByteArray()));
        assertTrue(url.startsWith("/images/rental-"));
        assertTrue(url.endsWith(".png"));
        assertTrue(Files.isRegularFile(directory.resolve(url.substring("/images/".length()))));
    }

    @Test
    void rejectsTextDisguisedAsImage() throws Exception {
        assertThrows(BaseException.class, () -> new RentalImageService(directory.toString()).upload(
                new MockMultipartFile("file", "image.png", "image/png", "not an image".getBytes())));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void rejectsExcessiveDimensionsBeforeWriting() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8193, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThrows(BaseException.class, () -> new RentalImageService(directory.toString()).upload(
                new MockMultipartFile("file", "wide.png", "image/png", output.toByteArray())));
    }

    @Test
    void rejectsOversizedUpload() {
        assertThrows(BaseException.class, () -> new RentalImageService(directory.toString()).upload(
                new MockMultipartFile("file", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1])));
    }
}
