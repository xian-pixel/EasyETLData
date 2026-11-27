package com.sched.core.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sched.core.dto.FileContent;
import com.sched.core.dto.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务：上传、下载、分页、逻辑删除。
 * 所有操作都以当前登录用户为归属，禁止跨用户访问。
 */
public interface FileService {

    /**
     * 上传文件
     *
     * @param file    上传文件
     * @param bizType AVATAR / ATTACHMENT
     * @param bizId   关联业务ID，可为空
     */
    FileVO upload(MultipartFile file, String bizType, Long bizId);

    /** 当前用户文件分页 */
    IPage<FileVO> pageFiles(int pageNum, int pageSize);

    /** 下载/预览：返回元数据与文件流，流由调用方关闭 */
    FileContent download(Long fileId);

    /** 逻辑删除，仅允许操作自己的文件 */
    void delete(Long fileId);
}
