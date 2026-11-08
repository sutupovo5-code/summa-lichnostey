/* Настройки Tailwind. После правки классов в index.html пересоберите стили: npm run build:css */
const v = name => `rgb(var(${name}) / <alpha-value>)`;
module.exports = {
  content: ['./index.html'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        display: ['Unbounded', 'sans-serif'],
        brush: ['Russo One', 'sans-serif'],
      },
      colors: {
        dc: { 950: '#040b1a', 900: '#07142e', 800: '#0c234e', 700: '#163878' },
        // акцентные цвета берутся из CSS-переменных — их меняют темы магазина
        neon: { pink: v('--ac'), light: v('--ac2') },
        pink: { 500: v('--ac') },
        sky: { 400: v('--ac3') },
      },
    },
  },
};
