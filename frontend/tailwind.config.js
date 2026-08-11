/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{js,jsx,ts,tsx,mdx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        serif: ['Merriweather', 'Georgia', 'serif'],
        display: ['Poppins', 'Inter', 'sans-serif'],
      },
      colors: {
        // Paleta base da marca; as 9 paletas dos layouts ficam em src/lib/theme.js
        brand: {
          50: '#F2F6FB',
          100: '#D8E4F2',
          500: '#2E5A88',
          700: '#1F3A5F',
          900: '#0B2040',
        },
      },
      keyframes: {
        fadeIn: { '0%': { opacity: 0 }, '100%': { opacity: 1 } },
        slideUp: {
          '0%': { opacity: 0, transform: 'translateY(8px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'fadeIn 250ms ease-out both',
        'slide-up': 'slideUp 300ms ease-out both',
      },
    },
  },
  plugins: [],
};
