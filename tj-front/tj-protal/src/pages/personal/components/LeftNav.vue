<!-- 个人中心 - 左侧导航  -->
<template>
  <div class="LeftNav">
    <router-link
      v-for="item in personalRoute"
      :key="item.path"
      class="fx-sb font-bt2"
      :class="{ active: activeClass(item) }"
      :to="item.path"
    >
      {{ item.meta.title }}<i class="iconfont" v-html="item.meta.icon"></i>
    </router-link>
    <a class="fx-sb font-bt2" @click="logout">退出<i class="iconfont">&#xe60c;</i></a>
  </div>
</template>
<script setup>
import { computed, ref, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import { userLogout } from "@/api/user";
import { useUserStore } from "@/store";

const store = useUserStore();
const route = useRoute();
const router = useRouter();
const personalRoute = ref([]);
const currentPath = ref("");

const activeClass = computed(() => (item) => {
  const reg = new RegExp("^" + item.meta.active);
  return reg.test(currentPath.value);
});

watchEffect(() => {
  const currentRoute = router.currentRoute.value;
  const personalChildren = currentRoute.matched[1]?.children || [];
  personalRoute.value = personalChildren.filter(
    (item) =>
      item.meta &&
      !item.meta.hidden &&
      item.name !== "myMessage" &&
      item.path !== "myMessage" &&
      item.meta.active !== "myMessage" &&
      item.name !== "mySet" &&
      item.path !== "mySet" &&
      item.meta.active !== "mySet"
  );
  currentPath.value = route.path.split("/").at(-1);
});

const logout = () => {
  userLogout()
    .then((res) => {
      if (res.code === 200) {
        store.logout();
        location.href = "/";
      }
    })
    .catch((err) => console.log(err));
};
</script>
<style lang="scss" scoped>
.LeftNav {
  width: 190px;
  height: fit-content;
  background: #ffffff;
  border-radius: 8px;
  margin-right: 20px;

  a {
    width: 100%;
    padding: 13px 15px 13px 20px;
    border-bottom: 1px solid #eeeeee;
    font-size: 14px;
    line-height: 32px;

    i {
      font-size: 30px;
    }
  }

  .active {
    color: var(--color-main);
  }
}
</style>
