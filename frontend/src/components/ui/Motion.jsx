'use client';
import { motion } from 'framer-motion';

export function FadeIn({ children, delay = 0, ...rest }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: 'easeOut', delay }}
      // Garante visibilidade mesmo se a animação não completar (JS lento / CSP)
      style={{ willChange: 'opacity, transform' }}
      {...rest}
    >
      {children}
    </motion.div>
  );
}

export function Stagger({ children, step = 0.06 }) {
  return (
    <motion.div
      initial="hidden"
      animate="show"
      variants={{
        hidden: {},
        show: { transition: { staggerChildren: step } },
      }}
    >
      {children}
    </motion.div>
  );
}

export function StaggerItem({ children }) {
  return (
    <motion.div
      variants={{
        hidden: { opacity: 0, y: 10 },
        show: { opacity: 1, y: 0, transition: { duration: 0.3 } },
      }}
    >
      {children}
    </motion.div>
  );
}
