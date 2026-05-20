<template>
  <div class="home-view">
    <!-- 欢迎横幅 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div class="welcome-text">
          <h2>欢迎回来，{{ userStore.username }}！</h2>
          <p>Spring Cloud Alibaba 商城管理平台</p>
        </div>
        <el-icon :size="80" color="#409EFF" class="welcome-icon hidden-xs"><Shop /></el-icon>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :xs="12" :sm="6" v-for="stat in stats" :key="stat.title">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-title">{{ stat.title }}</p>
              <h3 class="stat-value">{{ stat.value }}</h3>
            </div>
            <el-icon :size="40" :color="stat.color"><component :is="stat.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <el-row :gutter="20">
      <el-col :xs="24" :sm="12">
        <el-card shadow="never" class="action-card">
          <template #header>
            <span class="card-title">快捷操作</span>
          </template>
          <div class="action-grid">
            <div
              v-for="action in quickActions"
              :key="action.label"
              class="action-item"
              @click="$router.push(action.path)"
            >
              <el-icon :size="28" :color="action.color"><component :is="action.icon" /></el-icon>
              <span>{{ action.label }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12">
        <el-card shadow="never" class="action-card">
          <template #header>
            <span class="card-title">系统架构</span>
          </template>
          <div class="arch-info">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="框架">Spring Boot 2.6.13</el-descriptions-item>
              <el-descriptions-item label="微服务">Spring Cloud 2021.0.5</el-descriptions-item>
              <el-descriptions-item label="Alibaba">2021.0.5.0</el-descriptions-item>
              <el-descriptions-item label="网关">Spring Cloud Gateway (9000)</el-descriptions-item>
              <el-descriptions-item label="注册中心">Nacos (8848)</el-descriptions-item>
              <el-descriptions-item label="流量控制">Sentinel (8858)</el-descriptions-item>
              <el-descriptions-item label="前端">Vue3 + Element Plus</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const stats = ref([
  { title: '商品总数', value: '3', icon: 'Goods', color: '#409EFF' },
  { title: '订单总数', value: '0', icon: 'List', color: '#67C23A' },
  { title: '用户总数', value: '3', icon: 'User', color: '#E6A23C' },
  { title: '微服务数', value: '4', icon: 'Monitor', color: '#F56C6C' }
])

const quickActions = [
  { label: '商品列表', icon: 'Goods', color: '#409EFF', path: '/product' },
  { label: '我的订单', icon: 'List', color: '#67C23A', path: '/order' },
  { label: '个人中心', icon: 'User', color: '#E6A23C', path: '/user' }
]
</script>

<style scoped>
.welcome-card {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.welcome-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
}

.welcome-text h2 {
  margin: 0 0 8px;
  font-size: 24px;
}

.welcome-text p {
  margin: 0;
  opacity: 0.85;
  font-size: 14px;
}

.welcome-icon {
  opacity: 0.6;
}

.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  margin-bottom: 10px;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-title {
  color: #909399;
  font-size: 13px;
  margin: 0 0 8px;
}

.stat-value {
  color: #303133;
  font-size: 28px;
  margin: 0;
  font-weight: 600;
}

.action-card {
  margin-bottom: 20px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 13px;
  color: #606266;
}

.action-item:hover {
  background: #f5f7fa;
}
</style>
