package com.moonligh.consult.agent.domain.safety;

/**
 * 目录校验或自检失败。应用必须停止启动。
 */
public class CatalogException extends RuntimeException {

    /** 目录本身有问题，例如缺模板、正例没命中。 */
    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
