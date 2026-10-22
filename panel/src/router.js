import { createRouter, createWebHistory } from 'vue-router'
import Board from './views/Board.vue'
import Audit from './views/Audit.vue'
import Cost from './views/Cost.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/board' },
    { path: '/board', component: Board },
    { path: '/audit', component: Audit },
    { path: '/cost', component: Cost }
  ]
})
