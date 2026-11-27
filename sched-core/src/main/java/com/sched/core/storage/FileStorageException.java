package com.sched.core.storage;

import com.sched.enmu.ResultValue;
import com.sched.core.support.BizException;

/**
 * 存储层异常，对外统一收敛为"文件上传失败"，细节只进日志。
 */
public class FileStorageException extends BizException {

    public FileStorageException(ResultValue resultValue, String msg) {
        super(resultValue, msg);
    }
}
