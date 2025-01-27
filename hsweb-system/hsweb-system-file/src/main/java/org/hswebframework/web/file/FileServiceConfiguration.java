package org.hswebframework.web.file;

import org.hswebframework.web.file.service.FileStorageService;
import org.hswebframework.web.file.service.LocalFileStorageService;
import org.hswebframework.web.file.service.MinioFileStorageService;
import org.hswebframework.web.file.web.ReactiveFileController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileUploadProperties.class)
public class FileServiceConfiguration {


    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfiguration {

        @Bean
        @ConditionalOnMissingBean(FileStorageService.class)
        @ConditionalOnProperty(value = "hsweb.file.upload.storage", havingValue = "local", matchIfMissing = true)
        public FileStorageService localFileStorageService(FileUploadProperties properties) {
            return new LocalFileStorageService(properties);
        }

        @Bean
        @ConditionalOnMissingBean(FileStorageService.class)
        @ConditionalOnProperty(value = "hsweb.file.upload.storage", havingValue = "minio")
        public FileStorageService minioFileStorageService() {
            return new MinioFileStorageService();
        }

        @Bean
        @ConditionalOnMissingBean(name = "reactiveFileController")
        public ReactiveFileController reactiveFileController(FileUploadProperties properties,
                                                             FileStorageService storageService) {
            return new ReactiveFileController(properties, storageService);
        }

    }

}
