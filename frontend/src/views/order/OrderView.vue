<template>
  <div class="order-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">我的订单</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            创建订单
          </el-button>
        </div>
      </template>

      <el-empty v-if="orders.length === 0" description="暂无订单">
        <el-button type="primary" @click="$router.push('/product')">去选购</el-button>
      </el-empty>

      <el-table
        v-else
        :data="orders"
        stripe
        style="width: 100%"
        :default-sort="{ prop: 'oid', order: 'descending' }"
      >
        <el-table-column prop="oid" label="订单ID" width="80" />
        <el-table-column prop="pname" label="商品名称" min-width="140" />
        <el-table-column prop="pprice" label="商品价格" width="120">
          <template #default="{ row }">
            <span style="color: #F56C6C; font-weight: 600">
              ¥{{ row.pprice?.toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="number" label="数量" width="80" align="center" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default>
            <el-tag type="success" size="small">已完成</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建订单对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建订单"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="orderFormRef"
        :model="orderForm"
        :rules="orderRules"
        label-width="80px"
      >
        <el-form-item label="商品ID" prop="pid">
          <el-input-number
            v-model="orderForm.pid"
            :min="1"
            :max="9999"
            placeholder="输入商品ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="用户ID" prop="uid">
          <el-input-number
            v-model="orderForm.uid"
            :min="1"
            :max="9999"
            placeholder="输入用户ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item>
          <div class="form-tip">
            测试商品: 1-小米手机, 2-华为笔记本, 3-苹果平板
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createOrder">确认下单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { orderApi } from '@/api/order'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const orders = ref([])
const showCreateDialog = ref(false)
const creating = ref(false)
const orderFormRef = ref(null)

const orderForm = reactive({
  pid: 1,
  uid: Number(userStore.userId) || 1
})

const orderRules = {
  pid: [{ required: true, message: '请输入商品ID', trigger: 'blur' }],
  uid: [{ required: true, message: '请输入用户ID', trigger: 'blur' }]
}

onMounted(() => {
  // 订单数据从localStorage缓存读取（后端无列表查询接口）
  const cached = localStorage.getItem('user_orders')
  if (cached) {
    orders.value = JSON.parse(cached)
  }
})

async function createOrder() {
  const valid = await orderFormRef.value.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    const order = await orderApi.create(orderForm.pid, orderForm.uid)
    orders.value.unshift(order)
    // 缓存订单数据
    localStorage.setItem('user_orders', JSON.stringify(orders.value))
    ElMessage.success('下单成功！')
    showCreateDialog.value = false
  } catch (error) {
    ElMessage.error('下单失败：' + (error.message || '请稍后重试'))
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
  font-size: 16px;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
</style>
