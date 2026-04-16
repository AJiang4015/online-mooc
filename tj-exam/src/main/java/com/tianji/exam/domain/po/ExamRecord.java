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
@TableName("exam_record")
public class ExamRecord implements Serializable {

    @TableId("id")
    private Long id;

    private Integer type;

    private Long courseId;

    private Long sectionId;

    private Long userId;

    private Integer score;

    private Integer correctQuestions;

    private Integer duration;

    private String comment;

    private Boolean finished;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;

    private LocalDateTime updateTime;
}
