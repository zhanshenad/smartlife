package com.smartlife.pojo.vo;

import com.smartlife.pojo.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户信息出参。接口一律返回 VO 不返回实体，密码等敏感字段从源头就不带出来。
 */
@Data
@Schema(description = "当前用户信息")
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 id")
    private Long id;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "头像")
    private String icon;

    @Schema(description = "0 女 1 男")
    private Integer sex;

    @Schema(description = "1 用户端 2 商家端 3 管理端")
    private Integer role;

    @Schema(description = "手机号，脱敏展示")
    private String phone;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickName(user.getNickName());
        vo.setIcon(user.getIcon());
        vo.setSex(user.getSex());
        vo.setRole(user.getRole());
        vo.setPhone(maskPhone(user.getPhone()));
        return vo;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
