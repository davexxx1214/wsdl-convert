package com.example.wsdlconverter.exception;

/**
 * WSDL服务异常类
 * 
 * 用于包装WSDL服务调用过程中发生的异常
 */
public class WsdlServiceException extends RuntimeException {

    public WsdlServiceException(String message) {
        super(message);
    }

    public WsdlServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public WsdlServiceException(Throwable cause) {
        super(cause);
    }
}
