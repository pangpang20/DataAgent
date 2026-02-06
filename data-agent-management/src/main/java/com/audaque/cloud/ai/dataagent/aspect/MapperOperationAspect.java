/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.audaque.cloud.ai.dataagent.aspect;

import com.audaque.cloud.ai.dataagent.exception.DataAccessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Mapper操作监控切面
 * 统一处理Mapper方法的返回值检查和异常处理
 */
@Slf4j
@Aspect
@Component
public class MapperOperationAspect {

    /**
     * 拦截所有Mapper接口的insert/update/delete方法
     */
    @Around("execution(* com.audaque.cloud.ai.dataagent.mapper.*.*(..)) " +
            "&& (execution(* *.insert*(..)) || execution(* *.update*(..)) || execution(* *.delete*(..)))")
    public Object monitorMapperOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        Object[] args = joinPoint.getArgs();
        
        try {
            log.debug("Executing mapper method: {}.{} with args: {}", className, methodName, args);
            
            Object result = joinPoint.proceed();
            
            // 检查返回值
            if (result instanceof Integer) {
                int affectedRows = (Integer) result;
                logOperationResult(className, methodName, affectedRows, args);
                
                // 对于修改操作，如果影响行数为0，记录警告
                if (affectedRows == 0 && isModifyOperation(methodName)) {
                    log.warn("Mapper operation affected 0 rows: {}.{} with args: {}", 
                            className, methodName, args);
                }
            } else if (result == null && isInsertOperation(methodName)) {
                log.warn("Insert operation returned null: {}.{}", className, methodName);
            }
            
            return result;
        } catch (Exception e) {
            log.error("Mapper operation failed: {}.{} with args: {}", 
                    className, methodName, args, e);
            throw new DataAccessException("数据库操作失败: " + methodName, e);
        }
    }
    
    /**
     * 记录操作结果
     */
    private void logOperationResult(String className, String methodName, int affectedRows, Object[] args) {
        if (affectedRows > 0) {
            if (isInsertOperation(methodName)) {
                log.debug("Insert operation successful: {}.{} affected {} row(s)", 
                        className, methodName, affectedRows);
            } else if (isUpdateOperation(methodName)) {
                log.debug("Update operation successful: {}.{} affected {} row(s)", 
                        className, methodName, affectedRows);
            } else if (isDeleteOperation(methodName)) {
                log.debug("Delete operation successful: {}.{} affected {} row(s)", 
                        className, methodName, affectedRows);
            }
        }
    }
    
    /**
     * 判断是否为插入操作
     */
    private boolean isInsertOperation(String methodName) {
        return methodName.toLowerCase().startsWith("insert") || 
               methodName.toLowerCase().startsWith("add") ||
               methodName.toLowerCase().startsWith("create");
    }
    
    /**
     * 判断是否为更新操作
     */
    private boolean isUpdateOperation(String methodName) {
        return methodName.toLowerCase().startsWith("update") ||
               methodName.toLowerCase().startsWith("modify") ||
               methodName.toLowerCase().startsWith("change");
    }
    
    /**
     * 判断是否为删除操作
     */
    private boolean isDeleteOperation(String methodName) {
        return methodName.toLowerCase().startsWith("delete") ||
               methodName.toLowerCase().startsWith("remove");
    }
    
    /**
     * 判断是否为修改操作（更新/删除）
     */
    private boolean isModifyOperation(String methodName) {
        return isUpdateOperation(methodName) || isDeleteOperation(methodName);
    }
}

