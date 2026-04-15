package com.tianji.pay.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.pay.domain.po.ReconciliationRecord;
import com.tianji.pay.domain.query.ReconciliationPageQuery;
import com.tianji.pay.mapper.ReconciliationRecordMapper;
import com.tianji.pay.service.IReconciliationRecordService;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationRecordServiceImpl
        extends ServiceImpl<ReconciliationRecordMapper, ReconciliationRecord>
        implements IReconciliationRecordService {

    @Override
    public PageDTO<ReconciliationRecord> queryByPage(ReconciliationPageQuery query) {
        Page<ReconciliationRecord> page = lambdaQuery()
                .eq(query.getStatus() != null, ReconciliationRecord::getReconciliationStatus, query.getStatus())
                .page(query.toMpPage("id", false));
        return PageDTO.of(page);
    }

    @Override
    public ReconciliationRecord queryDetail(Long id) {
        return lambdaQuery()
                .eq(ReconciliationRecord::getId, id)
                .one();
    }
}
