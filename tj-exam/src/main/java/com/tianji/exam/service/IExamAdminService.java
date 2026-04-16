package com.tianji.exam.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.ExamCommentDTO;
import com.tianji.exam.domain.query.ExamAdminPageQuery;
import com.tianji.exam.domain.vo.AdminExamDetailVO;
import com.tianji.exam.domain.vo.AdminExamPageVO;

import java.util.List;

public interface IExamAdminService {

    PageDTO<AdminExamPageVO> queryExamPage(ExamAdminPageQuery query);

    List<AdminExamDetailVO> queryExamDetails(Long examId);

    void saveComments(List<ExamCommentDTO> comments);
}
