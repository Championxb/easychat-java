package com.easychat.controller;

import com.easychat.annotation.GlobalInterceptor;
import com.easychat.entity.config.AppConfig;
import com.easychat.entity.constants.Constants;
import com.easychat.entity.po.AppUpdate;
import com.easychat.entity.vo.AppUpdateVO;
import com.easychat.entity.vo.ResponseVO;
import com.easychat.service.AppUpdateService;
import com.easychat.utils.CopyTools;
import com.easychat.utils.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

@RestController("updateController")
@RequestMapping("/update")
@Validated
public class UpdateController extends ABaseController {

    private static final Logger logger = LoggerFactory.getLogger(UpdateController.class);

    @Resource
    private AppConfig appConfig;

    @Resource
    private AppUpdateService appUpdateService;

    @RequestMapping("/checkVersion")
    @GlobalInterceptor
    public ResponseVO loadAllCategory(String appVersion, String uid) {
        if (StringTools.isEmpty(appVersion)) {
            return getSuccessResponseVO(null);
        }
        AppUpdate appUpdate = appUpdateService.getLatestUpdate(appVersion, uid);
        if (appUpdate == null) {
            return getSuccessResponseVO(null);
        }
        AppUpdateVO updateVO = CopyTools.copy(appUpdate, AppUpdateVO.class);
        File file = new File(appConfig.getProjectFolder() + Constants.APP_UPDATE_FOLDER + appUpdate.getId() + Constants.APP_EXE_SUFFIX);
        updateVO.setSize(file.length());
        updateVO.setUpdateList(Arrays.asList(appUpdate.getUpdateDescArray()));
        String fileName = Constants.APP_NAME + appUpdate.getVersion() + Constants.APP_EXE_SUFFIX;
        updateVO.setFileName(fileName);
        return getSuccessResponseVO(updateVO);
    }

    @RequestMapping("/download")
    @GlobalInterceptor
    public void download(HttpServletResponse response, @NotNull Integer id) {
        OutputStream out = null;
        FileInputStream in = null;
        try {
            AppUpdate appUpdate = appUpdateService.getAppUpdateById(id);
            File file = new File(appConfig.getProjectFolder() + Constants.APP_UPDATE_FOLDER + appUpdate.getId() + Constants.APP_EXE_SUFFIX);
            if (!file.exists()) {
                return;
            }
            response.setContentType("application/x-msdownload; charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;");
            response.setContentLengthLong(file.length());
            in = new FileInputStream(file);
            byte[] byteData = new byte[1024];
            out = response.getOutputStream();
            int len = 0;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            logger.error("读取文件异常", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.error("IO异常", e);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error("IO异常", e);
                }
            }
        }
    }
}
