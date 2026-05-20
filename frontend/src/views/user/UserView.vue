<template>
  <div class="user-view">
    <el-row :gutter="20">
      <!-- 用户信息卡片 -->
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="profile-card">
          <div class="profile-content">
            <el-avatar :size="80" class="profile-avatar">
              {{ userStore.username?.charAt(0) || 'U' }}
            </el-avatar>
            <h3 class="profile-name">{{ userStore.username || '未设置' }}</h3>
            <p class="profile-id">ID: {{ userStore.userId || '-' }}</p>
          </div>
          <el-divider />
          <el-descriptions :column="1" size="small">
            <el-descriptions-item label-align="center">
              <template #label>
                <el-icon><User /></el-icon> 用户名
              </template>
              {{ userInfo.username || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label-align="center">
              <template #label>
                <el-icon><Phone /></el-icon> 手机号
              </template>
              {{ userInfo.telephone || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label-align="center">
              <template #label>
                <el-icon><Key /></el-icon> 用户ID
              </template>
              {{ userInfo.uid || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 操作区域 -->
      <el-col :xs="24" :sm="16">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">账户设置</span>
          </template>

          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本信息" name="info">
              <el-form label-width="80px" disabled>
                <el-form-item label="用户名">
                  <el-input :model-value="userInfo.username" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input :model-value="userInfo.telephone" />
                </el-form-item>
                <el-form-item label="用户ID">
                  <el-input :model-value="String(userInfo.uid || '')" />
                </el-form-item>
              </el-form>
              <el-button type="primary" @click="refreshUser" :loading="refreshing">
                刷新信息
              </el-button>
            </el-tab-pane>

            <el-tab-pane label="安全设置" name="security">
              <el-alert
                title="安全提示"
                type="info"
                description="当前为演示模式，密码修改功能暂未开放。实际项目中应对接后端认证服务。"
                :closable="false"
                show-icon
                style="margin-bottom: 16px"
              />
              <el-form label-width="80px">
                <el-form-item label="当前密码">
                  <el-input type="password" placeholder="请输入当前密码" disabled />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input type="password" placeholder="请输入新密码" disabled />
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="关于" name="about">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="系统名称">微服务商城</el-descriptions-item>
                <el-descriptions-item label="技术栈">Spring Cloud Alibaba + Vue3 + Element Plus</el-descriptions-item>
                <el-descriptions-item label="网关">Spring Cloud Gateway (9000)</el-descriptions-item>
                <el-descriptions-item label="注册中心">Nacos (8848)</el-descriptions-item>
                <el-descriptions-item label="流量控制">Sentinel (8858)</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <el-card shadow="never" style="margin-top: 20px">
          <template #header>
            <span class="card-title">危险操作</span>
          </template>
          <el-button type="danger" @click="handleLogout">退出登录</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { userApi } from '@/api/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('info')
const refreshing = ref(false)

const userInfo = reactive({
  uid: null,
  username: '',
  telephone: ''
})

onMounted(async () => {
  await refreshUser()
})

async function refreshUser() {
  if (!userStore.userId) return
  refreshing.value = true
  try {
    const user = await userApi.findById(Number(userStore.userId))
    if (user) {
      userInfo.uid = user.uid
      userInfo.username = user.username
      userInfo.telephone = user.telephone
    }
  } catch (error) {
    ElMessage.error('获取用户信息失败')
  } finally {
    refreshing.value = false
  }
}

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
  })
}
</script>

<style scoped>
.profile-card {
  text-align: center;
}

.profile-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.profile-avatar {
  background-color: #409EFF;
  color: #fff;
  font-size: 32px;
}

.profile-name {
  margin: 16px 0 4px;
  font-size: 20px;
  color: #303133;
}

.profile-id {
  color: #909399;
  font-size: 13px;
  margin: 0;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}
</style>
