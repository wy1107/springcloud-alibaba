<template>
  <div class="product-detail" v-loading="loading">
    <el-page-header @back="$router.back()" content="商品详情" class="page-header" />

    <el-row :gutter="20" v-if="product" style="margin-top: 20px">
      <el-col :xs="24" :sm="12">
        <el-card shadow="never" class="detail-img-card">
          <div class="detail-img">
            <el-icon :size="100" color="#C0C4CC"><Goods /></el-icon>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12">
        <el-card shadow="never">
          <h2 class="detail-name">{{ product.pname }}</h2>
          <div class="detail-price">
            <span class="label">价格</span>
            <span class="price">
              <span class="symbol">¥</span>
              <span class="value">{{ product.pprice?.toFixed(2) }}</span>
            </span>
          </div>
          <el-divider />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="商品ID">{{ product.pid }}</el-descriptions-item>
            <el-descriptions-item label="商品名称">{{ product.pname }}</el-descriptions-item>
            <el-descriptions-item label="商品价格">¥{{ product.pprice?.toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="库存数量">
              <el-tag :type="product.stock > 20 ? 'success' : product.stock > 0 ? 'warning' : 'danger'">
                {{ product.stock }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions">
            <el-button
              type="primary"
              size="large"
              :disabled="product.stock <= 0"
              @click="handleBuy"
            >
              <el-icon><ShoppingCart /></el-icon>
              立即购买
            </el-button>
            <el-button size="large" @click="handleSeckill">
              <el-icon><Lightning /></el-icon>
              秒杀
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && !product" description="商品不存在" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { productApi } from '@/api/product'
import { orderApi } from '@/api/order'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const product = ref(null)

onMounted(async () => {
  const pid = Number(route.params.id)
  loading.value = true
  try {
    product.value = await productApi.findById(pid)
  } catch (error) {
    ElMessage.error('获取商品信息失败')
  } finally {
    loading.value = false
  }
})

async function handleBuy() {
  try {
    const order = await orderApi.create(product.value.pid, Number(userStore.userId))
    ElMessage.success('下单成功！')
    router.push('/order')
  } catch (error) {
    ElMessage.error('下单失败：' + (error.message || '请稍后重试'))
  }
}

async function handleSeckill() {
  try {
    const result = await productApi.seckill(product.value.pid)
    ElMessage.success(result || '秒杀成功！')
  } catch (error) {
    ElMessage.warning('秒杀失败：' + (error.message || '请稍后重试'))
  }
}
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.detail-img-card {
  border-radius: 8px;
}

.detail-img {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  border-radius: 8px;
}

.detail-name {
  font-size: 22px;
  color: #303133;
  margin: 0 0 16px;
}

.detail-price {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px;
  background: #fef0f0;
  border-radius: 8px;
  margin-bottom: 16px;
}

.detail-price .label {
  font-size: 13px;
  color: #909399;
}

.detail-price .symbol {
  font-size: 16px;
  color: #F56C6C;
}

.detail-price .value {
  font-size: 32px;
  font-weight: 700;
  color: #F56C6C;
}

.detail-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}
</style>
