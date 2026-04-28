package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;

    @Override
    public void addUserLesson(Long userId, List<Long> courseIds) {
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        List<LearningLesson> list = new ArrayList<>();
        for (CourseSimpleInfoDTO cinfo : cinfos) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(cinfo.getId());

            Integer validDuration = cinfo.getValidDuration();
            if (validDuration != null) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            list.add(lesson);
        }
        this.saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        Long userId = UserContext.getUser();
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BadRequestException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        Map<Long, Integer> learnedSectionMap = queryLearnedSectionCountMap(
                userId, records.stream().map(LearningLesson::getId).collect(Collectors.toSet()));

        List<LearningLessonVO> voList = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            vo.setLearnedSections(learnedSectionMap.getOrDefault(record.getId(), 0));
            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId());
            if (infoDTO != null) {
                vo.setCourseName(infoDTO.getName());
                vo.setCourseCoverUrl(infoDTO.getCoverUrl());
                vo.setSections(infoDTO.getSectionNum());
            }
            voList.add(vo);
        }
        syncLearnedSectionCounts(records, learnedSectionMap);
        return PageDTO.of(page, voList);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }

        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BadRequestException("课程不存在");
        }
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, userId).count();

        Long latestSectionId = lesson.getLatestSectionId();
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BadRequestException("小节不存在");
        }

        Integer actualLearnedSections = queryLearnedSectionCountMap(userId, CollUtils.singletonList(lesson.getId()))
                .getOrDefault(lesson.getId(), 0);

        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setLearnedSections(actualLearnedSections);
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setSections(cinfo.getSectionNum());
        vo.setCourseAmount(count);
        CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());
        syncLearnedSectionCount(lesson, actualLearnedSections);
        return vo;
    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        Integer actualLearnedSections = queryLearnedSectionCountMap(userId, CollUtils.singletonList(lesson.getId()))
                .getOrDefault(lesson.getId(), 0);
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setLearnedSections(actualLearnedSections);
        syncLearnedSectionCount(lesson, actualLearnedSections);
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if (expireTime != null && now.isAfter(expireTime)) {
            return null;
        }
        return lesson.getId();
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return this.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count();
    }

    @Override
    public void createLearningPlan(LearningPlanDTO dto) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        if (lesson == null) {
            throw new BizIllegalException("该课程没有加入到课表");
        }
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        Long userId = UserContext.getUser();

        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.LEARNING, LessonStatus.NOT_BEGIN);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper);
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null) {
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }

        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);

        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;
        }

        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cinfosMap = cinfos.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        Set<Long> lessonIds = records.stream().map(LearningLesson::getId).collect(Collectors.toSet());
        Map<Long, Integer> learnedSectionMap = queryLearnedSectionCountMap(userId, lessonIds);
        Map<Long, Integer> courseWeekFinishNumMap = queryWeekLearnedSectionCountMap(userId, lessonIds, weekBeginTime, weekEndTime);

        int weekFinishedPlanNum = courseWeekFinishNumMap.values().stream().mapToInt(Integer::intValue).sum();

        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedPlanNum);
        List<LearningPlanVO> voList = new ArrayList<>();

        for (LearningLesson record : records) {
            LearningPlanVO planVO = BeanUtils.copyBean(record, LearningPlanVO.class);
            planVO.setLearnedSections(learnedSectionMap.getOrDefault(record.getId(), 0));
            CourseSimpleInfoDTO infoDTO = cinfosMap.get(record.getCourseId());
            if (infoDTO != null) {
                planVO.setCourseName(infoDTO.getName());
                planVO.setSections(infoDTO.getSectionNum());
            }
            planVO.setWeekLearnedSections(courseWeekFinishNumMap.getOrDefault(record.getId(), 0));
            voList.add(planVO);
        }
        syncLearnedSectionCounts(records, learnedSectionMap);
        vo.setList(voList);
        vo.setTotal(page.getTotal());
        vo.setPages(page.getPages());
        return vo;
    }

    @Override
    public void deleteMyLessons(Long id) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = getById(id);
        if (!lesson.getUserId().equals(userId)) {
            throw new BizIllegalException("只能删除自己的课程");
        }
        if (lesson.getStatus() != LessonStatus.EXPIRED) {
            throw new BizIllegalException("只能删除状态为已过期的课程");
        }
        baseMapper.deleteById(id);
    }

    private Map<Long, Integer> queryLearnedSectionCountMap(Long userId, Collection<Long> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.select("lesson_id as lessonId", "count(distinct section_id) as sectionId");
        wrapper.eq("user_id", userId);
        wrapper.eq("finished", true);
        wrapper.in("lesson_id", lessonIds);
        wrapper.groupBy("lesson_id");
        List<LearningRecord> records = recordMapper.selectList(wrapper);
        if (CollUtils.isEmpty(records)) {
            return Collections.emptyMap();
        }
        return records.stream().collect(Collectors.toMap(
                LearningRecord::getLessonId,
                record -> record.getSectionId() == null ? 0 : record.getSectionId().intValue()
        ));
    }

    private Map<Long, Integer> queryWeekLearnedSectionCountMap(
            Long userId, Collection<Long> lessonIds, LocalDateTime weekBeginTime, LocalDateTime weekEndTime) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<LearningRecord> wrapper = new QueryWrapper<>();
        wrapper.select("lesson_id as lessonId", "count(distinct section_id) as sectionId");
        wrapper.eq("user_id", userId);
        wrapper.eq("finished", true);
        wrapper.between("finish_time", weekBeginTime, weekEndTime);
        wrapper.in("lesson_id", lessonIds);
        wrapper.groupBy("lesson_id");
        List<LearningRecord> records = recordMapper.selectList(wrapper);
        if (CollUtils.isEmpty(records)) {
            return Collections.emptyMap();
        }
        return records.stream().collect(Collectors.toMap(
                LearningRecord::getLessonId,
                record -> record.getSectionId() == null ? 0 : record.getSectionId().intValue()
        ));
    }

    private void syncLearnedSectionCounts(List<LearningLesson> lessons, Map<Long, Integer> learnedSectionMap) {
        if (CollUtils.isEmpty(lessons)) {
            return;
        }
        for (LearningLesson lesson : lessons) {
            syncLearnedSectionCount(lesson, learnedSectionMap.getOrDefault(lesson.getId(), 0));
        }
    }

    private void syncLearnedSectionCount(LearningLesson lesson, Integer actualLearnedSections) {
        int actual = actualLearnedSections == null ? 0 : actualLearnedSections;
        int stored = lesson.getLearnedSections() == null ? 0 : lesson.getLearnedSections();
        if (stored == actual) {
            return;
        }
        this.lambdaUpdate()
                .set(LearningLesson::getLearnedSections, actual)
                .eq(LearningLesson::getId, lesson.getId())
                .update();
        lesson.setLearnedSections(actual);
    }
}
