package com.tianji.exam.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "考试评语")
public class ExamCommentDTO {

    @Schema(description = "考试明细id")
    private Long id;

    @Schema(description = "教师评语")
    private String comment;
}
