package com.tianji.search.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.search.config.InterestsProperties;
import com.tianji.search.constants.SearchErrorInfo;
import com.tianji.search.domain.po.Course;
import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.search.repository.CourseRepository;
import com.tianji.search.service.IInterestsService;
import com.tianji.search.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.search.repository.CourseRepository.PUBLISH_TIME;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements ISearchService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DateUtils.DEFAULT_DATE_TIME_FORMAT);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final IInterestsService interestsService;
    private final UserClient userClient;
    private final CategoryCache categoryCache;
    private final InterestsProperties interestsProperties;

    @Override
    public List<CourseVO> queryCourseByCateId(Long cateLv2Id) {
        return queryTopNByCategoryIdLv2sAndFree(
                CollUtils.singletonList(cateLv2Id), null, PUBLISH_TIME, false, 10);
    }

    @Override
    public List<CourseVO> queryBestTopN() {
        return queryTopNCourseOnMarketByFree(false, CourseRepository.SOLD);
    }

    @Override
    public List<CourseVO> queryNewTopN() {
        return queryTopNCourseOnMarketByFree(false, PUBLISH_TIME);
    }

    @Override
    public List<CourseVO> queryFreeTopN() {
        return queryTopNCourseOnMarketByFree(true, CourseRepository.SOLD);
    }

    private List<CourseVO> queryTopNCourseOnMarketByFree(boolean isFree, String sortBy) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return queryTopNByCategoryIdLv2sAndFree(
                    null, isFree, sortBy, false, interestsProperties.getTopNumber());
        }
        List<Long> categoryIds = interestsService.queryMyInterestsIds();
        if (CollUtils.isEmpty(categoryIds)) {
            return queryTopNByCategoryIdLv2sAndFree(
                    null, isFree, sortBy, false, interestsProperties.getTopNumber());
        }
        return queryTopNByCategoryIdLv2sAndFree(
                categoryIds, isFree, sortBy, false, interestsProperties.getTopNumber());
    }

    private List<CourseVO> queryTopNByCategoryIdLv2sAndFree(
            List<Long> categoryIds, Boolean isFree, String sortBy, boolean isASC, int n) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("size", Math.min(n, 1000));
            body.set("query", buildTopNQuery(categoryIds, isFree));
            addSort(body, normalizeSortField(sortBy), isASC);
            SearchResultData result = executeSearch(body, Math.min(n, 1000));
            return attachTeacherInfo(result.getCourses());
        } catch (IOException e) {
            log.error("Elasticsearch query failed", e);
            throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR, e);
        } catch (Exception e) {
            log.error("Unexpected error during Elasticsearch query", e);
            throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR, e);
        }
    }

    @Override
    public PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("from", query.from());
            body.put("size", query.getPageSize());
            body.set("query", buildPortalQuery(query));
            addSort(body, normalizeSortField(query.getSortBy()), Boolean.TRUE.equals(query.getIsAsc()));
            addHighlight(body);
            addExcludeFields(body, CourseVO.EXCLUDE_FIELDS);

            SearchResultData result = executeSearch(body, query.getPageSize());
            List<CourseVO> vos = attachTeacherInfo(result.getCourses());
            return new PageDTO<>(result.getTotal(), result.getPages(), vos);
        } catch (Exception e) {
            log.error("Query portal courses failed", e);
            throw new CommonException(ErrorInfo.Msg.SERVER_INTER_ERROR, e);
        }
    }

    @Override
    public List<Long> queryCoursesIdByName(String keyword) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("size", 1000);
            body.set("query", wrapMustQuery(buildKeywordQuery(keyword)));
            ObjectNode source = body.putObject("_source");
            ArrayNode includes = source.putArray("includes");
            includes.add("id");

            SearchResultData result = executeSearch(body, 1000);
            return result.getCourses().stream().map(Course::getId).toList();
        } catch (IOException e) {
            throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR, e);
        }
    }

    private List<CourseVO> attachTeacherInfo(List<Course> courses) {
        if (CollUtils.isEmpty(courses)) {
            return CollUtils.emptyList();
        }
        Set<Long> teacherIds = new HashSet<>();
        for (Course course : courses) {
            if (course.getTeacher() != null && course.getTeacher() > 0) {
                teacherIds.add(course.getTeacher());
            }
        }
        Map<Long, UserDTO> teacherMap = CollUtils.isEmpty(teacherIds)
                ? Map.of()
                : userClient.queryUserByIds(teacherIds).stream()
                .collect(Collectors.toMap(UserDTO::getId, user -> user, (a, b) -> a));

        return courses.stream().map(course -> {
            CourseVO vo = BeanUtils.toBean(course, CourseVO.class);
            UserDTO teacher = teacherMap.get(course.getTeacher());
            if (teacher != null) {
                vo.setTeacher(teacher.getName());
                vo.setIcon(teacher.getIcon());
            } else {
                vo.setTeacher("未知");
            }
            return vo;
        }).toList();
    }

    private SearchResultData executeSearch(ObjectNode body, int pageSize) throws IOException {
        Request request = new Request("POST", "/" + CourseRepository.INDEX_NAME + "/_search");
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        Response response = restClient.performRequest(request);
        String json = EntityUtils.toString(response.getEntity());
        JsonNode root = objectMapper.readTree(json);
        return parseSearchResult(root, pageSize);
    }

    private SearchResultData parseSearchResult(JsonNode root, int pageSize) {
        JsonNode hitsNode = root.path("hits");
        long total = parseTotal(hitsNode.path("total"));
        long pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;

        List<Course> courses = new ArrayList<>();
        for (JsonNode hit : hitsNode.path("hits")) {
            JsonNode source = hit.path("_source");
            if (source.isMissingNode() || source.isNull()) {
                continue;
            }
            try {
                Course course = objectMapper.treeToValue(source, Course.class);
                JsonNode highlight = hit.path("highlight").path(CourseRepository.DEFAULT_QUERY_NAME);
                if (highlight.isArray() && highlight.size() > 0) {
                    course.setName(highlight.get(0).asText(course.getName()));
                }
                courses.add(course);
            } catch (Exception e) {
                log.warn("Parse course document failed: {}", source, e);
            }
        }
        return new SearchResultData(total, pages, courses);
    }

    private long parseTotal(JsonNode totalNode) {
        if (totalNode == null || totalNode.isMissingNode() || totalNode.isNull()) {
            return 0L;
        }
        if (totalNode.isNumber()) {
            return totalNode.asLong();
        }
        return totalNode.path("value").asLong(0L);
    }

    private ObjectNode buildPortalQuery(CoursePageQuery query) {
        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode must = bool.putArray("must");
        must.add(buildKeywordQuery(query.getKeyword()));

        ArrayNode filter = bool.putArray("filter");
        addTermFilter(filter, CourseRepository.CATEGORY_ID_LV1, query.getCategoryIdLv1());
        addTermFilter(filter, CourseRepository.CATEGORY_ID_LV2, query.getCategoryIdLv2());
        addTermFilter(filter, CourseRepository.CATEGORY_ID_LV3, query.getCategoryIdLv3());
        addTermFilter(filter, CourseRepository.FREE, query.getFree());
        addTermFilter(filter, CourseRepository.TYPE, query.getType());
        addDateRangeFilter(filter, query.getBeginTime(), query.getEndTime());

        return objectMapper.createObjectNode().set("bool", bool);
    }

    private ObjectNode buildTopNQuery(List<Long> categoryIds, Boolean isFree) {
        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode must = bool.putArray("must");
        must.add(objectMapper.createObjectNode().set("match_all", objectMapper.createObjectNode()));

        ArrayNode filter = bool.putArray("filter");
        if (isFree != null) {
            addTermFilter(filter, CourseRepository.FREE, isFree);
        }
        if (CollUtils.isNotEmpty(categoryIds)) {
            ObjectNode terms = objectMapper.createObjectNode();
            ArrayNode values = objectMapper.createArrayNode();
            categoryIds.forEach(values::add);
            terms.set(CourseRepository.CATEGORY_ID_LV2, values);
            filter.add(objectMapper.createObjectNode().set("terms", terms));
        }
        return objectMapper.createObjectNode().set("bool", bool);
    }

    private ObjectNode buildKeywordQuery(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return objectMapper.createObjectNode().set("match_all", objectMapper.createObjectNode());
        }
        ObjectNode query = objectMapper.createObjectNode();
        query.put("query", keyword);
        return objectMapper.createObjectNode()
                .set("match_phrase", objectMapper.createObjectNode()
                        .set(CourseRepository.DEFAULT_QUERY_NAME, query));
    }

    private ObjectNode wrapMustQuery(ObjectNode queryNode) {
        ObjectNode bool = objectMapper.createObjectNode();
        bool.putArray("must").add(queryNode);
        return objectMapper.createObjectNode().set("bool", bool);
    }

    private void addTermFilter(ArrayNode filter, String field, Object value) {
        if (value == null) {
            return;
        }
        ObjectNode valueNode = objectMapper.createObjectNode();
        if (value instanceof Number number) {
            valueNode.put(field, number.longValue());
        } else if (value instanceof Boolean bool) {
            valueNode.put(field, bool);
        } else {
            valueNode.put(field, String.valueOf(value));
        }
        filter.add(objectMapper.createObjectNode().set("term", valueNode));
    }

    private void addDateRangeFilter(ArrayNode filter, LocalDateTime beginTime, LocalDateTime endTime) {
        if (beginTime == null && endTime == null) {
            return;
        }
        ObjectNode rangeField = objectMapper.createObjectNode();
        if (beginTime != null) {
            rangeField.put("gte", beginTime.format(DATE_TIME_FORMATTER));
        }
        if (endTime != null) {
            rangeField.put("lte", endTime.format(DATE_TIME_FORMATTER));
        }
        filter.add(objectMapper.createObjectNode().set("range",
                objectMapper.createObjectNode().set(CourseRepository.UPDATE_TIME, rangeField)));
    }

    private void addSort(ObjectNode body, String sortBy, boolean isAsc) {
        ArrayNode sortArray = body.putArray("sort");
        if (StringUtils.isBlank(sortBy)) {
            sortArray.add(objectMapper.createObjectNode().set("_score",
                    objectMapper.createObjectNode().put("order", "desc")));
            return;
        }
        sortArray.add(objectMapper.createObjectNode().set(sortBy,
                objectMapper.createObjectNode().put("order", isAsc ? "asc" : "desc")));
    }

    private void addHighlight(ObjectNode body) {
        ObjectNode highlight = body.putObject("highlight");
        highlight.putArray("pre_tags").add("<em>");
        highlight.putArray("post_tags").add("</em>");
        highlight.putObject("fields").putObject(CourseRepository.DEFAULT_QUERY_NAME);
    }

    private void addExcludeFields(ObjectNode body, String[] excludeFields) {
        if (excludeFields == null || excludeFields.length == 0) {
            return;
        }
        ObjectNode source = body.putObject("_source");
        ArrayNode excludes = source.putArray("excludes");
        for (String excludeField : excludeFields) {
            excludes.add(excludeField);
        }
    }

    private String normalizeSortField(String sortBy) {
        if (StringUtils.isBlank(sortBy)) {
            return sortBy;
        }
        if (PUBLISH_TIME.concat(".keyword").equals(sortBy)) {
            return PUBLISH_TIME;
        }
        return sortBy;
    }

    private static class SearchResultData {
        private final long total;
        private final long pages;
        private final List<Course> courses;

        private SearchResultData(long total, long pages, List<Course> courses) {
            this.total = total;
            this.pages = pages;
            this.courses = courses;
        }

        public long getTotal() {
            return total;
        }

        public long getPages() {
            return pages;
        }

        public List<Course> getCourses() {
            return courses;
        }
    }
}
