package com.sched.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncReturnVo<T> {
    private String taskCode;
    private T rows;
}
