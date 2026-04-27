package com.tianji.media.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.annotations.NoWrapper;
import com.tianji.media.domain.dto.FileDTO;
import com.tianji.media.domain.query.FileQuery;
import com.tianji.media.domain.query.MediaQuery;
import com.tianji.media.domain.vo.FileDetailVO;
import com.tianji.media.domain.vo.FileVO;
import com.tianji.media.domain.vo.MediaVO;
import com.tianji.media.service.IFileService;
import com.tianji.media.storage.IFileStorage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * <p>
 * 文件表，可以是普通文件、图片等 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2022-06-30
 */
@RestController
@RequestMapping("/files")
@Api(tags = "文件管理相关接口")
@RequiredArgsConstructor
public class FileController {

    private final IFileService fileService;
    private final IFileStorage fileStorage;

    @ApiOperation("分页搜索已上传媒资信息")
    @GetMapping
    public PageDTO<FileVO> queryFilePage(FileQuery query){
        return fileService.queryFilePage(query);
    }


    @ApiOperation("上传文件")
    @PostMapping
    public FileDTO uploadFile(
            @ApiParam(value = "文件数据") @RequestParam("file")MultipartFile file){
        return fileService.uploadFile(file);
    }

    @ApiOperation("获取文件信息")
    @GetMapping("/{id}")
    public FileDetailVO getFileInfo(
            @ApiParam(value = "文件id", example = "1") @PathVariable("id") Long id){
        return fileService.getFileInfo(id);
    }

    @ApiOperation("预览文件")
    @NoWrapper
    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> previewFile(
            @ApiParam(value = "文件key", required = true) @RequestParam("key") String key) {
        InputStream inputStream = fileStorage.downloadFile(key);
        MediaType mediaType = MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(new InputStreamResource(inputStream));
    }

    @ApiOperation("删除文件")
    @DeleteMapping("/{id}")
    public void deleteFileById(
            @ApiParam(value = "文件id", example = "1") @PathVariable("id") Long id) {
        fileService.deleteFileById(id);
    }

    @ApiOperation(value = "文件上传前先检查文件")
    @PostMapping("/upload/checkfile")
    public Boolean checkFile(
            @RequestParam("fileMd5") String fileMd5
    ) throws Exception {
        return  fileService.checkFile(fileMd5);
    }


    // 分块文件上传前检测
    @ApiOperation("分块文件上传前检测")
    @PostMapping("/upload/checkchunk")
    public Boolean checkChunk(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunk") int chunk) {
        return fileService.checkChunk(fileMd5, chunk);
    }

    // 分片上传文件
    @ApiOperation("分片上传文件")
    @PostMapping("/upload/uploadchunk")
    public Boolean uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunk") int chunk) throws IOException {
        //创建临时文件
        File tempFile = File.createTempFile("minio", "temp");
        //上传的文件拷贝到临时文件
        file.transferTo(tempFile);
        //文件路径
        String absolutePath = tempFile.getAbsolutePath();
        return fileService.uploadChunk(fileMd5,chunk,absolutePath);
    }

    @ApiOperation(value = "合并文件")
    @PostMapping("/upload/mergechunks")
    public FileDTO mergeChunks(@RequestParam("fileMd5") String fileMd5,
                                    @RequestParam("fileName") String fileName,
                                    @RequestParam("chunkTotal") int chunkTotal) throws Exception {
        return fileService.mergechunks(fileMd5,chunkTotal,fileName);

    }
}
