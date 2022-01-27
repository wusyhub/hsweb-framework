package org.hswebframework.web.file.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.ResourceAction;
import org.hswebframework.web.authorization.exception.AccessDenyException;
import org.hswebframework.web.file.FileUploadProperties;
import org.hswebframework.web.file.enums.MimeTypeEnum;
import org.hswebframework.web.file.service.FileStorageService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.channels.Channels;

@RestController
@Resource(id = "file", name = "文件上传")
@Slf4j
@RequestMapping("/file")
@Tag(name = "文件上传")
public class ReactiveFileController {

    private final FileUploadProperties properties;

    private final FileStorageService fileStorageService;

    public ReactiveFileController(FileUploadProperties properties, FileStorageService fileStorageService) {
        this.properties = properties;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/static")
    @SneakyThrows
    @ResourceAction(id = "upload-static", name = "静态文件")
    @Operation(summary = "上传静态文件")
    public Mono<String> uploadStatic(@RequestPart("file")
                                     @Parameter(name = "file", description = "文件", style = ParameterStyle.FORM) Part part) {
        if (part instanceof FilePart) {
            FilePart filePart = ((FilePart) part);
            if (properties.denied(filePart.filename(), filePart.headers().getContentType())) {
                throw new AccessDenyException();
            }
            return fileStorageService.saveFile(filePart);
        } else {
            return Mono.error(() -> new IllegalArgumentException("[file] part is not a file"));
        }

    }

    @GetMapping("/display/{bucket}/{fileId}")
    @Authorize(ignore = true)
    @Operation(summary = "获取静态文件")
    public Mono<Void> display(@PathVariable String bucket, @PathVariable String fileId, ServerWebExchange exchange) {
        return fileStorageService.getFile(bucket, fileId)
                .switchIfEmpty(Mono.error(new FileNotFoundException("No file was found with fileId: " + bucket + File.separator + fileId)))
                .flatMap(file -> {
                    ServerHttpRequest request = exchange.getRequest();

                    ServerHttpResponse response = exchange.getResponse();
                    //设置文件长度
                    response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, "" + file.getFileSize());
                    //设置文件类型
                    response.getHeaders().setContentType(MediaType.parseMediaType(MimeTypeEnum.getContentType(file.getFileType())));
                    //设置文件名
                    response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + encodeFileName(file.getFileName(), request));
                    return response.writeWith(DataBufferUtils.readByteChannel(
                            () -> Channels.newChannel(new ByteArrayInputStream(file.getBytes())),
                            new DefaultDataBufferFactory(),
                            file.getFileSize()));
                });
    }

    /**
     * 文件名编码，处理下载时文件乱码问题
     *
     * @param fileName
     * @param request
     * @return
     */
    private String encodeFileName(String fileName, ServerHttpRequest request) {
        try {
            String agent = request.getHeaders().getFirst("USER-AGENT");
            if (null != agent && -1 != agent.indexOf("MSIE")) {
                // ie浏览器及Edge浏览器
                return java.net.URLEncoder.encode(fileName, "UTF-8");
            }
            if (null != agent && -1 != agent.indexOf("Trident")) {
                // ie浏览器及Edge浏览器
                return java.net.URLEncoder.encode(fileName, "UTF-8");
            }
            if (null != agent && -1 != agent.indexOf("Edge")) {
                // ie浏览器及Edge浏览器
                return java.net.URLEncoder.encode(fileName, "UTF-8");
            }
            if (null != agent && -1 != agent.indexOf("Mozilla")) {
                // 火狐,Chrome等浏览器
                return new String(fileName.getBytes("UTF-8"), "iso-8859-1");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return fileName;
    }
}
