'use client';

/**
 * Template "Sidebar Navy Engenharia" — portado de
 * cvfacil.ng-test-copy-20260916-194157/frontend/src/components/resume/templates/NavySidebarEngineer.js
 *
 * Adaptação de dados: `data.headline` no lugar de `resume.profession`,
 * `data.photo` direto, `data.location` no lugar de `resume.address`,
 * `data.education[].{school,period}` no lugar de `{institution,startDate,
 * endDate}`, `data.experience[].bullets[]` no lugar de `description`
 * (que na origem era um texto com `\n` quebrado manualmente em linhas —
 * aqui já chega como array).
 */
export function NavySidebarEngineer({ theme, data }) {
  const navy = theme.primary;
  const lightBlue = theme.secondary;
  const onSecondary = theme.onSecondary;
  const skills = data.skills || [];

  return (
    <div style={{ fontFamily: "'Inter', sans-serif", color: '#111', background: '#ffffff', minHeight: '100%', display: 'flex' }}>
      {/* Sidebar */}
      <aside style={{ flex: '0 0 34%', background: navy, color: '#fff', padding: '28px 20px', display: 'flex', flexDirection: 'column', gap: 22 }}>
        <div style={{ width: '100%', height: 160, border: '3px solid #fff', overflow: 'hidden' }}>
          {data.photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={data.photo} alt="Foto" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', background: '#2a4c72' }} />
          )}
        </div>

        <section>
          <h3 style={{ color: lightBlue, fontSize: 12.5, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 10px 0' }}>Contato</h3>
          <div style={{ display: 'flex', flexDirection: 'column', fontSize: 11.5, wordBreak: 'break-word' }}>
            {data.location && (
              <>
                <div style={{ padding: '6px 0' }}>{data.location}</div>
                <div style={{ borderTop: '1px solid rgba(255,255,255,0.3)' }} />
              </>
            )}
            {data.phone && (
              <>
                <div style={{ padding: '6px 0' }}>{data.phone}</div>
                <div style={{ borderTop: '1px solid rgba(255,255,255,0.3)' }} />
              </>
            )}
            {data.email && <div style={{ padding: '6px 0' }}>{data.email}</div>}
          </div>
        </section>

        {data.education?.length > 0 && (
          <section>
            <h3 style={{ color: lightBlue, fontSize: 12.5, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 10px 0' }}>Formação</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 11.5 }}>
              {data.education.map((edu, i) => (
                <div key={i}>
                  <div style={{ fontWeight: 700 }}>{edu.degree}</div>
                  <div>{edu.school}</div>
                  <div style={{ opacity: 0.85 }}>{edu.period}</div>
                </div>
              ))}
            </div>
          </section>
        )}

        {skills.length > 0 && (
          <section>
            <h3 style={{ color: lightBlue, fontSize: 12.5, fontWeight: 800, textTransform: 'uppercase', margin: '0 0 10px 0' }}>Habilidades</h3>
            <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 5 }}>
              {skills.map((s, i) => (
                <li key={i} style={{ fontSize: 11.5 }}>{typeof s === 'string' ? s : s.name}</li>
              ))}
            </ul>
          </section>
        )}
      </aside>

      {/* Main */}
      <main style={{ flex: '0 0 66%', display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <div style={{ background: lightBlue, padding: '26px 32px' }}>
          <h1 style={{ margin: 0, fontSize: '1.85rem', fontWeight: 800, color: onSecondary, textTransform: 'uppercase', wordBreak: 'break-word' }}>
            {data.fullName || 'Seu Nome'}
          </h1>
          <p style={{ margin: '5px 0 0 0', fontSize: 13, fontWeight: 700, color: onSecondary }}>
            {(data.headline || 'Profissional').toUpperCase()}
          </p>
        </div>

        <div style={{ padding: '26px 32px', display: 'flex', flexDirection: 'column', gap: 22 }}>
          {data.summary && (
            <section>
              <h3 style={{ fontSize: 15, fontWeight: 800, color: '#111', margin: '0 0 6px 0' }}>Perfil</h3>
              <div style={{ height: 2, background: '#111', width: 50, marginBottom: 10 }} />
              <p style={{ margin: 0, fontSize: 12, lineHeight: 1.7, color: '#222' }}>{data.summary}</p>
            </section>
          )}

          {data.experience?.length > 0 && (
            <section>
              <h3 style={{ fontSize: 15, fontWeight: 800, color: '#111', margin: '0 0 6px 0' }}>Experiência profissional</h3>
              <div style={{ height: 2, background: '#111', width: 50, marginBottom: 10 }} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {data.experience.map((exp, i) => {
                  const bullets = (exp.bullets || []).filter((b) => b.trim());
                  return (
                    <div key={i}>
                      <div style={{ fontWeight: 700, fontSize: 13 }}>{exp.role}</div>
                      <div style={{ fontSize: 11.5, opacity: 0.75, marginBottom: 5 }}>
                        {exp.company}{exp.company && exp.period ? ' · ' : ''}{exp.period}
                      </div>
                      {bullets.length > 0 && (
                        <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 3 }}>
                          {bullets.map((b, bi) => (
                            <li key={bi} style={{ fontSize: 11.5, lineHeight: 1.5 }}>{b}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}

export default NavySidebarEngineer;
