package com.tianji.media.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "视频播放签名信息")
public class VideoPlayVO {
    @ApiModelProperty(value = "视频appId", example = "1367473421")
    private Long appId;

    @ApiModelProperty(value = "视频唯一标识", example = "12412534535143242")
    private String fileId;

    @ApiModelProperty(value = "视频播放签名", example = "xxx.xxx.xxx")
    private String signature;
}
