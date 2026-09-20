/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        // Body / UI text — closest well-supported equivalent to "Peace Sans"
        sans: ['Poppins', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        // Headings / display text — closest well-supported equivalent to "Open Sauce"
        display: ['Sora', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      colors: {
        // Neutral text/background/border scale, re-tuned from a warm sandstone
        // at the light end into a deep sea-glass charcoal at the dark end.
        // Every existing `slate-*` utility in the app picks this up automatically.
        slate: {
          50: '#FBF7EC',
          100: '#F5EEDD',
          200: '#EDE0C2',
          300: '#D9C69B',
          400: '#A99B7C',
          500: '#7C8681',
          600: '#5C6B65',
          700: '#3E4F49',
          800: '#25332E',
          900: '#0F2420',
          950: '#081815',
        },
        // Primary brand / interactive scale — deep ocean teal. Every existing
        // `blue-*` utility (primary buttons, links, focus rings) picks this up.
        blue: {
          50: '#EAF6F1',
          100: '#CFE7E1',
          200: '#9DCFC4',
          300: '#6CB6A6',
          400: '#3FA79A',
          500: '#1A9186',
          600: '#0F6259',
          700: '#0A3733',
          800: '#082D2A',
          900: '#062421',
          950: '#041917',
        },
        // Sunset coral — kept for decorative tints (category icon chips etc.),
        // no longer used for primary CTAs.
        coral: {
          50: '#FFF1EC',
          100: '#FFE0D4',
          200: '#FFC1AA',
          300: '#FF9C7D',
          400: '#FF7F5C',
          500: '#F0532E',
          600: '#D9432B',
          700: '#B23422',
          800: '#8C2A1C',
          900: '#6B2115',
        },
        // Deep cobalt blue — the primary CTA / interactive accent, paired with
        // white. Distinct from the muted sea-glass `blue` (ocean) neutral scale.
        cobalt: {
          50: '#EAF0FF',
          100: '#CFE0FF',
          200: '#9EC0FF',
          300: '#6B9AFF',
          400: '#3E72F2',
          500: '#2454DD',
          600: '#1B3FC2',
          700: '#14319C',
          800: '#0F2678',
          900: '#0B1B57',
        },
        // Status semantics re-tuned to sit inside the coastal palette.
        green: {
          50: '#EFF8F1',
          100: '#E1F3EA',
          200: '#BFE6D0',
          500: '#22A06B',
          600: '#1C8A5C',
          700: '#1C7A50',
          800: '#175F40',
        },
        amber: {
          50: '#FEF8ED',
          100: '#FCEFD8',
          200: '#F6DCAC',
          500: '#DE9A3C',
          600: '#C6822A',
          700: '#A16A1F',
          800: '#7E5219',
        },
        red: {
          50: '#FDEDED',
          100: '#FADBDB',
          200: '#F3B8B8',
          500: '#E23B3B',
          600: '#CC2E2E',
          700: '#A32424',
          800: '#7E1C1C',
        },
        sky: {
          50: '#EDF6F7',
          100: '#D9EDEF',
          200: '#B3DBE0',
          500: '#3B93A0',
          600: '#2E7986',
          700: '#25626C',
          800: '#1E4E56',
        },
      },
      borderRadius: {
        xl: '1rem',
        '2xl': '1.25rem',
      },
      boxShadow: {
        soft: '0 12px 28px -8px rgba(10, 55, 51, 0.16)',
      },
    },
  },
  plugins: [],
}
