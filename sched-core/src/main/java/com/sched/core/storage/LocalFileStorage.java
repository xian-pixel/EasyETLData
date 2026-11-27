package com.sched.core.storage;

import com.sched.enmu.ResultValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 本地磁盘存储实现。
 * 安全约束：objectKey 完全由服务端生成（日期目录 + UUID），用户文件名不参与路径；
 * 读写前统一做 normalize + startsWith(root) 校验，防路径穿越。
 * 注意：多实例部署时本地盘不共享，需挂共享存储或切换对象存储实现。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sched.file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${sched.file.root-dir:./data/upload}") String rootDir) throws IOException {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("本地文件存储根目录初始化完成 root={}", root);
    }

    @Override
    public String store(InputStream in, String bizType, String ext) {
        String dir = LocalDate.now().toString().replace('-', '/');
        String fileName = UUID.randomUUID().toString().replace("-", "")
                + (ext == null || ext.isBlank() ? "" : "." + ext);
        String objectKey = bizType.toLowerCase() + "/" + dir + "/" + fileName;
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (IOException e) {
            log.error("写入本地文件失败 objectKey={}", objectKey, e);
            throw new FileStorageException(ResultValue.FILE_UPLOAD_FAILED, "文件保存失败");
        }
    }

    @Override
    public InputStream load(String objectKey) {
        Path target = resolve(objectKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("读取本地文件失败 objectKey={}", objectKey, e);
            throw new FileStorageException(ResultValue.DATA_NOT_FOUND, "文件不存在或已被清理");
        }
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 物理删除失败不影响业务主流程，仅记录，后续由清理任务兜底
            log.warn("删除本地文件失败 objectKey={}", objectKey, e);
        }
    }

    /**
     * 把 objectKey 解析为根目录下的绝对路径，并校验未越界
     */
    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new FileStorageException(ResultValue.PARAM_FORMAT_ERROR, "文件路径不合法");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new FileStorageException(ResultValue.PARAM_FORMAT_ERROR, "文件路径不合法");
        }
        return target;
    }
}
