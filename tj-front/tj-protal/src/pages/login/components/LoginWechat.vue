<template>
  <div class="loginWechat">
    <div v-show="showQrCode" id="wxLogin" class="wx-login-container"></div>
    <div v-if="loginStatus === 'pending'" class="status-message">
      等待扫码...
    </div>
    <div v-else-if="loginStatus === 'success'" class="status-message success">
      登录成功，正在跳转...
    </div>
    <div v-else-if="loginStatus === 'expired'" class="status-message error">
      二维码已过期，请刷新页面重新获取。
    </div>
    <div v-else-if="loginStatus === 'error'" class="status-message error">
      {{ errorMessage || '微信登录失败，请重试' }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/store';
import { checkWxLoginStatus, getUserInfo, saveWxUuid } from '@/api/user';

const router = useRouter();
const route = useRoute();
const store = useUserStore();

const loginStatus = ref('pending');
const errorMessage = ref('');
const showQrCode = ref(true);
let loginTimer = null;

const WX_CONFIG = {
  appid: 'wxed9954c01bb89b47',
  redirectUri: 'http://localhost:8160/auth/wxLogin',
};

const generateState = () => {
  return `${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
};

const loadWxLoginScript = () => {
  return new Promise((resolve, reject) => {
    if (window.WxLogin) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://res.wx.qq.com/connect/zh_CN/htmledition/js/wxLogin.js';
    script.onload = resolve;
    script.onerror = reject;
    document.body.appendChild(script);
  });
};

const clearLoginTimer = () => {
  if (loginTimer) {
    clearInterval(loginTimer);
    loginTimer = null;
  }
};

const handleLoginSuccess = async (token) => {
  if (!token) {
    loginStatus.value = 'error';
    errorMessage.value = '微信登录成功，但未获取到登录凭证';
    return;
  }
  await store.setToken(token);
  const userInfo = await getUserInfo();
  if (userInfo.code !== 200) {
    loginStatus.value = 'error';
    errorMessage.value = userInfo.msg || '获取用户信息失败';
    return;
  }
  await store.setUserInfo(userInfo.data);
  loginStatus.value = 'success';
  setTimeout(() => {
    router.push('/main/index');
  }, 800);
};

const pollLoginStatus = async (state) => {
  try {
    const res = await checkWxLoginStatus(state);
    if (res.code !== 200) {
      return;
    }
    const status = res.data.status;
    if (status === 'pending') {
      loginStatus.value = 'pending';
      return;
    }
    if (status === 'success') {
      clearLoginTimer();
      await handleLoginSuccess(res.data.token);
      return;
    }
    if (status === 'expired') {
      clearLoginTimer();
      loginStatus.value = 'expired';
      return;
    }
    if (status === 'error') {
      clearLoginTimer();
      loginStatus.value = 'error';
      errorMessage.value = res.data.msg || '微信登录失败，请重试';
    }
  } catch (error) {
    console.error('检查微信登录状态失败', error);
  }
};

const startLoginCheck = async (state) => {
  clearLoginTimer();
  await pollLoginStatus(state);
  if (loginStatus.value === 'success' || loginStatus.value === 'error' || loginStatus.value === 'expired') {
    return;
  }
  loginTimer = setInterval(() => {
    pollLoginStatus(state);
  }, 2000);
};

const initWxLogin = async () => {
  try {
    await loadWxLoginScript();
    await nextTick();

    const state = generateState();
    await saveWxUuid(state);

    showQrCode.value = true;
    new window.WxLogin({
      id: 'wxLogin',
      appid: WX_CONFIG.appid,
      scope: 'snsapi_login',
      redirect_uri: encodeURIComponent(WX_CONFIG.redirectUri),
      state,
      style: 'black',
      href: '',
    });

    await startLoginCheck(state);
  } catch (error) {
    console.error('初始化微信登录失败', error);
    loginStatus.value = 'error';
    errorMessage.value = '初始化微信登录失败，请刷新页面重试';
    ElMessage.error('初始化微信登录失败，请刷新页面重试');
  }
};

const resumeWxLogin = async () => {
  const wxState = route.query.wxState;
  const wxError = route.query.wxError;
  if (typeof wxError === 'string' && wxError) {
    loginStatus.value = 'error';
    errorMessage.value = decodeURIComponent(wxError);
  }
  if (typeof wxState === 'string' && wxState) {
    showQrCode.value = false;
    loginStatus.value = 'pending';
    await startLoginCheck(wxState);
    return true;
  }
  return false;
};

watch(
  () => route.query.wxState,
  async (value) => {
    if (typeof value === 'string' && value) {
      await resumeWxLogin();
    }
  }
);

onMounted(async () => {
  const resumed = await resumeWxLogin();
  if (!resumed) {
    initWxLogin();
  }
});

onUnmounted(() => {
  clearLoginTimer();
});
</script>

<style scoped>
.loginWechat {
  margin-top: 40px;
  text-align: center;
}

.wx-login-container {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.status-message {
  margin-top: 10px;
  font-size: 14px;
}

.success {
  color: #67c23a;
}

.error {
  color: #f56c6c;
}
</style>
