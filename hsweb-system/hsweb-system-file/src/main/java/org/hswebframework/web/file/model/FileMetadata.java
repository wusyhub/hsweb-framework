package org.hswebframework.web.file.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * @author wusy
 * Company: 福建亿鑫海信息科技有限公司
 * Createtime : 2022/1/24 下午2:58
 * Description :
 * 注意：本内容仅限于福建亿鑫海信息科技有限公司内部传阅，禁止外泄以及用于其他的商业目的
 */
@Getter
@Setter
@Builder
public class FileMetadata implements Serializable {
    /**
     * 文件桶名称
     */
    @Schema(description = "文件桶名称")
    private String bucket;

    /**
     * 文件类型
     */
    @Schema(description = "文件类型")
    private String fileType;

    /**
     * 文件名称
     */
    @Schema(description = "文件名称")
    private String fileName;
    /**
     * 文件id
     */
    @Schema(description = "文件id")
    private String fileId;
    /**
     * 文件大小
     */
    @Schema(description = "文件大小")
    private Integer fileSize;
    /**
     * 文件头部信息
     */
    @Schema(description = "文件头部信息")
    private Map<String, String> headers;
    /**
     * 文件自定义用户元素
     */
    @Schema(description = "文件自定义用户元素")
    private Map<String, String> metadata;
    /**
     * 文件流
     */
    @Schema(description = "文件流")
    private byte[] bytes;
}
