import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页', icon: 'HomeFilled' }
      },
      {
        path: 'product',
        name: 'Product',
        component: () => import('@/views/product/ProductView.vue'),
        meta: { title: '商品列表', icon: 'Goods' }
      },
      {
        path: 'product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/product/ProductDetailView.vue'),
        meta: { title: '商品详情', hidden: true }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/OrderView.vue'),
        meta: { title: '我的订单', icon: 'List' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/UserView.vue'),
        meta: { title: '个人中心', icon: 'User' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || 'Shop'} - 商城`
  const userStore = useUserStore()

  if (to.meta.requiresAuth !== false && !userStore.isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (to.name === 'Login' && userStore.isLoggedIn) {
    next({ name: 'Home' })
  } else {
    next()
  }
})

export default router
