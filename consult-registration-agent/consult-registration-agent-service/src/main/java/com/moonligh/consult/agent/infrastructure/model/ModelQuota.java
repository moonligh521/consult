package com.moonligh.consult.agent.infrastructure.model;

/**
 * 所有实例合计的模型并发配额。单实例信号量之外再加一道，保证总并发不超过供应商账号限额。
 */
public interface ModelQuota {

    /** 不设全局配额。 */
    ModelQuota NONE = new ModelQuota() {
        @Override
        public Permit acquire(ModelPurpose purpose, long waitMs) {
            return Permit.UNLIMITED;
        }

        @Override
        public void release(Permit permit) {
        }
    };

    /**
     * 等最多 waitMs 拿一个许可。等满也没拿到返回 null；配额本身不可用时返回 {@link Permit#UNLIMITED}，
     * 由单实例信号量兜底，不因为协调组件故障把整条通道判成繁忙。
     */
    Permit acquire(ModelPurpose purpose, long waitMs);

    void release(Permit permit);

    /** id 为空表示未占用全局许可，释放时什么也不做。 */
    record Permit(ModelPurpose purpose, String id) {

        public static final Permit UNLIMITED = new Permit(null, null);
    }
}
