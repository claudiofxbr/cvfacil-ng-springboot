'use client';

/**
 * Template "Teal Círculos Arredondados" — portado de
 * cvfacil.ng-test-copy-20260916-194157/frontend/src/components/resume/templates/TealRoundedCircles.js
 *
 * Adaptação de dados: `data.headline` no lugar de `resume.profession`,
 * `data.photo` direto, `data.location` no lugar de `resume.address`,
 * `data.education[].{school,period}` e `data.experience[].bullets[]`
 * (renderizados como lista, não como parágrafo único de `description`).
 *
 * Bug conhecido do repositório de origem (já corrigido lá e preservado
 * aqui): a foto circular sobreposta ficava colidindo com o nome no header.
 * A correção original é o espaçador antes das seções de Educação/Contato,
 * que empurra o conteúdo para baixo do círculo (posicionado em `top: 105px`,
 * diâmetro 165px → base do círculo em y≈270px); os espaçadores (220px no
 * painel esquerdo, 255px no direito, já somando o padding do painel)
 * garantem uma folga de ~15px abaixo do círculo — não remover/reduzir sem
 * recalcular contra a posição e o diâmetro do círculo.
 *
 * Bug NOVO encontrado nesta validação (dados mais longos que o normal): um
 * nome muito longo dentro do `maxWidth: 72%` do título quebrava em muitas
 * linhas (inclusive no meio de palavras, com `wordBreak: 'break-word'`),
 * ficando alto o suficiente para invadir a área do círculo mesmo com os
 * espaçadores acima. Corrigido reduzindo o tamanho da fonte do nome
 * conforme o comprimento do texto (`nameFontSize`), o que mantém o bloco de
 * nome+cargo em poucas linhas e dentro da folga calculada para o círculo.
 */
function nameFontSize(fullName) {
  const len = (fullName || '').length;
  if (len > 40) return 26;
  if (len > 25) return 32;
  return 38;
}

export function TealRoundedCircles({ theme, data }) {
  const teal = theme.primary;
  const offWhite = '#f2f1ee';
  const skills = data.skills || [];
  const fontSize = nameFontSize(data.fullName);

  return (
    <div style={{ fontFamily: "'Inter', sans-serif", color: '#222', background: offWhite, minHeight: '100%', display: 'flex', position: 'relative', overflow: 'hidden' }}>
      {/* Left panel */}
      <div style={{ flex: '0 0 62%', background: offWhite, padding: '32px 32px 32px 32px', position: 'relative', zIndex: 1, minWidth: 0 }}>
        <h1 style={{ fontSize, fontWeight: 800, color: teal, margin: 0, lineHeight: 1.15, maxWidth: '72%', wordBreak: 'break-word' }}>
          {data.fullName || 'Seu Nome'}
        </h1>
        <p style={{ marginTop: 8, fontSize: 12, color: '#555', maxWidth: '72%' }}>
          {data.headline || ''}
        </p>

        {/* Espaçador — mantém o conteúdo abaixo do círculo sobreposto (ver nota acima) */}
        <div style={{ height: 220 }} />

        {data.education?.length > 0 && (
          <section style={{ marginBottom: 24 }}>
            <div style={{ background: teal, color: '#fff', fontWeight: 700, borderRadius: 50, height: 40, display: 'flex', alignItems: 'center', paddingLeft: 20, fontSize: 12.5 }}>
              Educação
            </div>
            <ul style={{ margin: '14px 0 0 0', paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {data.education.map((edu, i) => (
                <li key={i} style={{ fontSize: 12 }}>
                  <div style={{ fontWeight: 700, color: '#111' }}>{edu.degree}</div>
                  <div style={{ color: '#555' }}>{edu.school}{edu.school && edu.period ? ' · ' : ''}{edu.period}</div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {data.experience?.length > 0 && (
          <section>
            <div style={{ background: teal, color: '#fff', fontWeight: 700, borderRadius: 50, height: 40, display: 'flex', alignItems: 'center', paddingLeft: 20, fontSize: 12.5 }}>
              Experiências
            </div>
            <ul style={{ margin: '14px 0 0 0', paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {data.experience.map((exp, i) => {
                const bullets = (exp.bullets || []).filter((b) => b.trim());
                return (
                  <li key={i} style={{ fontSize: 12 }}>
                    <div style={{ fontWeight: 700, color: '#111' }}>{exp.role}</div>
                    <div style={{ color: '#555' }}>{exp.company}{exp.company && exp.period ? ' · ' : ''}{exp.period}</div>
                    {bullets.length > 0 && (
                      <ul style={{ margin: '4px 0 0 16px', padding: 0, color: '#444', lineHeight: 1.5 }}>
                        {bullets.map((b, bi) => <li key={bi}>{b}</li>)}
                      </ul>
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </div>

      {/* Right panel */}
      <div style={{ flex: '0 0 38%', background: teal, color: '#fff', padding: '32px 24px 32px 48px', position: 'relative', zIndex: 1, minWidth: 0 }}>
        {/* Espaçador — mantém o conteúdo abaixo do círculo sobreposto (ver nota acima) */}
        <div style={{ height: 255 }} />

        <section style={{ marginBottom: 20 }}>
          <h3 style={{ margin: '0 0 10px 0', fontSize: 13, fontWeight: 800 }}>Contato</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 11.5, wordBreak: 'break-word' }}>
            {data.phone && <div>{data.phone}</div>}
            {data.email && <div>{data.email}</div>}
            {data.location && <div>{data.location}</div>}
          </div>
        </section>

        <div style={{ borderTop: '1px solid rgba(255,255,255,0.35)', margin: '16px 0' }} />

        {data.languages?.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <h4 style={{ margin: '0 0 8px 0', fontSize: 12, fontWeight: 700 }}>Idiomas</h4>
            <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 4 }}>
              {data.languages.map((lang, i) => (
                <li key={i} style={{ fontSize: 11 }}>{lang.name}{lang.level ? ` — ${lang.level}` : ''}</li>
              ))}
            </ul>
          </div>
        )}

        {skills.length > 0 && (
          <div>
            <h4 style={{ margin: '0 0 8px 0', fontSize: 12, fontWeight: 700 }}>Competências</h4>
            <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 4 }}>
              {skills.map((s, i) => (
                <li key={i} style={{ fontSize: 11 }}>{typeof s === 'string' ? s : s.name}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Overlapping circle with photo */}
      <div style={{
        position: 'absolute', top: 105, left: '55%', transform: 'translateX(-50%)',
        width: 165, height: 165, borderRadius: '50%', background: teal,
        zIndex: 2, display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <div style={{ width: 113, height: 113, borderRadius: '50%', border: '4px solid #fff', overflow: 'hidden', transform: 'translate(15px, 15px)' }}>
          {data.photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={data.photo} alt="Foto" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', background: '#0e2d29' }} />
          )}
        </div>
      </div>
    </div>
  );
}

export default TealRoundedCircles;
