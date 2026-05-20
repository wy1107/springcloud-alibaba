<template>
  <div class="login-container">
    <div class="login-bg">
      <div class="bg-shape bg-shape-1"></div>
      <div class="bg-shape bg-shape-2"></div>
      <div class="bg-shape bg-shape-3"></div>
    </div>

    <el-card class="login-card">
      <div class="login-header">
        <el-icon :size="40" color="#409EFF"><Shop /></el-icon>
        <h2>商城</h2>
        <p>Spring Cloud Alibaba + Vue3</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="uid">
          <el-input
            v-model="loginForm.uid"
            placeholder="请输入用户ID"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="telephone">
          <el-input
            v-model="loginForm.telephone"
            placeholder="请输入手机号"
            :prefix-icon="Phone"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <el-divider>测试账号</el-divider>
        <div class="test-accounts">
          <el-tag
            v-for="account in testAccounts"
            :key="account.uid"
            class="account-tag"
            effect="plain"
            @click="fillAccount(account)"
          >
            {{ account.label }}
          </el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { User, Phone } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  uid: '',
  telephone: ''
})

const loginRules = {
  uid: [
    { required: true, message: '请输入用户ID', trigger: 'blur' },
    { pattern: /^[1-9]\d*$/, message: '用户ID必须为正整数', trigger: 'blur' }
  ],
  telephone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

const testAccounts = [
  { uid: '1', telephone: '13800138001', label: '张三 (ID:1)' },
  { uid: '2', telephone: '13800138002', label: '李四 (ID:2)' },
  { uid: '3', telephone: '13800138003', label: '王五 (ID:3)' }
]

function fillAccount(account) {
  loginForm.uid = account.uid
  loginForm.telephone = account.telephone
}

async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(Number(loginForm.uid), loginForm.telephone)
    ElMessage.success(`欢迎回来，${userStore.username}！`)
    const redirect = route.query.redirect || '/home'
    router.push(redirect)
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || error.message || '登录失败，请检查用户ID和手机号')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;
}

.bg-shape-1 {
  width: 400px;
  height: 400px;
  background: #fff;
  top: -100px;
  right: -100px;
}

.bg-shape-2 {
  width: 300px;
  height: 300px;
  background: #fff;
  bottom: -80px;
  left: -80px;
}

.bg-shape-3 {
  width: 200px;
  height: 200px;
  background: #fff;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.login-card {
  width: 420px;
  max-width: 90vw;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  margin: 12px 0 4px;
  color: #303133;
  font-size: 24px;
}

.login-header p {
  color: #909399;
  font-size: 13px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

.login-footer {
  margin-top: 10px;
}

.test-accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.account-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.account-tag:hover {
  color: #409EFF;
  border-color: #409EFF;
}
</style>
