# 微服务商城 - 前端

基于 Vue3 + Element Plus 的微服务商城前端项目。

## 技术栈

- Vue 3 + Composition API
- Element Plus (UI组件库)
- Vue Router 4 (路由)
- Pinia (状态管理)
- Axios (HTTP请求)
- Vite 5 (构建工具)

## 快速启动

```bash
# 安装依赖
npm install

# 启动开发服务器（端口3000）
npm run dev

# 构建生产版本
npm run build
```

## 项目结构

```
src/
├── api/            # API接口定义
│   ├── request.js  # Axios封装（拦截器、错误处理）
│   ├── product.js  # 商品服务API
│   ├── order.js    # 订单服务API
│   └── user.js     # 用户服务API
├── store/          # Pinia状态管理
│   └── user.js     # 用户状态（登录、token）
├── router/         # 路由配置
│   └── index.js    # 路由定义+权限守卫
├── layouts/        # 布局组件
│   └── MainLayout.vue  # 主布局（侧边栏+顶栏）
├── views/          # 页面组件
│   ├── login/      # 登录页
│   ├── home/       # 首页
│   ├── product/    # 商品列表+详情
│   ├── order/      # 订单管理
│   ├── user/       # 个人中心
│   └── error/      # 404页面
└── styles/         # 全局样式
    └── index.css
```

## API代理配置

开发环境通过 Vite proxy 转发请求到 Gateway (localhost:9000)：
- `/product/**` → 商品服务
- `/order/**` → 订单服务
- `/user/**` → 用户服务

## 权限说明

- 登录后自动在请求头注入 JWT Bearer token
- 非白名单路径自动附带 token 参数（AuthGlobalFilter要求）
- 401响应自动跳转登录页
- 429响应提示限流信息
