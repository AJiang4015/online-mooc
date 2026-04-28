package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.msg.SignInMessage;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;
    private final RabbitMqHelper mqHelper;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            throw new BadRequestException("课程未加入课表");
        }
        List<LearningRecord> recordList = this.lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setRecords(BeanUtils.copyList(recordList, LearningRecordDTO.class));
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        return dto;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        Long userId = UserContext.getUser();
        boolean finished;
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            finished = handleVideoRecord(userId, dto);
        } else {
            finished = handleExamRecord(userId, dto);
        }
        if (!finished) {
            return;
        }
        handleLessonData(dto);
    }

    private void handleLessonData(LearningRecordFormDTO dto) {
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课表不存在");
        }
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cinfo == null) {
            throw new BizIllegalException("课程不存在");
        }
        Integer sectionNum = cinfo.getSectionNum();
        Integer learnedSections = lesson.getLearnedSections();
        boolean allFinished = learnedSections + 1 >= sectionNum;

        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(), dto.getSectionId());
        if (learningRecord == null) {
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(userId);
            boolean result = this.save(record);
            if (!result) {
                throw new DbException("create video learning record failed");
            }
            return false;
        }
        boolean isFinished = !Boolean.TRUE.equals(learningRecord.getFinished()) && dto.getMoment() * 2 >= dto.getDuration();
        if (!isFinished) {
            LearningRecord record = new LearningRecord();
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            record.setId(learningRecord.getId());
            taskHandler.addLearningRecordTask(record);
            return false;
        }

        boolean result = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(true, LearningRecord::getFinished, true)
                .set(true, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if (!result) {
            throw new DbException("update video learning record failed");
        }

        taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.LEARN_SECTION,
                SignInMessage.of(userId, LearningConstants.REWARD_LEARN_SECTION)
        );
        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        if (cache != null) {
            return cache;
        }
        List<LearningRecord> dbRecords = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .list();
        if (dbRecords == null || dbRecords.isEmpty()) {
            return null;
        }
        LearningRecord dbRecord = dbRecords.stream()
                .reduce(this::mergeLearningRecord)
                .orElse(null);
        if (dbRecord == null) {
            return null;
        }
        taskHandler.writeRecordCache(dbRecord);
        return dbRecord;
    }

    private LearningRecord mergeLearningRecord(LearningRecord r1, LearningRecord r2) {
        boolean finished1 = Boolean.TRUE.equals(r1.getFinished());
        boolean finished2 = Boolean.TRUE.equals(r2.getFinished());
        if (finished1 != finished2) {
            return finished1 ? r1 : r2;
        }
        return safeMoment(r1) >= safeMoment(r2) ? r1 : r2;
    }

    private int safeMoment(LearningRecord record) {
        return record.getMoment() == null ? 0 : record.getMoment();
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(), dto.getSectionId());
        if (learningRecord != null) {
            if (Boolean.TRUE.equals(learningRecord.getFinished())) {
                return false;
            }
            boolean result = this.lambdaUpdate()
                    .set(LearningRecord::getFinished, true)
                    .set(LearningRecord::getFinishTime, dto.getCommitTime())
                    .eq(LearningRecord::getId, learningRecord.getId())
                    .update();
            if (!result) {
                throw new DbException("update exam learning record failed");
            }
            taskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
            return true;
        }

        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        record.setUserId(userId);
        boolean result = this.save(record);
        if (!result) {
            throw new DbException("create exam learning record failed");
        }
        return true;
    }
}
