package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
public class CommonController {

    @Value("${sky.upload.path}")
    private String uploadPath;

    @Value("${sky.upload.base-url}")
    private String baseUrl;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public ResponseEntity<Result<String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Result.error(MessageConstant.UPLOAD_FAILED));
        }

        try {
            byte[] bytes = file.getBytes();
            String extension = imageExtension(bytes);
            if (extension == null || ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                return ResponseEntity.badRequest().body(Result.error("只支持 JPG、PNG 图片"));
            }

            Path directory = Paths.get(uploadPath).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String fileName = UUID.randomUUID() + extension;
            Files.write(directory.resolve(fileName), bytes);
            String url = baseUrl.replaceAll("/+$", "") + "/" + fileName;
            return ResponseEntity.ok(Result.success(url));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(MessageConstant.UPLOAD_FAILED));
        }
    }

    private String imageExtension(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff &&
                (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return ".jpg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' &&
                bytes[2] == 'N' && bytes[3] == 'G' && (bytes[4] & 0xff) == 0x0d &&
                (bytes[5] & 0xff) == 0x0a && (bytes[6] & 0xff) == 0x1a &&
                (bytes[7] & 0xff) == 0x0a) {
            return ".png";
        }
        return null;
    }
}
