import { createApp } from 'vue'
import App from './App.vue'
import { createRentalRouter } from './router'
import './style.css'
import './comfort.css'
import './member-layout.css'
createApp(App).use(createRentalRouter()).mount('#app')
