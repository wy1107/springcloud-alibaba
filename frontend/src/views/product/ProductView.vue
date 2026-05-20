<template>
  <div class="product-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">商品列表</span>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索商品..."
            :prefix-icon="Search"
            style="width: 240px"
            clearable
          />
        </div>
      </template>

      <!-- 商品卡片网格 -->
      <el-row :gutter="16" v-loading="loading">
        <el-col
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
          v-for="product in filteredProducts"
          :key="product.pid"
        >
          <el-card
            class="product-card"
            shadow="hover"
            @click="goDetail(product.pid)"
          >
            <div class="product-img">
              <el-icon :size="60" color="#C0C4CC"><Goods /></el-icon>
            </div>
            <div class="product-info">
              <h3 class="product-name">{{ product.pname }}</h3>
              <p class="product-price">
                <span class="price-symbol">¥</span>
                <span class="price-value">{{ product.pprice?.toFixed(2) }}</span>
              </p>
              <div class="product-stock">
                <el-tag
                  :type="product.stock > 20 ? 'success' : product.stock > 0 ? 'warning' : 'danger'"
                  size="small"
                >
                  {{ product.stock > 0 ? `库存: ${product.stock}` : '已售罄' }}
                </el-tag>
              </div>
            </div>
            <div class="product-actions">
              <el-button
                type="primary"
                size="small"
                :disabled="product.stock <= 0"
                @click.stop="handleBuy(product)"
              >
                立即购买
              </el-button>
              <el-button size="small" @click.stop="handleSeckill(product)">
                秒杀
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && filteredProducts.length === 0"
        description="暂无商品"
      />
    </el-card>

    <!-- 购买确认对话框 -->
    <el-dialog
      v-model="buyDialogVisible"
      title="确认购买"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-descriptions :column="1" border v-if="selectedProduct">
        <el-descriptions-item label="商品名称">{{ selectedProduct.pname }}</el-descriptions-item>
        <el-descriptions-item label="商品价格">¥{{ selectedProduct.pprice?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="库存数量">{{ selectedProduct.stock }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="buyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="buying" @click="confirmBuy">确认下单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { productApi } from '@/api/product'
import { orderApi } from '@/api/order'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const buying = ref(false)
const buyDialogVisible = ref(false)
const selectedProduct = ref(null)
const searchKeyword = ref('')
const products = ref([])

// 模拟商品列表数据（后端仅有按ID查询接口，前端维护列表用于展示）
const mockProducts = [
  { pid: 1, pname: '小米手机', pprice: 1999.00, stock: 100 },
  { pid: 2, pname: '华为笔记本', pprice: 5999.00, stock: 50 },
  { pid: 3, pname: '苹果平板', pprice: 3999.00, stock: 80 }
]

const filteredProducts = computed(() => {
  if (!searchKeyword.value) return products.value
  const keyword = searchKeyword.value.toLowerCase()
  return products.value.filter(p =>
    p.pname.toLowerCase().includes(keyword)
  )
})

onMounted(async () => {
  loading.value = true
  try {
    // 尝试从后端获取每个商品的真实数据
    const fetched = []
    for (const mock of mockProducts) {
      try {
        const real = await productApi.findById(mock.pid)
        if (real) {
          fetched.push(real)
          continue
        }
      } catch (e) {
        // 后端不可用时使用mock数据
      }
      fetched.push(mock)
    }
    products.value = fetched
  } finally {
    loading.value = false
  }
})

function goDetail(pid) {
  router.push(`/product/${pid}`)
}

function handleBuy(product) {
  selectedProduct.value = product
  buyDialogVisible.value = true
}

async function confirmBuy() {
  if (!selectedProduct.value) return
  buying.value = true
  try {
    const order = await orderApi.create(
      selectedProduct.value.pid,
      Number(userStore.userId)
    )
    ElMessage.success('下单成功！')
    buyDialogVisible.value = false
    // 刷新商品库存
    try {
      const updated = await productApi.findById(selectedProduct.value.pid)
      if (updated) {
        const idx = products.value.findIndex(p => p.pid === updated.pid)
        if (idx !== -1) products.value[idx] = updated
      }
    } catch (e) { /* ignore */ }
  } catch (error) {
    ElMessage.error('下单失败：' + (error.message || '请稍后重试'))
  } finally {
    buying.value = false
  }
}

async function handleSeckill(product) {
  try {
    const result = await productApi.seckill(product.pid)
    ElMessage.success(result || '秒杀成功！')
  } catch (error) {
    ElMessage.warning('秒杀失败：' + (error.message || '请稍后重试'))
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.card-title {
  font-weight: 600;
  font-size: 16px;
}

.product-card {
  margin-bottom: 16px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border-radius: 8px;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.product-img {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 140px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  border-radius: 6px;
  margin-bottom: 12px;
}

.product-name {
  font-size: 15px;
  color: #303133;
  margin: 0 0 8px;
}

.product-price {
  margin: 0 0 8px;
  color: #F56C6C;
}

.price-symbol {
  font-size: 14px;
}

.price-value {
  font-size: 22px;
  font-weight: 600;
}

.product-stock {
  margin-bottom: 12px;
}

.product-actions {
  display: flex;
  gap: 8px;
}
</style>
