package com.smartlife.server.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.smartlife.common.exception.BusinessException;
import com.smartlife.server.config.OssProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云 OSS 上传。OSS 客户端线程安全，进程内复用一个实例。
 * 文件名用 UUID 重生成，不信任原始文件名（防路径穿越/覆盖）。
 */
@Component
public class OssUtil {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    private final OssProperties props;
    private final OSS oss;

    public OssUtil(OssProperties props) {
        this.props = props;
        this.oss = new OSSClientBuilder()
                .build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
    }

    /** 上传图片，返回可直接访问的 URL（bucket 为公共读） */
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String ext = extOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg/jpeg/png/webp 图片");
        }
        String objectName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream in = file.getInputStream()) {
            oss.putObject(props.getBucket(), objectName, in, null);
        } catch (Exception e) {
            throw new BusinessException("图片上传失败，请稍后重试");
        }
        // endpoint 形如 https://oss-cn-beijing.aliyuncs.com，虚拟主机风格拼出公网 URL
        String host = props.getEndpoint().replaceFirst("^https?://", "");
        return "https://" + props.getBucket() + "." + host + "/" + objectName;
    }

    private String extOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
