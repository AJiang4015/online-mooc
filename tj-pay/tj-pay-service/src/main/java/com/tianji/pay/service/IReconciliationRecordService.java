package com.tianji.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.pay.domain.po.ReconciliationRecord;
import com.tianji.pay.domain.query.ReconciliationPageQuery;

public interface IReconciliationRecordService extends IService<ReconciliationRecord> {

    PageDTO<ReconciliationRecord> queryByPage(ReconciliationPageQuery query);

    ReconciliationRecord queryDetail(Long id);
}
