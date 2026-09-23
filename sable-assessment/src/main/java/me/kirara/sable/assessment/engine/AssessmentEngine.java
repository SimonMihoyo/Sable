package me.kirara.sable.assessment.engine;

import me.kirara.sable.assessment.entity.Assessment;
import me.kirara.sable.assessment.entity.AssessmentItem;
import java.util.List;

/**
 * 合规评估引擎 — 对 check_type = AUTO 的检查项执行自动判定并汇总得分。
 */
public interface AssessmentEngine {

    /**
     * 对评估任务下的检查项执行自动评估。
     *
     * @param assessment 评估任务
     * @param items 待评估检查项
     */
    void autoEvaluate(Assessment assessment, List<AssessmentItem> items);

    /**
     * 依据检查项结果重新计算得分与等级，并回写任务汇总字段。
     */
    void recalculate(Assessment assessment, List<AssessmentItem> items);
}
