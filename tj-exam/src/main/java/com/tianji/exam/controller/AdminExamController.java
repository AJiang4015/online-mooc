package com.tianji.exam.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.ExamCommentDTO;
import com.tianji.exam.domain.query.ExamAdminPageQuery;
import com.tianji.exam.domain.vo.AdminExamDetailVO;
import com.tianji.exam.domain.vo.AdminExamPageVO;
import com.tianji.exam.service.IExamAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "考试管理接口")
@RestController
@RequestMapping("/admin/exams")
@RequiredArgsConstructor
public class AdminExamController {

    private final IExamAdminService examAdminService;

    @Operation(summary = "分页查询考试记录")
    @GetMapping("/page")
    public PageDTO<AdminExamPageVO> queryExamPage(ExamAdminPageQuery query) {
        return examAdminService.queryExamPage(query);
    }

    @Operation(summary = "查询考试详情")
    @GetMapping("/{id}")
    public List<AdminExamDetailVO> queryExamDetails(@PathVariable("id") Long id) {
        return examAdminService.queryExamDetails(id);
    }

    @Operation(summary = "保存教师评语")
    @PostMapping("/comment")
    public void saveComments(@RequestBody List<ExamCommentDTO> comments) {
        examAdminService.saveComments(comments);
    }
}
