/**
 * 问诊平台公共能力，所有业务服务都依赖这一包。
 * <p>
 * 调用链：业务抛 {@link com.moonligh.consult.framework.BusinessException}
 * → {@link com.moonligh.consult.framework.GlobalExceptionHandler} 拦住
 * → 返回 {@link com.moonligh.consult.framework.Result}。
 * HTTP 状态固定 200，前端看 {@code code} 判断成败。
 */
package com.moonligh.consult.framework;
