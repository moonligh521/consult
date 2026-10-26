import { createRouter, createWebHistory } from 'vue-router'
import ChatView from './views/ChatView.vue'
import LoginView from './views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'chat', component: ChatView },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('consult-token')
  if (to.meta.public) {
    return token && to.path === '/login' ? '/' : true
  }
  return token ? true : '/login'
})

export default router
