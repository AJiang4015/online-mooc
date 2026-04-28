package com.tianji.api.dto.user;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@ApiModel(description = "微信扫码首登自动注册参数")
public class WxLoginRegisterDTO {

    @NotBlank
    @ApiModelProperty(value = "微信unionid", required = true)
    private String unionid;

    @ApiModelProperty("微信昵称")
    private String nickname;

    @ApiModelProperty("微信头像")
    private String icon;
}
