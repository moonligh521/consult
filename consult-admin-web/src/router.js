import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import ShellView from './views/ShellView.vue'
import AccountsView from './views/AccountsView.vue'
import PatientsView from './views/PatientsView.vue'
import ConsultationsView from './views/ConsultationsView.vue'
import DepartmentsView from './views/DepartmentsView.vue'
import DoctorsView from './views/DoctorsView.vue'
import SchedulesView from './views/SchedulesView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: ShellView,
      redirect: '/accounts',
      children: [
        { path: 'accounts', name: 'accounts', component: AccountsView },
        { path: 'patients', name: 'patients', component: PatientsView },
        { path: 'consultations', name: 'consultations', component: ConsultationsView },
        { path: 'departments', name: 'departments', component: DepartmentsView },
        { path: 'doctors', name: 'doctors', component: DoctorsView },
        { path: 'schedules', name: 'schedules', component: SchedulesView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('consult-admin-token')
  if (to.meta.public) {
    return token && to.path === '/login' ? '/accounts' : true
  }
  return token ? true : '/login'
})

export default router
