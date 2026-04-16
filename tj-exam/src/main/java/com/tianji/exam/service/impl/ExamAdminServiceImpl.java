package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.exam.QuestionDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.exam.domain.dto.ExamCommentDTO;
import com.tianji.exam.domain.po.ExamRecord;
import com.tianji.exam.domain.po.ExamRecordDetail;
import com.tianji.exam.domain.query.ExamAdminPageQuery;
import com.tianji.exam.domain.vo.AdminExamDetailVO;
import com.tianji.exam.domain.vo.AdminExamPageVO;
import com.tianji.exam.mapper.ExamRecordMapper;
import com.tianji.exam.mapper.ExamRecordDetailMapper;
import com.tianji.exam.service.IExamAdminService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamAdminServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements IExamAdminService {

    private final ExamRecordDetailMapper examRecordDetailMapper;
    private final IQuestionService questionService;
    private final UserClient userClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;

    @Override
    public PageDTO<AdminExamPageVO> queryExamPage(ExamAdminPageQuery query) {
        Page<ExamRecord> page = lambdaQuery()
                .eq(query.getType() != null, ExamRecord::getType, query.getType())
                .orderByDesc(ExamRecord::getFinishTime)
                .orderByDesc(ExamRecord::getId)
                .page(query.toMpPage("finish_time", false));
        List<ExamRecord> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        Set<Long> userIds = records.stream().map(ExamRecord::getUserId).collect(Collectors.toSet());
        Set<Long> courseIds = records.stream().map(ExamRecord::getCourseId).collect(Collectors.toSet());
        Set<Long> sectionIds = records.stream().map(ExamRecord::getSectionId).collect(Collectors.toSet());

        Map<Long, UserDTO> userMap = toMap(userClient.queryUserByIds(userIds), UserDTO::getId);
        Map<Long, CourseSimpleInfoDTO> courseMap =
                toMap(courseClient.getSimpleInfoList(courseIds), CourseSimpleInfoDTO::getId);
        Map<Long, CataSimpleInfoDTO> cataMap =
                toMap(catalogueClient.batchQueryCatalogue(sectionIds), CataSimpleInfoDTO::getId);

        List<AdminExamPageVO> list = records.stream().map(record -> {
            AdminExamPageVO vo = new AdminExamPageVO();
            vo.setId(record.getId());
            vo.setType(record.getType());
            vo.setScore(record.getScore());
            vo.setDuration(record.getDuration());
            vo.setFinishTime(record.getFinishTime());
            UserDTO user = userMap.get(record.getUserId());
            if (user != null) {
                vo.setName(user.getName());
                vo.setIcon(user.getIcon());
            }
            CourseSimpleInfoDTO course = courseMap.get(record.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
            }
            CataSimpleInfoDTO cata = cataMap.get(record.getSectionId());
            if (cata != null) {
                vo.setSectionName(cata.getName());
            }
            return vo;
        }).toList();
        return PageDTO.of(page, list);
    }

    @Override
    public List<AdminExamDetailVO> queryExamDetails(Long examId) {
        List<ExamRecordDetail> details = examRecordDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExamRecordDetail>()
                        .eq(ExamRecordDetail::getExamId, examId)
                        .orderByAsc(ExamRecordDetail::getId)
        );
        if (CollUtils.isEmpty(details)) {
            return CollUtils.emptyList();
        }
        List<Long> questionIds = details.stream().map(ExamRecordDetail::getQuestionId).toList();
        Map<Long, QuestionDTO> questionMap = questionService.queryQuestionByIds(questionIds).stream()
                .collect(Collectors.toMap(QuestionDTO::getId, Function.identity()));
        List<AdminExamDetailVO> result = new ArrayList<>(details.size());
        for (ExamRecordDetail detail : details) {
            AdminExamDetailVO vo = new AdminExamDetailVO();
            vo.setId(detail.getId());
            vo.setCorrect(detail.getCorrect());
            vo.setScore(detail.getScore());
            vo.setAnswer(detail.getAnswer());
            vo.setComment(detail.getComment());
            vo.setQuestion(questionMap.get(detail.getQuestionId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void saveComments(List<ExamCommentDTO> comments) {
        if (CollUtils.isEmpty(comments)) {
            return;
        }
        List<ExamRecordDetail> details = comments.stream()
                .filter(item -> item.getId() != null)
                .map(item -> new ExamRecordDetail().setId(item.getId()).setComment(item.getComment()))
                .toList();
        if (CollUtils.isEmpty(details)) {
            return;
        }
        details.forEach(examRecordDetailMapper::updateById);
    }

    private static <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyMapper) {
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
    }
}
