package com.tianji.pay.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.pay.domain.po.ReconciliationRecord;
import com.tianji.pay.domain.query.ReconciliationPageQuery;
import com.tianji.pay.service.IReconciliationRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "对账记录相关接口")
@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final IReconciliationRecordService reconciliationRecordService;

    @Operation(summary = "分页查询对账记录")
    @GetMapping("/list")
    public PageDTO<ReconciliationRecord> queryReconciliationList(ReconciliationPageQuery query) {
        return reconciliationRecordService.queryByPage(query);
    }

    @Operation(summary = "查询对账记录详情")
    @GetMapping("/{id}")
    public ReconciliationRecord queryReconciliationDetail(
            @Parameter(description = "对账记录id") @PathVariable("id") Long id) {
        return reconciliationRecordService.queryDetail(id);
    }
}
