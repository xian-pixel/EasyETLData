package com.sched.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sched.annotation.OperLog;
import com.sched.core.dto.FileContent;
import com.sched.core.dto.vo.FileVO;
import com.sched.core.service.FileService;
import com.sched.core.support.BizException;
import com.sched.dao.entity.FileRecord;
import com.sched.entity.dto.vo.Result;
import com.sched.enmu.ResultValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件上传下载接口。
 * 安全要点：
 * 1) 上传目录不对外暴露为静态资源，所有访问必须走 /api/file/**，由服务层按 uploader_id 鉴权；
 * 2) 下载统一加 nosniff，文件名按 RFC 5987 编码，避免中文乱码与响应头注入；
 * 3) 非图片一律强制 attachment，防止在浏览器内联执行 HTML/SVG。
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** 上传文件：bizType 可选（默认 ATTACHMENT），bizId 可选 */
    @OperLog(module = "文件管理", action = "上传文件")
    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String bizType,
                                 @RequestParam(required = false) Long bizId) {
        return Result.success(fileService.upload(file, bizType, bizId));
    }

    /** 当前用户的文件分页 */
    @GetMapping("/page")
    public Result<IPage<FileVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(fileService.pageFiles(pageNum, pageSize));
    }

    /** 下载：强制附件下载 */
    @OperLog(module = "文件管理", action = "下载文件", recordParams = false)
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long fileId) {
        FileContent content = fileService.download(fileId);
        FileRecord meta = content.meta();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFileName(meta))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(meta.getFileSize() == null ? -1 : meta.getFileSize())
                .body(new InputStreamResource(content.stream()));
    }

    /** 预览：仅允许图片内联展示，其余一律拒绝，避免 SVG/HTML 反射型 XSS */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable Long fileId) {
        FileContent content = fileService.download(fileId);
        FileRecord meta = content.meta();
        String contentType = meta.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BizException(ResultValue.PARAM_FORMAT_ERROR, "该文件不支持在线预览，请下载查看");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodeFileName(meta))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(meta.getFileSize() == null ? -1 : meta.getFileSize())
                .body(new InputStreamResource(content.stream()));
    }

    /** 逻辑删除自己的文件 */
    @OperLog(module = "文件管理", action = "删除文件", recordParams = false)
    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@PathVariable Long fileId) {
        fileService.delete(fileId);
        return Result.success();
    }

    private String encodeFileName(FileRecord meta) {
        String name = meta.getOriginalName() == null ? "download" : meta.getOriginalName();
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
