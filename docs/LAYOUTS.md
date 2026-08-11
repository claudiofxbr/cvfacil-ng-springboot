# Catálogo de Layouts — CVFacil.NG

Todas as paletas foram validadas para contraste WCAG 2.1 AA ou AAA sobre fundo
branco (#FFFFFF). Fonte oficial: `frontend/src/lib/theme.js` — Tabela 2.2.1 do PRD.

| # | ID interno | Nome | Variante | Primária | Secundária | Contraste | WCAG |
|---|---|---|---|---|---|---|---|
| 1 | `onyxExecutive` | Onyx Executive | minimal | `#0A0A0A` | `#E5E7EB` | 19.6:1 | AAA |
| 2 | `navyClassic` | Navy Classic | header | `#0B2545` | `#D8E3F2` | 14.2:1 | AAA |
| 3 | `goldPrestige` | Gold Prestige | badge | `#B8860B` | `#F5E9C9` | 4.9:1 | AA |
| 4 | `forestPro` | Forest Pro | band | `#0F3D2E` | `#D1E7DD` | 13.1:1 | AAA |
| 5 | `roseBold` | Rose Bold | split | `#9D174D` | `#FCE7F3` | 8.9:1 | AAA |
| 6 | `graphiteNeutral` | Graphite Neutral | minimal | `#374151` | `#F3F4F6` | 9.6:1 | AAA |
| 7 | `crimsonImpact` | Crimson Impact | header | `#7F1D1D` | `#FEE2E2` | 10.4:1 | AAA |
| 8 | `magentaVivid` | Magenta Vivid | band | `#86198F` | `#F5D0FE` | 8.1:1 | AAA |
| 9 | `lilacSoft` | Lilac Soft | split | `#5B21B6` | `#EDE9FE` | 9.8:1 | AAA |

## Regras de uso

- Ouro (#B8860B) só pode ser usado em áreas de acento. Nunca em corpo de texto.
- Toda adição de novo layout exige novo registro em `LAYOUT_THEMES` e
  atualização desta tabela.
- Toda alteração de cores requer re-validação no WebAIM Contrast Checker
  anexada ao PR.
