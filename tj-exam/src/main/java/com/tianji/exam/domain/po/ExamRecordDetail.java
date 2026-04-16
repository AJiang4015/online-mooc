package com.tianji.exam.domain.po;

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
@TableName("exam_record_detail")
public class ExamRecordDetail implements Serializable {

    @TableId("id")
    private Long id;

    private Long examId;

    private Long questionId;

    private Boolean correct;

    private Integer score;

    private String answer;

    private String comment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
