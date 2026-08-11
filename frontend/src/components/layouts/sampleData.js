export const SAMPLE_RESUME = {
  fullName: 'Ana Beatriz Carvalho',
  headline: 'Engenheira de Software Sênior',
  email: 'ana.carvalho@email.com',
  phone: '+55 11 98765-4321',
  location: 'São Paulo, Brasil',
  website: 'anacarvalho.dev',
  photo: null,
  summary:
    'Engenheira full-stack com 8+ anos de experiência em aplicações web escaláveis, arquitetura de microsserviços e liderança técnica de squads multidisciplinares.',
  skills: [
    { name: 'Java / Spring Boot', pct: 90 },
    { name: 'Next.js / React',    pct: 85 },
    { name: 'PostgreSQL / Redis', pct: 80 },
    { name: 'AWS / Docker',       pct: 75 },
    { name: 'Arquitetura de Software', pct: 85 },
    { name: 'Metodologias Ágeis', pct: 90 },
  ],
  experience: [
    {
      role: 'Senior Software Engineer',
      company: 'Nimbus Labs',
      period: '2023 — hoje',
      bullets: [
        'Liderou migração de monolito para microsserviços, reduzindo latência em 38%.',
        'Implementou pipeline CI/CD com GitHub Actions e deploy blue/green.',
        'Reduziu custo de infraestrutura em 22% via otimização de containers.',
        'Desenvolveu API Gateway com rate limiting e autenticação OAuth2.',
        'Mentoria técnica de 4 engenheiros plenos e 2 juniores.',
        '', '', '', '', '',
      ],
    },
    {
      role: 'Software Engineer',
      company: 'FinTrail',
      period: '2019 — 2023',
      bullets: [
        'Desenvolveu motor antifraude em Java + Kafka processando 10 k req/s.',
        'Criou sistema de relatórios em tempo real com WebSockets e Redis Pub/Sub.',
        'Integrou 3 parceiros de pagamento via APIs REST, aumentando conversão em 15%.',
        'Implementou suite de testes com cobertura de 87% (JUnit + Mockito + Testcontainers).',
        'Conduziu revisões de código e definiu padrões de engenharia do time.',
        '', '', '', '', '',
      ],
    },
  ],
  education: [
    {
      degree: 'Bacharelado em Ciência da Computação',
      school: 'Universidade de São Paulo',
      period: '2014 — 2018',
    },
  ],
  languages: [
    { name: 'Português Brasileiro', level: 'Nativo' },
    { name: 'Inglês Americano (C1)', level: 'Fluente' },
    { name: 'Espanhol (B2)',         level: 'Intermediário' },
  ],
  hobbies: ['Leitura técnica', 'Corrida', 'Xadrez', 'Fotografia', 'Cozinhar'],
};
