package com.tianji.exam.domain.vo;

import com.tianji.api.dto.exam.QuestionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "考试明细")
public class AdminExamDetailVO {

    private Long id;

    private Boolean correct;

    private Integer score;

    private String answer;

    private String comment;

    private QuestionDTO question;
}
