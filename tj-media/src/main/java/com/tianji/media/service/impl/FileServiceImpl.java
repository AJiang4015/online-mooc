package com.tianji.media.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.media.config.PlatformProperties;
import com.tianji.media.domain.dto.FileDTO;
import com.tianji.media.domain.po.File;
import com.tianji.media.enums.FileErrorInfo;
import com.tianji.media.enums.FileStatus;
import com.tianji.media.mapper.FileMapper;
import com.tianji.media.service.IFileService;
import com.tianji.media.storage.IFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * <p>
 * 文件表，可以是普通文件、图片等 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements IFileService {

    private final IFileStorage fileStorage;
    private final PlatformProperties properties;

    @Override
    public FileDTO uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String filename = generateNewFileName(originalFilename);
        InputStream inputStream;
        try {
            inputStream = file.getInputStream();
        } catch (IOException e) {
            throw new CommonException("文件读取异常", e);
        }
        fileStorage.uploadFile(filename, inputStream, file.getSize());
        File fileInfo;
        try {
            fileInfo = new File();
            fileInfo.setFilename(originalFilename);
            fileInfo.setKey(filename);
            fileInfo.setFileSize(file.getSize());
            fileInfo.setStatus(FileStatus.UPLOADED);
            fileInfo.setPlatform(properties.getFile());
            save(fileInfo);
        } catch (Exception e) {
            log.error("文件信息保存异常", e);
            fileStorage.deleteFile(filename);
            throw new DbException(FileErrorInfo.Msg.FILE_UPLOAD_ERROR);
        }
        FileDTO fileDTO = new FileDTO();
        fileDTO.setId(fileInfo.getId());
        fileDTO.setPath(buildFilePath(fileInfo));
        fileDTO.setFilename(originalFilename);
        return fileDTO;
    }

    @Override
    public FileDTO getFileInfo(Long id) {
        File file = getById(id);
        if (file == null) {
            return null;
        }
        return toFileDTO(file);
    }

    @Override
    public PageDTO<FileDTO> queryFiles(PageQuery query) {
        Page<File> page = lambdaQuery().page(query.toMpPage("id", false));
        List<FileDTO> list = page.getRecords().stream()
                .map(this::toFileDTO)
                .toList();
        return PageDTO.of(page, list);
    }

    private String buildFilePath(File file) {
        if (file == null) {
            return null;
        }
        if (file.getPlatform() != null && StringUtils.isNotBlank(file.getKey())) {
            return file.getPlatform().getPath() + file.getKey();
        }
        if (StringUtils.isNotBlank(file.getRequestId())) {
            return file.getRequestId();
        }
        if (StringUtils.isNotBlank(file.getKey())) {
            return "/img-tx/" + file.getKey();
        }
        return null;
    }

    private FileDTO toFileDTO(File file) {
        FileDTO dto = BeanUtils.copyBean(file, FileDTO.class);
        dto.setPath(buildFilePath(file));
        dto.setStatus(file.getStatus() == null ? null : file.getStatus().getValue());
        dto.setPlatform(file.getPlatform() == null ? null : file.getPlatform().getValue());
        return dto;
    }

    private String generateNewFileName(String originalFilename) {
        String suffix = StringUtils.subAfter(originalFilename, ".", true);
        return UUID.randomUUID().toString(true) + "." + suffix;
    }
}
