package com.sched.core.service.synctask;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sched.core.dto.synctask.SyncTaskCreateDTO;
import com.sched.core.dto.synctask.SyncTaskUpdateDTO;
import com.sched.core.vo.synctask.SyncTaskVO;
import com.sched.dao.entity.synctask.SyncTask;

/**
 * 同步任务配置服务。
 * 继承 IService 后自动拥有通用 CRUD，下面只声明本业务的特殊方法。
 */
public interface SyncTaskService extends IService<SyncTask> {

    /** 新增任务，返回生成的 taskId */
    String create(SyncTaskCreateDTO dto);

    /** 修改任务配置（taskCode 不可改） */
    void update(String taskId, SyncTaskUpdateDTO dto);

    /** 逻辑删除任务 */
    void delete(String taskId);

    /** 任务详情 */
    SyncTaskVO detail(String taskId);

    /** 分页查询（自动过滤已删除） */
    IPage<SyncTaskVO> page(int pageNum, int pageSize, String keyword);

    /** 启用 / 停用任务 */
    void changeStatus(String taskId, boolean enabled);
}
