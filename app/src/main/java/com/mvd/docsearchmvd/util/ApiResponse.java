package com.mvd.docsearchmvd.util;

import com.google.gson.annotations.SerializedName;

public class ApiResponse<T> {
    @SerializedName("success")
    private boolean success;

    @SerializedName("data")
    private T data;

    @SerializedName("error")
    private String error;

    @SerializedName("errorDetails")
    private String errorDetails;

    public ApiResponse(T data) {
        this.success = true;
        this.data = data;
    }

    public ApiResponse(String error, String errorDetails) {
        this.success = false;
        this.error = error;
        this.errorDetails = errorDetails;
    }

}