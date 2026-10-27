package com.sky.rental.service;
import com.sky.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.UUID;
@Service
public class RentalImageService {
    private final Path directory;
    public RentalImageService(@Value("${sky.upload.path}") String path) { directory=Paths.get(path).toAbsolutePath().normalize(); }
    public String upload(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>2L*1024*1024) throw new BaseException("图片须小于2MB且不能为空");
        try {
            byte[] bytes=file.getBytes();
            String extension;
            try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);
                if(!readers.hasNext()) throw new BaseException("仅支持实际PNG或JPEG图片");
                ImageReader reader=readers.next();
                try {
                    String format=reader.getFormatName();
                    if(!("PNG".equalsIgnoreCase(format)||"JPEG".equalsIgnoreCase(format))) throw new BaseException("仅支持PNG或JPEG图片");
                    reader.setInput(stream,true,true);
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1 || height<1 || (long)width*height>16000000 || width>8192 || height>8192) throw new BaseException("图片尺寸过大");
                    if(reader.read(0)==null) throw new BaseException("图片内容损坏");
                    extension="PNG".equalsIgnoreCase(format)?".png":".jpg";
                } finally { reader.dispose(); }
            }
            Files.createDirectories(directory);String name="rental-"+UUID.randomUUID()+extension;
            Files.write(directory.resolve(name),bytes,StandardOpenOption.CREATE_NEW);return "/images/"+name;
        } catch(IOException ex) { throw new BaseException("图片读取或保存失败"); }
    }
}
