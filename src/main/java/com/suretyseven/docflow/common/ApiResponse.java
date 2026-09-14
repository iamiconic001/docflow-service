package com.suretyseven.docflow.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.suretyseven.docflow.constants.ResponseCodes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private String status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ResponseCodes.SUCCESS, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ResponseCodes.ERROR, message, null);
    }
}
