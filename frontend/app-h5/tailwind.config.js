/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,ts,tsx}',
    '../client-h5/src/**/*.{vue,ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: '#1677ff',
        'primary-light': '#e6f4ff',
        'msg-user': '#1677ff',
        'msg-agent': '#f5f5f5',
      },
    },
  },
  plugins: [],
}
