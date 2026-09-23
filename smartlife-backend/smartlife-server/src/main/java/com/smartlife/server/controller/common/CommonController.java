package com.smartlife.server.controller.common;

import com.smartlife.common.result.Result;
import com.smartlife.server.util.OssUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用接口：图片上传（商家发菜品图/用户传头像共用），登录即可调用。
 */
@RestController
@RequestMapping("/common")
public class CommonController {

    private final OssUtil ossUtil;

    public CommonController(OssUtil ossUtil) {
        this.ossUtil = ossUtil;
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam MultipartFile file) {
        return Result.ok(ossUtil.uploadImage(file));
    }
}
