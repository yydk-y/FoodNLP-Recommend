package com.SFood.common.dto;

import lombok.Data;

/**
 * 统一响应结果DTO
 */
@Data
public class ResultDTO<T> {
    
    private Integer code;
    private String message;
    private T data;
    
    public static <T> ResultDTO<T> success(T data) {
        ResultDTO<T> result = new ResultDTO<>();
        result.setCode(200);
        result.setMessage("成功");
        result.setData(data);
        return result;
    }
    
    public static <T> ResultDTO<T> success(String message, T data) {
        ResultDTO<T> result = new ResultDTO<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
    
    public static ResultDTO<Void> error(String message) {
        ResultDTO<Void> result = new ResultDTO<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }
    
    public static ResultDTO<Void> error(Integer code, String message) {
        ResultDTO<Void> result = new ResultDTO<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}