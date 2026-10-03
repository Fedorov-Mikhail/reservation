import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir:'./e2e', workers:1, timeout:45000,
  use:{baseURL:'http://127.0.0.1:5173',channel:'msedge',headless:true,timezoneId:'Europe/Samara',viewport:{width:1366,height:1000},screenshot:'only-on-failure'},
  webServer:{command:'npm.cmd run dev',url:'http://127.0.0.1:5173',reuseExistingServer:true,timeout:30000},
});

