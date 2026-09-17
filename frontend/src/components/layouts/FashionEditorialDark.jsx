'use client';

import { Star } from 'lucide-react';

/**
 * Template "Editorial Dark Fashion" — portado de
 * cvfacil.ng-test-copy-20260916-194157/frontend/src/components/resume/templates/FashionEditorialDark.js
 *
 * Adaptação de dados: `data.headline` no lugar de `resume.profession`,
 * `data.photo` direto (sem `getPhotoUrl`), `data.education[].{school,period}`
 * no lugar de `{institution,fieldOfStudy,startDate,endDate}`,
 * `data.experience[].bullets[]` no lugar de `description` (parágrafo único).
 *
 * Bug conhecido do repositório de origem (já corrigido lá e preservado
 * aqui): o badge do site (`data.website`) fica ABAIXO da lista de
 * habilidades com margem própria — não deve ser reposicionado para dentro/
 * sobre a lista de skills.
 */
export function FashionEditorialDark({ theme, data }) {
  const red = theme.primary;
  const nameParts = (data.fullName || 'Seu Nome').trim().split(' ');
  const firstName = nameParts[0] || '';
  const lastName = nameParts.slice(1).join(' ') || '';
  const skills = data.skills || [];

  return (
    <div style={{
      fontFamily: "'Inter', sans-serif", color: '#fff',
      background: 'radial-gradient(circle at 30% 20%, #1a1a1a 0%, #0d0d0d 60%)',
      minHeight: '100%', display: 'flex',
    }}>
      {/* Left: photo + vertical name */}
      <div style={{ flex: '0 0 32%', display: 'flex', position: 'relative' }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <div style={{ width: '100%', height: 260, background: '#333', overflow: 'hidden', position: 'relative' }}>
            {data.photo ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={data.photo} alt="Foto" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              <div style={{ width: '100%', height: '100%', background: '#2a2a2a' }} />
            )}
            <div style={{ position: 'absolute', bottom: -8, left: -8, width: 70, height: 28, background: red }} />
          </div>

          <div style={{ padding: '20px 14px' }}>
            {data.education?.length > 0 && (
              <section style={{ marginBottom: 20 }}>
                <h3 style={{ fontSize: 13, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 10px 0' }}>Educação</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {data.education.map((edu, i) => (
                    <div key={i} style={{ fontSize: 11 }}>
                      <div style={{ fontWeight: 700 }}>{edu.period}</div>
                      <div style={{ opacity: 0.85 }}>{edu.degree}</div>
                      <div style={{ opacity: 0.7 }}>{edu.school}</div>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {skills.length > 0 && (
              <section>
                <h3 style={{ fontSize: 13, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 8px 0', borderBottom: `1px solid ${red}`, paddingBottom: 8 }}>
                  Habilidades
                </h3>
                <ul style={{ listStyle: 'none', padding: 0, margin: '10px 0 0 0', display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {skills.map((s, i) => (
                    <li key={i} style={{ fontSize: 11, display: 'flex', gap: 8, alignItems: 'center' }}>
                      <span style={{ color: red }}>■</span> {typeof s === 'string' ? s : s.name}
                    </li>
                  ))}
                </ul>
              </section>
            )}

            {/* Badge do site — sempre abaixo das habilidades, nunca sobreposto */}
            {data.website && (
              <div style={{ marginTop: 20, background: red, padding: '7px 12px', display: 'inline-block' }}>
                <span style={{ fontWeight: 700, fontSize: 11, wordBreak: 'break-word' }}>{data.website}</span>
              </div>
            )}
          </div>
        </div>

        {/* Vertical name band */}
        <div style={{ flex: '0 0 52px', borderLeft: `2px solid ${red}`, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px 0' }}>
          <div style={{ writingMode: 'vertical-rl', transform: 'rotate(180deg)', fontWeight: 900, fontSize: '1.4rem', letterSpacing: 2, whiteSpace: 'nowrap' }}>
            <span style={{ color: '#fff' }}>{firstName} </span>
            <span style={{ color: red }}>{lastName}</span>
          </div>
        </div>
      </div>

      {/* Right: main content */}
      <div style={{ flex: '0 0 68%', padding: '32px 32px 32px 24px', minWidth: 0 }}>
        <h1 style={{ fontSize: '1.9rem', fontWeight: 900, textTransform: 'uppercase', margin: 0, lineHeight: 1.15, wordBreak: 'break-word' }}>
          {data.headline || 'Profissional'}
        </h1>
        <div style={{ marginTop: 12, fontSize: 12, lineHeight: 1.8, opacity: 0.9 }}>
          {data.phone && <div>{data.phone}</div>}
          {data.email && <div>{data.email}</div>}
          {data.location && <div>{data.location}</div>}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '20px 0' }}>
          <div style={{ flex: 1, height: 1, background: red }} />
          <Star size={14} color={red} fill={red} />
        </div>

        {data.summary && (
          <section style={{ marginBottom: 22 }}>
            <h3 style={{ fontSize: 14, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 8px 0' }}>Sobre mim</h3>
            <p style={{ fontSize: 12, lineHeight: 1.7, margin: 0, opacity: 0.9 }}>{data.summary}</p>
          </section>
        )}

        {data.experience?.length > 0 && (
          <section>
            <h3 style={{ fontSize: 14, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 12px 0' }}>Experiência</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {data.experience.map((exp, i) => {
                const bullets = (exp.bullets || []).filter((b) => b.trim());
                return (
                  <div key={i}>
                    <div style={{ color: red, fontWeight: 700, fontSize: 12 }}>
                      {exp.role}{exp.role && exp.company ? ' / ' : ''}{exp.company}{exp.period ? ` / ${exp.period}` : ''}
                    </div>
                    {bullets.length > 0 && (
                      <ul style={{ margin: '6px 0 0 16px', padding: 0, fontSize: 11, lineHeight: 1.6, opacity: 0.9 }}>
                        {bullets.map((b, bi) => <li key={bi}>{b}</li>)}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

export default FashionEditorialDark;
