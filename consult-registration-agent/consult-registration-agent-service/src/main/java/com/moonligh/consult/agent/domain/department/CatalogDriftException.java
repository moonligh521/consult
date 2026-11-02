package com.moonligh.consult.agent.domain.department;

import java.util.List;

/**
 * 种子目录有挂词，库里没有，而且对应二级科从未被管理端改过。
 */
public class CatalogDriftException extends IllegalStateException {

    public CatalogDriftException(List<String> gaps) {
        super("目录漏同步，拒绝替换快照: " + String.join("；", gaps.stream().limit(8).toList()));
    }
}
