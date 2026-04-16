<template>
  <div>
    <el-dialog
      title="上传文件"
      v-model="dialogVisible"
      width="90%"
      @closed="handleCloseDialog"
      style="max-height: 90vh; overflow-y: auto;"
    >
      <div class="upload-files-container">
        <div
          v-for="file in uploadingFiles"
          :key="file.uniqueId"
          class="upload-file-card"
        >
          <div class="file-info">
            <div class="file-name">
              <i class="el-icon-document"></i>
              <span>{{ file.name }}</span>
            </div>
            <div class="file-size">{{ (file.size / 1024).toFixed(2) }} KB</div>
          </div>

          <div class="progress-container">
            <el-progress
              :percentage="file.percentage"
              :status="getProgressStatus(file)"
              :stroke-width="16"
            />
            <div class="status-text">
              <span v-if="file.status === 'success'" class="success">
                <i class="el-icon-success"></i> 上传成功
              </span>
              <span v-else-if="file.status === 'error'" class="error">
                <i class="el-icon-error"></i> 上传失败
              </span>
              <span v-else-if="file.status === 'paused'" class="warning">
                <i class="el-icon-warning"></i> 已暂停
              </span>
              <span v-else>{{ file.percentage }}%</span>
            </div>
          </div>

          <div class="file-actions">
            <el-button
              v-if="file.status === 'uploading'"
              size="mini"
              type="warning"
              @click="pauseUpload(file)"
            >
              <i class="el-icon-pause"></i> 暂停
            </el-button>
            <el-button
              v-else-if="file.status === 'paused' || file.status === 'error'"
              size="mini"
              type="primary"
              @click="resumeUpload(file)"
            >
              <i class="el-icon-play"></i> 继续
            </el-button>
            <el-button
              size="mini"
              type="danger"
              @click="removeFile(file)"
            >
              <i class="el-icon-delete"></i> 移除
            </el-button>
          </div>
        </div>

        <div v-if="uploadingFiles.length === 0" class="empty-upload-area">
          <i class="el-icon-upload el-icon--primary"></i>
          <p>暂无上传文件</p>
          <p class="small-text">点击下方“添加文件”按钮选择文件上传</p>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-upload
            ref="uploadRef"
            action="#"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            :before-upload="beforeUpload"
            multiple
          >
            <el-button type="primary">
              <i class="el-icon-plus"></i> 添加文件
            </el-button>
          </el-upload>
          <el-button
            type="success"
            :loading="isUploadingAll"
            :disabled="!hasFilesToUpload"
            @click="uploadAll"
          >
            <i class="el-icon-upload2"></i> 全部上传
          </el-button>
          <el-button
            type="warning"
            :disabled="!hasUploadingFiles"
            @click="pauseAll"
          >
            <i class="el-icon-pause-circle"></i> 全部暂停
          </el-button>
          <el-button @click="dialogVisible = false">
            <i class="el-icon-close"></i> 关闭
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import CryptoJS from "crypto-js";
import { uploadFile as uploadFileApi } from "@/api/media";

const emit = defineEmits(["uploaded"]);
const dialogVisible = ref(false);
const uploadingFiles = ref([]);
const isUploadingAll = ref(false);
const uploadRef = ref(null);

const hasFilesToUpload = computed(() =>
  uploadingFiles.value.some(
    (file) => file.status === "ready" || file.status === "paused" || file.status === "error"
  )
);

const hasUploadingFiles = computed(() =>
  uploadingFiles.value.some((file) => file.status === "uploading")
);

const getProgressStatus = (file) => {
  if (file.status === "success") return "success";
  if (file.status === "error") return "exception";
  if (file.status === "paused") return "warning";
  return "";
};

const openDialog = () => {
  dialogVisible.value = true;
};

const handleCloseDialog = () => {
  uploadingFiles.value = [];
  nextTick(() => uploadRef.value?.clearFiles());
};

const generateFileKey = (file) => `${file.name}-${file.size}-${Date.now()}`;

const calculateFileFingerprint = async (file) =>
  new Promise((resolve) => {
    const reader = new FileReader();
    reader.readAsArrayBuffer(file.slice(0, 1024 * 1024));
    reader.onload = (e) => {
      const wordArray = CryptoJS.lib.WordArray.create(e.target.result);
      resolve(CryptoJS.MD5(wordArray).toString().substring(0, 10));
    };
  });

const beforeUpload = (file) => {
  const maxSize = 200 * 1024 * 1024;
  if (file.size > maxSize) {
    ElMessage.error(`文件大小不能超过 ${maxSize / 1024 / 1024}MB`);
    return false;
  }
  return true;
};

const handleFileChange = async (file, fileList) => {
  const validFiles = fileList.filter(beforeUpload);
  if (validFiles.length === 0) return;

  const filesWithFingerprint = await Promise.all(
    validFiles.map(async (item) => ({
      ...item,
      fingerprint: await calculateFileFingerprint(item.raw),
    }))
  );

  const existingFingerprints = new Set(uploadingFiles.value.map((item) => item.fingerprint));
  const newFiles = filesWithFingerprint.filter(
    (item) => !existingFingerprints.has(item.fingerprint)
  );
  const duplicateCount = filesWithFingerprint.length - newFiles.length;
  if (duplicateCount > 0) {
    ElMessage.warning(`已过滤 ${duplicateCount} 个重复文件`);
  }

  uploadingFiles.value = [
    ...uploadingFiles.value,
    ...newFiles.map((item) => ({
      ...item,
      uniqueId: generateFileKey(item),
      percentage: 0,
      status: "ready",
      abortController: new AbortController(),
    })),
  ];
  nextTick(() => uploadRef.value?.clearFiles());
};

const doUpload = (file) => {
  const index = uploadingFiles.value.findIndex((item) => item.uniqueId === file.uniqueId);
  if (index === -1) return;

  uploadingFiles.value[index].status = "uploading";
  uploadFileApi(file.raw, {
    signal: uploadingFiles.value[index].abortController.signal,
    onUploadProgress: (event) => {
      const currentIndex = uploadingFiles.value.findIndex(
        (item) => item.uniqueId === file.uniqueId
      );
      if (currentIndex === -1 || !event.total) return;
      uploadingFiles.value[currentIndex].percentage = Math.min(
        Math.round((event.loaded / event.total) * 100),
        99
      );
    },
  })
    .then((res) => {
      const currentIndex = uploadingFiles.value.findIndex(
        (item) => item.uniqueId === file.uniqueId
      );
      if (currentIndex === -1) return;
      if (res.code !== 200) {
        throw new Error(res.msg || "上传失败");
      }
      uploadingFiles.value[currentIndex].percentage = 100;
      uploadingFiles.value[currentIndex].status = "success";
      ElMessage.success(`文件 ${file.name} 上传成功`);
      emit("uploaded", res.data);
    })
    .catch((e) => {
      const currentIndex = uploadingFiles.value.findIndex(
        (item) => item.uniqueId === file.uniqueId
      );
      if (currentIndex === -1) return;
      if (e.name === "CanceledError" || e.name === "AbortError") {
        uploadingFiles.value[currentIndex].status = "paused";
        return;
      }
      uploadingFiles.value[currentIndex].status = "error";
      ElMessage.error(`文件 ${file.name} 上传失败: ${e.message || "未知错误"}`);
    });
};

const uploadAll = () => {
  const filesToUpload = uploadingFiles.value.filter(
    (file) => file.status === "ready" || file.status === "paused" || file.status === "error"
  );
  if (filesToUpload.length === 0) {
    ElMessage.info("没有需要上传的文件");
    return;
  }

  isUploadingAll.value = true;
  filesToUpload.forEach((file) => {
    if (!file.abortController || file.abortController.signal.aborted) {
      file.abortController = new AbortController();
    }
    doUpload(file);
  });
  isUploadingAll.value = false;
};

const pauseUpload = (file) => {
  const index = uploadingFiles.value.findIndex((item) => item.uniqueId === file.uniqueId);
  if (index === -1) return;
  uploadingFiles.value[index].abortController.abort();
  uploadingFiles.value[index].status = "paused";
};

const resumeUpload = (file) => {
  const index = uploadingFiles.value.findIndex((item) => item.uniqueId === file.uniqueId);
  if (index === -1) return;
  uploadingFiles.value[index].abortController = new AbortController();
  uploadingFiles.value[index].percentage = 0;
  doUpload(file);
};

const pauseAll = () => {
  const files = uploadingFiles.value.filter((file) => file.status === "uploading");
  if (files.length === 0) {
    ElMessage.info("没有正在上传的文件");
    return;
  }
  files.forEach((file) => pauseUpload(file));
};

const removeFile = (file) => {
  if (file.status === "uploading") {
    file.abortController.abort();
  }
  uploadingFiles.value = uploadingFiles.value.filter((item) => item.uniqueId !== file.uniqueId);
  nextTick(() => uploadRef.value?.clearFiles());
};

defineExpose({
  openDialog,
});
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 20px;
}

.status-text {
  margin-top: 5px;
  font-size: 12px;
}

.success {
  color: #67c23a;
}

.error {
  color: #f56c6c;
}

.warning {
  color: #e6a23c;
}

.upload-files-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  padding: 16px;
}

.upload-file-card {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 16px;
  transition: all 0.3s ease;
}

.upload-file-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.file-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.file-name {
  display: flex;
  align-items: center;
  font-weight: 500;
  overflow: hidden;
}

.file-name i {
  margin-right: 8px;
  color: #409eff;
}

.file-name span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  color: #909399;
  font-size: 12px;
}

.progress-container {
  margin-bottom: 12px;
}

.status-text {
  margin-top: 6px;
  text-align: center;
  font-size: 14px;
}

.file-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.empty-upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: #909399;
  text-align: center;
  grid-column: 1 / -1;
}
</style>
