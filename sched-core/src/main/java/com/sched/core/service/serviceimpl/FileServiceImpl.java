package com.sched.core.service.serviceimpl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sched.core.dto.FileContent;
import com.sched.core.dto.vo.FileVO;
import com.sched.core.service.FileService;
import com.sched.core.storage.FileStorage;
import com.sched.core.storage.FileStorageException;
import com.sched.core.support.BizException;
import com.sched.core.support.CurrentUserProvider;
import com.sched.dao.entity.FileRecord;
import com.sched.dao.mapper.FileRecordMapper;
import com.sched.enmu.ResultValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件服务实现。
 * 安全要点：
 * 1) 扩展名白名单 + 大小上限，杜绝可执行文件；
 * 2) objectKey 由 FileStorage 生成，用户文件名不参与路径，防路径穿越；
 * 3) 下载/删除一律校验 uploader_id，防越权拉取他人文件；
 * 4) 删除只做逻辑删除，物理文件由清理任务按删除时间回收，避免误删仍被引用的文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final long NOT_DELETED = 0L;
    private static final long STATUS_VALID = 1L;

    /** 头像允许的图片类型 */
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    /** 普通附件允许的类型 */
    private static final Set<String> DOC_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip");
    /** 业务类型白名单，防止按前端传值创建任意目录 */
    private static final Set<String> BIZ_TYPES = Set.of("AVATAR", "ATTACHMENT", "EXPORT");

    private static final String BIZ_AVATAR = "AVATAR";
    private static final long AVATAR_MAX_SIZE = 2L * 1024 * 1024;
    private static final long DEFAULT_MAX_SIZE = 10L * 1024 * 1024;

    private final FileRecordMapper fileRecordMapper;
    private final FileStorage fileStorage;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(MultipartFile file, String bizType, Long bizId) {
        Long uploaderId = currentUserProvider.currentUserId();
        String type = normalizeBizType(bizType);

        if (file == null || file.isEmpty()) {
            throw new BizException(ResultValue.PARAM_MISSING, "上传文件不能为空");
        }
        String originalName = StringUtils.cleanPath(
                StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed");
        if (originalName.contains("..")) {
            throw new BizException(ResultValue.PARAM_FORMAT_ERROR, "文件名不合法");
        }
        String ext = StringUtils.getFilenameExtension(originalName);
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);

        boolean avatar = BIZ_AVATAR.equals(type);
        boolean extAllowed = avatar ? IMAGE_EXT.contains(ext) : (IMAGE_EXT.contains(ext) || DOC_EXT.contains(ext));
        if (!extAllowed) {
            throw new BizException(ResultValue.PARAM_FORMAT_ERROR, "不支持的文件类型：" + ext);
        }
        long maxSize = avatar ? AVATAR_MAX_SIZE : DEFAULT_MAX_SIZE;
        if (file.getSize() > maxSize) {
            throw new BizException(ResultValue.PARAM_FORMAT_ERROR, "文件大小超过限制：" + (maxSize / 1024 / 1024) + "MB");
        }

        String objectKey;
        try (InputStream in = file.getInputStream()) {
            objectKey = fileStorage.store(in, type.toLowerCase(Locale.ROOT), ext);
        } catch (IOException e) {
            log.error("读取上传文件流失败 originalName={} size={}", originalName, file.getSize(), e);
            throw new FileStorageException(ResultValue.FILE_UPLOAD_FAILED, "文件上传失败");
        }

        FileRecord record = new FileRecord();
        record.setBizType(type);
        record.setBizId(bizId);
        record.setUploaderId(uploaderId);
        record.setOriginalName(originalName);
        record.setExt(ext);
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setStorageType("LOCAL");
        record.setObjectKey(objectKey);
        record.setStatus((int) STATUS_VALID);
        record.setDeleted(NOT_DELETED);
        try {
            fileRecordMapper.insert(record);
        } catch (RuntimeException e) {
            // 元数据落库失败，补偿删除已写入的物理文件，避免产生孤儿文件
            fileStorage.delete(objectKey);
            log.error("文件元数据落库失败，已回滚物理文件 objectKey={}", objectKey, e);
            throw e;
        }
        log.info("文件上传成功 fileId={} uploaderId={} bizType={} size={}",
                record.getFileId(), uploaderId, type, record.getFileSize());
        return toVO(record);
    }

    @Override
    public IPage<FileVO> pageFiles(int pageNum, int pageSize) {
        Page<FileRecord> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        fileRecordMapper.selectPage(page, Wrappers.<FileRecord>lambdaQuery()
                .eq(FileRecord::getUploaderId, currentUserProvider.currentUserId())
                .eq(FileRecord::getDeleted, NOT_DELETED)
                .orderByDesc(FileRecord::getCreateTime));

        Page<FileVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<FileVO> records = page.getRecords().stream().map(this::toVO).toList();
        result.setRecords(records);
        return result;
    }

    @Override
    public FileContent download(Long fileId) {
        FileRecord record = requireOwnedFile(fileId);
        return new FileContent(record, fileStorage.load(record.getObjectKey()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long fileId) {
        requireOwnedFile(fileId);
        fileRecordMapper.update(null, Wrappers.<FileRecord>lambdaUpdate()
                .eq(FileRecord::getFileId, fileId)
                .eq(FileRecord::getDeleted, NOT_DELETED)
                .set(FileRecord::getDeleted, System.currentTimeMillis())
                .set(FileRecord::getStatus, 0));
        log.info("逻辑删除文件 fileId={}", fileId);
    }

    /**
     * 查询有效且属于当前用户的文件；他人文件一律按"无权限"处理，不泄露存在性
     */
    private FileRecord requireOwnedFile(Long fileId) {
        FileRecord record = fileRecordMapper.selectOne(Wrappers.<FileRecord>lambdaQuery()
                .eq(FileRecord::getFileId, fileId)
                .eq(FileRecord::getDeleted, NOT_DELETED)
                .eq(FileRecord::getStatus, STATUS_VALID));
        if (record == null) {
            throw new BizException(ResultValue.DATA_NOT_FOUND, "文件不存在");
        }
        if (!record.getUploaderId().equals(currentUserProvider.currentUserId())) {
            throw new BizException(ResultValue.NO_PERMISSION, "无权访问该文件");
        }
        return record;
    }

    private String normalizeBizType(String bizType) {
        String type = StringUtils.hasText(bizType) ? bizType.toUpperCase(Locale.ROOT) : "ATTACHMENT";
        if (!BIZ_TYPES.contains(type)) {
            throw new BizException(ResultValue.PARAM_FORMAT_ERROR, "不支持的业务类型");
        }
        return type;
    }

    private FileVO toVO(FileRecord record) {
        FileVO vo = new FileVO();
        vo.setFileId(record.getFileId());
        vo.setBizType(record.getBizType());
        vo.setOriginalName(record.getOriginalName());
        vo.setExt(record.getExt());
        vo.setContentType(record.getContentType());
        vo.setFileSize(record.getFileSize());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private long normalizePageNum(int pageNum) {
        return pageNum < 1 ? 1 : pageNum;
    }

    private long normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }
}
