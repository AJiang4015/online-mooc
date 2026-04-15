package com.tianji.pay.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("reconciliation_record")
public class ReconciliationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long bizOrderNo;

    private Long payOrderNo;

    private Long refundOrderNo;

    private String payChannelCode;

    private Integer amount;

    private Integer refundAmount;

    private Integer reconciliationStatus;

    private LocalDateTime reconciliationTime;

    private String resultCode;

    private String resultMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long creater;

    private Long updater;

    private Integer deleted;
}
