package com.tianji.pay.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "对账记录分页查询条件")
public class ReconciliationPageQuery extends PageQuery {

    @Schema(description = "对账状态", example = "1")
    private Integer status;
}
