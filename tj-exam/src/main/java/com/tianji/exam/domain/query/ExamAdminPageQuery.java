package com.tianji.exam.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "考试管理分页查询条件")
public class ExamAdminPageQuery extends PageQuery {

    @Schema(description = "考试类型：1-练习，2-考试")
    private Integer type;
}
