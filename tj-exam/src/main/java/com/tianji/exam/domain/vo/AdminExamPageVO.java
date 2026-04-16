package com.tianji.exam.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "考试管理分页数据")
public class AdminExamPageVO {

    private Long id;

    private String icon;

    private String name;

    private String courseName;

    private String sectionName;

    private Integer type;

    private Integer score;

    private Integer duration;

    private LocalDateTime finishTime;
}
