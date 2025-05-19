package com.mvd.docsearchmvd.model;

import com.google.gson.annotations.SerializedName;

public class ApiResponse<T> {
    @SerializedName("type")
    private String type;
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

    public ApiResponse(String type, T data) {
        this.success = true;
        this.data = data;
        this.type = type;
    }

    public ApiResponse(String error, String errorDetails) {
        this.success = false;
        this.error = error;
        this.errorDetails = errorDetails;
    }

    public ApiResponse(String type, String error, String errorDetails) {
        this.success = false;
        this.error = error;
        this.errorDetails = errorDetails;
        this.type = type;
    }

}