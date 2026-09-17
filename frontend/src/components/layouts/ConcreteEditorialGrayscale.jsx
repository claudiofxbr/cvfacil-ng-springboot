'use client';

/**
 * Template "Editorial Concreto Grayscale" — portado de
 * cvfacil.ng-test-copy-20260916-194157/frontend/src/components/resume/templates/ConcreteEditorialGrayscale.js
 *
 * Adaptação de dados: `data.headline` no lugar de `resume.profession`,
 * `data.photo` direto, `data.education[].{school,period}` no lugar de
 * `{institution,fieldOfStudy,startDate,endDate}`, `data.experience[].bullets[]`
 * no lugar de `description`.
 */
export function ConcreteEditorialGrayscale({ theme, data }) {
  const ink = theme.primary;
  const nameParts = (data.fullName || 'Seu Nome').trim().split(' ');
  const firstName = nameParts[0] || '';
  const lastName = nameParts.slice(1).join(' ') || '';
  const skills = data.skills || [];

  return (
    <div style={{
      fontFamily: "'Inter', sans-serif", color: '#1a1a1a',
      background: 'linear-gradient(160deg, #dedede 0%, #d3d3d3 100%)',
      minHeight: '100%', display: 'grid', gridTemplateColumns: '1fr 1.2fr', padding: '36px',
    }}>
      {/* Left column */}
      <div style={{ display: 'flex', flexDirection: 'column', paddingRight: 24, minWidth: 0 }}>
        <h1 style={{ fontSize: 44, fontWeight: 800, margin: 0, lineHeight: 1.0, color: ink, wordBreak: 'break-word' }}>
          {firstName}<br />{lastName}
        </h1>
        <div style={{ marginTop: 16, fontSize: 12, lineHeight: 1.9, color: '#333' }}>
          {data.website && <div>{data.website}</div>}
          {data.email && <div>{data.email}</div>}
          {data.phone && <div>{data.phone}</div>}
        </div>

        <div style={{ flex: 1, minHeight: 24 }} />

        <div style={{ width: '100%', height: 200, overflow: 'hidden', marginTop: 24 }}>
          {data.photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={data.photo} alt="Foto" style={{ width: '100%', height: '100%', objectFit: 'cover', filter: 'grayscale(100%)' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', background: '#bbb', filter: 'grayscale(100%)' }} />
          )}
        </div>

        {data.headline && (
          <h2 style={{ fontSize: '1.7rem', fontWeight: 800, margin: '16px 0 0 0', color: ink, lineHeight: 1.15, wordBreak: 'break-word' }}>
            {data.headline.split(' ').map((w, i) => <div key={i}>{w}</div>)}
          </h2>
        )}
      </div>

      {/* Right column */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 24, paddingLeft: 24, borderLeft: '1px solid #999', minWidth: 0 }}>
        {data.summary && (
          <section>
            <h3 style={{ fontSize: 14, fontWeight: 800, borderBottom: `2px solid ${ink}`, paddingBottom: 6, margin: '0 0 12px 0' }}>Perfil</h3>
            <p style={{ fontSize: 12, lineHeight: 1.7, margin: 0, color: '#222' }}>{data.summary}</p>
          </section>
        )}

        {data.education?.length > 0 && (
          <section>
            <h3 style={{ fontSize: 14, fontWeight: 800, borderBottom: `2px solid ${ink}`, paddingBottom: 6, margin: '0 0 12px 0' }}>Educação</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {data.education.map((edu, i) => (
                <div key={i} style={{ fontSize: 12, color: '#222' }}>
                  <div style={{ fontWeight: 700 }}>{edu.school}</div>
                  <div>{edu.degree}{edu.degree && edu.period ? ' · ' : ''}{edu.period}</div>
                </div>
              ))}
            </div>
          </section>
        )}

        {data.experience?.length > 0 && (
          <section>
            <h3 style={{ fontSize: 14, fontWeight: 800, borderBottom: `2px solid ${ink}`, paddingBottom: 6, margin: '0 0 12px 0' }}>Experiências</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {data.experience.map((exp, i) => {
                const bullets = (exp.bullets || []).filter((b) => b.trim());
                return (
                  <div key={i} style={{ fontSize: 12, color: '#222' }}>
                    <div style={{ fontWeight: 700 }}>{exp.role}{exp.role && exp.company ? ' — ' : ''}{exp.company}</div>
                    <div style={{ opacity: 0.8, marginBottom: 4 }}>{exp.period}</div>
                    {bullets.length > 0 && (
                      <ul style={{ margin: 0, paddingLeft: 16, lineHeight: 1.6 }}>
                        {bullets.map((b, bi) => <li key={bi}>{b}</li>)}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        )}

        {skills.length > 0 && (
          <section>
            <h3 style={{ fontSize: 14, fontWeight: 800, borderBottom: `2px solid ${ink}`, paddingBottom: 6, margin: '0 0 12px 0' }}>Habilidades</h3>
            <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {skills.map((s, i) => (
                <li key={i} style={{ fontSize: 12, color: '#222' }}>{typeof s === 'string' ? s : s.name}</li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  );
}

export default ConcreteEditorialGrayscale;
