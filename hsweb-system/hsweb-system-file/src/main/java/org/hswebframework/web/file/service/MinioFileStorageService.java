package org.hswebframework.web.file.service;

import io.minio.*;
import io.minio.messages.*;
import org.apache.commons.compress.utils.IOUtils;
import org.hswebframework.utils.time.DateFormatter;
import org.hswebframework.web.file.bucket.Bucket;
import org.hswebframework.web.file.model.FileMetadata;
import org.hswebframework.web.id.IDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wusy
 * Company: 福建亿鑫海信息科技有限公司
 * Createtime : 2022/1/24 下午2:32
 * Description :
 * 注意：本内容仅限于福建亿鑫海信息科技有限公司内部传阅，禁止外泄以及用于其他的商业目的
 */
public class MinioFileStorageService implements FileStorageService, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(MinioFileStorageService.class);

    private final Set<String> buckets = ConcurrentHashMap.newKeySet();

    private MinioClient minio;

    /**
     * minio对象存储服务的URL
     */
    @Value("${hsweb.file.minio.server.endpoint}")
    private String endpoint;

    /**
     * minio对象存储服务的URL
     */
    @Value("${hsweb.file.minio.server.access}")
    private String access;

    /**
     * minio对象存储服务的密码
     */
    @Value("${hsweb.file.minio.server.secret}")
    private String secret;

    /**
     * 文件所在路径
     */
    @Value("${hsweb.file.upload.static-location:/jetlinks/file/display/}")
    private String location;
    /**
     * 临时文件过期时间,默认3天
     */
    @Value("${minio.temporary.bucket.overdue.days:3}")
    private int OVERDUE_DAYS;
    /**
     * 自定义用户元素header头部标签
     */
    private final String X_AMZ_META = "x-amz-meta-";
    /**
     * 文件名标签
     */
    private final String FILE_NAME_LABEL = "file-name";
    /**
     * 文件大小标签
     */
    private final String CONTENT_LENGTH_LABEL = "content-length";

    @Override
    public Mono<String> saveFile(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> this.uploadFile(
                        () -> DateFormatter.toString(new Date(), "yyyyMMdd"),
                        filePart.filename(),
                        null,
                        null,
                        dataBuffer.asInputStream()
                )).map(fileMetadata -> location.concat(File.separator).concat(fileMetadata.getFileId()));
    }

    @Override
    public Mono<String> saveFile(InputStream inputStream, String fileType) {
        String fileName = "_temp" + (fileType.startsWith(".") ? fileType : "." + fileType);
        FileMetadata fileMetadata = this.uploadFile(
                () -> DateFormatter.toString(new Date(), "yyyyMMdd"),
                fileName,
                null,
                null,
                inputStream
        );
        return Mono.just(location.concat(File.separator).concat(fileMetadata.getFileId()));
    }

    @Override
    public Mono<FileMetadata> getFile(String bucket, String fileId) {
        GetObjectResponse response = null;
        try {
            String fileType = fileId.substring(fileId.lastIndexOf(".") + 1);
            MinioClient minio = this.getMinioClient(() -> bucket, false);
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    //设置上传的存储桶名称
                    .bucket(bucket)
                    //设置文件名称
                    .object(fileId);
            String[] fileName = {fileId};
            response = minio.getObject(builder.build());
            //获取所有的头部信息，该头部信息包含了用户自定义信息
            Map<String, String> headers = new HashMap<>(response.headers().size());
            //用户自定义信息
            Map<String, String> metadata = new HashMap<>(8);
            response.headers().toMultimap().forEach((name, value) -> {
                headers.put(name, value.get(0));
                if (name.startsWith(X_AMZ_META)) {
                    metadata.put(name, value.get(0));
                    if (name.contains(FILE_NAME_LABEL)) {
                        fileName[0] = value.get(0);
                    }
                }
            });
            //文件流大小
            int available = Integer.parseInt(headers.get(CONTENT_LENGTH_LABEL));
            //读取文件流数据
            byte[] bytes = IOUtils.toByteArray(response);
            return Mono.just(FileMetadata.builder().bucket(bucket)
                    .fileId(bucket + "/" + fileId)
                    .fileName(fileName[0]).fileSize(available)
                    .fileType(fileType)
                    .headers(headers)
                    .metadata(metadata)
                    .bytes(bytes)
                    .build());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * 插入文件
     *
     * @param bucket   桶名字
     * @param fileName 文件名
     * @param headers  文件头部信息
     * @param metadata 文件元数据
     * @param stream   文件字节
     * @return
     */
    public FileMetadata uploadFile(Bucket bucket, String fileName, Map<String, String> headers, Map<String, String> metadata, InputStream stream) {
        try {
            MinioClient minio = this.getMinioClient(bucket, true);
            //文件上传到minio上的Name把文件后缀带上，不然下载出现格式问题
            String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
            String fileId = IDGenerator.SNOW_FLAKE_STRING.generate() + "." + fileType;
            //文件流大小
            int available = stream.available();
            //设置上传的存储桶名称
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    //设置上传的存储桶名称
                    .bucket(bucket.getName())
                    //设置文件名称
                    .object(fileId)
                    //设置文件流
                    .stream(stream, stream.available(), -1);
            if (headers != null && !headers.isEmpty()) {
                //设置文件头部信息
                builder.headers(headers);
            }
            if (metadata == null) {
                metadata = new HashMap<>(1);
                metadata.put(FILE_NAME_LABEL, fileName);
            }
            if (!metadata.isEmpty()) {
                //添加自定义/用户元数据
                builder.userMetadata(metadata);
            }
            //上传
            minio.putObject(builder.build());

            FileMetadata fileMetadata = FileMetadata.builder().bucket(bucket.getName())
                    .fileName(fileName)
                    .fileType(fileType)
                    .fileId(bucket.getName() + File.separator + fileId)
                    .fileSize(available).headers(headers).metadata(metadata).build();
            return fileMetadata;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * 创建minio客户端
     *
     * @param bucket 上传文件存储的桶
     * @param isMake 当桶不存在的时候，是否自动创桶，
     * @return minio客户端
     * @throws Exception 异常
     */
    private MinioClient getMinioClient(Bucket bucket, boolean isMake) throws Exception {
        // 检查存储桶是否已经存在
        if (buckets.contains(bucket.getName())) {
            return minio;
        }
        boolean isExist = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket.getName()).build());
        if (isExist) {
            buckets.add(bucket.getName());
            return minio;
        }
        logger.warn("bucket [{}] does not exists.", bucket.getName());
        if (!isMake) {
            throw new Exception("bucket [" + bucket.getName() + "] does not exists.");
        }
        //如果存储桶不存在则创建一个
        minio.makeBucket(MakeBucketArgs.builder().bucket(bucket.getName()).build());
        //判断桶是否是临时设备
        if (bucket.temporary()) {
            List<LifecycleRule> rules = new ArrayList<>();
            //设置文件7天有效
            rules.add(new LifecycleRule(Status.ENABLED, null, new Expiration((ResponseDate) null, OVERDUE_DAYS, null), null, null, null, null, null));
            LifecycleConfiguration configuration = new LifecycleConfiguration(rules);
            minio.setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(bucket.getName()).config(configuration).build());
        }
        buckets.add(bucket.getName());
        return minio;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 使用MinIO服务的URL，端口，Access key和Secret key创建一个MinioClient对象
        minio = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
        //设置超时时间,链接5秒超时,写30秒超时,读30秒超时
        minio.setTimeout(5 * 1000, 30 * 1000, 30 * 1000);
    }
}
