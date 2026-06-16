import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'io.ionic.starter',
  appName: 'CAP-KEYB-SAMPLE',
  webDir: 'dist',
  backgroundColor: "#fffb38",
  plugins: {
    Keyboard: {
      autoBackdropColor: 'auto'
    }
  }
};

export default config;
