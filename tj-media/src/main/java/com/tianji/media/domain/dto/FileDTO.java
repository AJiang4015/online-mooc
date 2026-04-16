package com.tianji.media.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Schema(description = "文件信息实体")
@NoArgsConstructor
@AllArgsConstructor
public class FileDTO {
    @Schema(description = "文件id", example = "1")
    private Long id;
    @Schema(description = "文件名称", example = "图片.jpg")
    private String filename;
    @Schema(description = "文件访问路径", example = "a.jpg")
    private String path;
    @Schema(description = "文件key")
    private String key;
    @Schema(description = "文件大小")
    private Long fileSize;
    @Schema(description = "文件状态")
    private Integer status;
    @Schema(description = "引用次数")
    private Integer useTimes;
    @Schema(description = "请求id")
    private String requestId;
    @Schema(description = "平台")
    private Integer platform;
    @Schema(description = "创建人")
    private Long creater;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public static FileDTO of(Long id, String filename, String path) {
        return new FileDTO(id, filename, path, null, null, null, null, null, null, null, null);
    }
}
