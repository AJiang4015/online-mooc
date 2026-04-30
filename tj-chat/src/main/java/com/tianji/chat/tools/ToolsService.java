package com.tianji.chat.tools;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.chat.domain.vo.CourseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ToolsService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SearchClient searchClient;

    @Autowired
    private CourseClient courseClient;

    public String currentDateTime() {
        LocalDateTime now = LocalDateTime.now(DEFAULT_ZONE_ID);
        return String.format("当前日期时间：%s，星期%s，时区：%s。",
                now.format(DATE_TIME_FORMATTER),
                weekdayName(now.getDayOfWeek().getValue()),
                DEFAULT_ZONE_ID);
    }

    public String queryTodayWeather(String city) {
        if (city == null || city.trim().isEmpty()) {
            return "请先告诉我要查询天气的城市名称。";
        }
        String trimmedCity = city.trim();
        try {
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=zh&format=json&name="
                    + URLEncoder.encode(trimmedCity, StandardCharsets.UTF_8);
            HttpRequest geoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(geoUrl))
                    .GET()
                    .build();
            HttpResponse<String> geoResponse = HTTP_CLIENT.send(geoRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            JSONObject geoJson = JSONObject.parseObject(geoResponse.body());
            JSONArray results = geoJson.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return "暂时没有找到城市“" + trimmedCity + "”的天气信息，请试试更完整的城市名称。";
            }

            JSONObject location = results.getJSONObject(0);
            double latitude = location.getDoubleValue("latitude");
            double longitude = location.getDoubleValue("longitude");
            String resolvedCity = location.getString("name");
            String country = location.getString("country");

            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,weather_code,wind_speed_10m"
                    + "&timezone=Asia%2FShanghai";
            HttpRequest weatherRequest = HttpRequest.newBuilder()
                    .uri(URI.create(weatherUrl))
                    .GET()
                    .build();
            HttpResponse<String> weatherResponse = HTTP_CLIENT.send(weatherRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            JSONObject weatherJson = JSONObject.parseObject(weatherResponse.body());
            JSONObject current = weatherJson.getJSONObject("current");
            if (current == null) {
                return "暂时无法获取“" + trimmedCity + "”的实时天气，请稍后再试。";
            }

            return String.format("%s%s当前天气：%s，温度 %.1f°C，体感 %.1f°C，湿度 %d%%，降水 %.1f mm，降雨 %.1f mm，风速 %.1f km/h，数据时间 %s。",
                    resolvedCity,
                    country == null ? "" : "（" + country + "）",
                    weatherDescription(current.getIntValue("weather_code")),
                    current.getDoubleValue("temperature_2m"),
                    current.getDoubleValue("apparent_temperature"),
                    current.getIntValue("relative_humidity_2m"),
                    current.getDoubleValue("precipitation"),
                    current.getDoubleValue("rain"),
                    current.getDoubleValue("wind_speed_10m"),
                    current.getString("time"));
        } catch (Exception e) {
            log.error("查询天气失败，city={}", trimmedCity, e);
            return "查询“" + trimmedCity + "”天气时出现异常，请稍后再试。";
        }
    }

    public List<CourseVO> queryCourse(String name) {
        List<Long> ids = searchClient.queryCoursesIdByName(name);
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<CourseSimpleInfoDTO> courseInfoList = courseClient.getSimpleInfoList(ids);
        List<CourseVO> voList = new ArrayList<>();
        for (CourseSimpleInfoDTO courseInfo : courseInfoList) {
            CourseVO courseVO = new CourseVO();
            courseVO.setId(courseInfo.getId());
            courseVO.setName(courseInfo.getName());
            courseVO.setPrice(courseInfo.getPrice());
            voList.add(courseVO);
        }
        log.info("查询课程结果：{}", courseInfoList);
        return voList;
    }

    private String weekdayName(int dayOfWeek) {
        switch (dayOfWeek) {
            case 1:
                return "一";
            case 2:
                return "二";
            case 3:
                return "三";
            case 4:
                return "四";
            case 5:
                return "五";
            case 6:
                return "六";
            case 7:
                return "日";
            default:
                return "";
        }
    }

    private String weatherDescription(int weatherCode) {
        switch (weatherCode) {
            case 0:
                return "晴朗";
            case 1:
                return "基本晴";
            case 2:
                return "局部多云";
            case 3:
                return "阴天";
            case 45:
            case 48:
                return "有雾";
            case 51:
            case 53:
            case 55:
                return "毛毛雨";
            case 56:
            case 57:
                return "冻毛毛雨";
            case 61:
            case 63:
            case 65:
                return "下雨";
            case 66:
            case 67:
                return "冻雨";
            case 71:
            case 73:
            case 75:
                return "降雪";
            case 77:
                return "雪粒";
            case 80:
            case 81:
            case 82:
                return "阵雨";
            case 85:
            case 86:
                return "阵雪";
            case 95:
                return "雷暴";
            case 96:
            case 99:
                return "雷暴伴冰雹";
            default:
                return "未知天气";
        }
    }
}
