package com.moonligh.consult.agent.domain.safety;

/**
 * 目录校验或自检失败。应用必须停止启动。
 */
public class CatalogException extends RuntimeException {

    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
