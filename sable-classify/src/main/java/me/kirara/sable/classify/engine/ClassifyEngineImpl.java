package me.kirara.sable.classify.engine;

import me.kirara.sable.classify.entity.ClassifyRule;
import org.springframework.stereotype.Component;

import java.util.List;

/** 分类分级引擎实现（脚手架占位，待实现正则 / 关键词 / 采样匹配）。 */
@Component
public class ClassifyEngineImpl implements ClassifyEngine {

    @Override
    public List<ClassifyRule> match(String columnName, String columnComment, List<String> samples) {
        // TODO 按 priority 顺序执行 REGEX / KEYWORD / SAMPLE / COMPOSITE 匹配
        return List.of();
    }
}
