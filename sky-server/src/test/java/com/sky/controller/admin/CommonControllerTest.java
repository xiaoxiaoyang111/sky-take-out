package com.sky.controller.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommonControllerTest {

    @TempDir
    Path uploadDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CommonController controller = new CommonController();
        ReflectionTestUtils.setField(controller, "uploadPath", uploadDirectory.toString());
        ReflectionTestUtils.setField(controller, "baseUrl", "http://localhost:8080/images");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(new Validator() {
            @Override
            public boolean supports(Class<?> clazz) {
                return false;
            }

            @Override
            public void validate(Object target, Errors errors) {
            }
        }).build();
    }

    @Test
    void pngUploadReturnsReachablePathAndWritesFile() throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l/8AAAAASUVORK5CYII=");
        String json = mockMvc.perform(multipart("/admin/common/upload")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", png)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/images/")))
                .andReturn().getResponse().getContentAsString();

        String url = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("data").asText();
        Path stored = uploadDirectory.resolve(url.substring(url.lastIndexOf('/') + 1));
        assertEquals(png.length, Files.size(stored));
    }

    @Test
    void nonImageIsRejectedAndNotWritten() throws Exception {
        mockMvc.perform(multipart("/admin/common/upload")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "text".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0));
        try (java.util.stream.Stream<Path> files = Files.list(uploadDirectory)) {
            assertEquals(0, files.count());
        }
    }
}
