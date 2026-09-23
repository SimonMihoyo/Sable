package me.kirara.sable.classify.engine;

import me.kirara.sable.classify.entity.ClassifyRule;
import java.util.List;

/**
 * 分类分级引擎 — 依据规则集对字段样本进行敏感级别判定。
 */
public interface ClassifyEngine {

    /**
     * 对单个字段进行识别，返回命中的规则（按优先级从高到低）。
     *
     * @param columnName 字段名
     * @param columnComment 字段注释
     * @param samples 采样值
     * @return 命中的规则列表，未命中返回空列表
     */
    List<ClassifyRule> match(String columnName, String columnComment, List<String> samples);
}
