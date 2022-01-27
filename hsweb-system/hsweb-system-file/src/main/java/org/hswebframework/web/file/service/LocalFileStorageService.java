package org.hswebframework.web.file.service;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.compress.utils.IOUtils;
import org.hswebframework.web.file.FileUploadProperties;
import org.hswebframework.web.file.model.FileMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@AllArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final FileUploadProperties properties;

    @Override
    public Mono<String> saveFile(FilePart filePart) {
        FileUploadProperties.StaticFileInfo info = properties.createStaticSavePath(filePart.filename());
        return (filePart)
                .transferTo(new File(info.getSavePath()))
                .thenReturn(info.getLocation());
    }

    private static final OpenOption[] FILE_CHANNEL_OPTIONS = {
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE};

    @Override
    @SneakyThrows
    public Mono<String> saveFile(InputStream inputStream, String fileType) {
        String fileName = "_temp" + (fileType.startsWith(".") ? fileType : "." + fileType);

        FileUploadProperties.StaticFileInfo info = properties.createStaticSavePath(fileName);

        return Mono
                .fromCallable(() -> {
                    try (ReadableByteChannel input = Channels.newChannel(inputStream);
                         FileChannel output = FileChannel.open(Paths.get(info.getSavePath()), FILE_CHANNEL_OPTIONS)) {
                        long size = (input instanceof FileChannel ? ((FileChannel) input).size() : Long.MAX_VALUE);
                        long totalWritten = 0;
                        while (totalWritten < size) {
                            long written = output.transferFrom(input, totalWritten, size - totalWritten);
                            if (written <= 0) {
                                break;
                            }
                            totalWritten += written;
                        }
                        return info.getLocation();
                    }
                });
    }

    @Override
    public Mono<FileMetadata> getFile(String bucket, String fileId) {
        FileInputStream input = null;
        try {
            String fileType = fileId.substring(fileId.lastIndexOf(".") + 1);
            String absPath = properties.getStaticFilePath().concat(File.separator).concat(bucket).concat(File.separator).concat(fileId);
            File file = new File(absPath);
            input = new FileInputStream(file);
            //文件流大小
            int available = input.available();
            //读取文件流数据
            byte[] bytes = IOUtils.toByteArray(input);
            return Mono.just(FileMetadata.builder().bucket(bucket)
                    .fileId(bucket + "/" + fileId)
                    .fileName(file.getName())
                    .fileSize(available)
                    .fileType(fileType)
                    //.headers(headers)
                    //.metadata(metadata)
                    .bytes(bytes)
                    .build());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

    }
}
