package org.hswebframework.web.file.bucket;

/**
 * @author wusy
 * Company: 福建亿鑫海信息科技有限公司
 * Createtime : 2022/1/24 下午2:58
 * Description :
 * 注意：本内容仅限于福建亿鑫海信息科技有限公司内部传阅，禁止外泄以及用于其他的商业目的
 */
public interface Bucket {
    /**
     * 桶名称
     *
     * @return
     */
    String getName();

    /**
     * 是否是临时的桶
     *
     * @return true临时 false 永久
     */
    default boolean temporary() {
        return false;
    }
}
