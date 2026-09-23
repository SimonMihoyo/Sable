package me.kirara.sable.assessment.engine;

import me.kirara.sable.assessment.entity.Assessment;
import me.kirara.sable.assessment.entity.AssessmentItem;
import org.springframework.stereotype.Component;

import java.util.List;

/** 合规评估引擎实现（脚手架占位）。 */
@Component
public class AssessmentEngineImpl implements AssessmentEngine {

    @Override
    public void autoEvaluate(Assessment assessment, List<AssessmentItem> items) {
        // TODO 依据自动化检查项（如是否启用审计、是否分类分级）判定 PASSED / FAILED
    }

    @Override
    public void recalculate(Assessment assessment, List<AssessmentItem> items) {
        // TODO 加权计算百分制得分并映射 A/B/C/D 等级
    }
}
