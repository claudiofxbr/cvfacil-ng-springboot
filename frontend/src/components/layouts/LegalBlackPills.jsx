'use client';

/**
 * Template "Jurídico Black Pills" — portado de
 * cvfacil.ng-test-copy-20260916-194157/frontend/src/components/resume/templates/LegalBlackPills.js
 *
 * Adaptação de dados: `data.photo` direto, `data.education[].{school,period}`
 * (já vem como uma string única "2018 — 2022", não startDate/endDate
 * separados), `data.experience[].bullets[]` no lugar de `description`,
 * `data.languages[].level` (string livre, ex. "Fluente") em vez de um
 * campo `percentage` numérico — o mapa nível→% abaixo cobre os valores do
 * seletor do editor (`Nativo/Fluente/Avançado/Intermediário/Básico`).
 */
const LEVEL_PCT = {
  nativo: 100,
  fluente: 90,
  avancado: 75,
  intermediario: 55,
  basico: 30,
};

function levelToPct(level) {
  const key = (level || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
  return LEVEL_PCT[key] ?? 60;
}

export function LegalBlackPills({ theme, data }) {
  const black = theme.primary;
  const nameParts = (data.fullName || 'Seu Nome').trim().split(' ');
  const skills = data.skills || [];

  return (
    <div style={{ fontFamily: "'Inter', sans-serif", color: '#111', background: '#ffffff', minHeight: '100%', padding: '32px' }}>
      {/* Top row */}
      <div style={{ display: 'flex', gap: 20, marginBottom: 24 }}>
        <div style={{ flex: '0 0 130px', height: 130, overflow: 'hidden' }}>
          {data.photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={data.photo} alt="Foto" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', background: '#ddd' }} />
          )}
        </div>
        <div style={{
          flex: 1, background: black, color: '#fff', borderRadius: 20, borderBottomLeftRadius: 4,
          display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', padding: 16,
        }}>
          <div>
            {nameParts.map((part, i) => (
              <div key={i} style={{ fontSize: '1.3rem', fontWeight: 800, lineHeight: 1.2, wordBreak: 'break-word' }}>{part}</div>
            ))}
          </div>
        </div>
      </div>

      {data.summary && (
        <p style={{ fontSize: 12, lineHeight: 1.7, margin: '0 0 24px 0', color: '#111' }}>
          <strong>Perfil |</strong> {data.summary}
        </p>
      )}

      {/* Body */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.6fr', gap: 20 }}>
        {/* Left column - black cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>
          <div style={{ background: black, color: '#fff', borderRadius: 20, padding: 16 }}>
            <h3 style={{ margin: '0 0 10px 0', fontSize: 13, fontWeight: 800 }}>Contato</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 11, wordBreak: 'break-word' }}>
              {data.phone && <div><strong>Celular</strong> {data.phone}</div>}
              {data.email && <div><strong>E-mail</strong> {data.email}</div>}
              {data.location && <div><strong>Endereço</strong> {data.location}</div>}
            </div>
          </div>

          {data.languages?.length > 0 && (
            <div style={{ background: black, color: '#fff', borderRadius: 20, padding: 16 }}>
              <h3 style={{ margin: '0 0 10px 0', fontSize: 13, fontWeight: 800 }}>Idiomas</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {data.languages.map((lang, i) => {
                  const pct = levelToPct(lang.level);
                  return (
                    <div key={i}>
                      <div style={{ fontSize: 10.5, marginBottom: 4 }}>{lang.name}{lang.level ? ` — ${lang.level}` : ''}</div>
                      <div style={{ height: 7, background: 'rgba(255,255,255,0.25)', borderRadius: 10, overflow: 'hidden' }}>
                        <div style={{ width: `${pct}%`, height: '100%', background: '#fff', borderRadius: 10 }} />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {skills.length > 0 && (
            <div style={{ background: black, color: '#fff', borderRadius: 20, padding: 16 }}>
              <h3 style={{ margin: '0 0 10px 0', fontSize: 13, fontWeight: 800 }}>Competências</h3>
              <ul style={{ margin: 0, paddingLeft: 16, display: 'flex', flexDirection: 'column', gap: 5 }}>
                {skills.map((s, i) => (
                  <li key={i} style={{ fontSize: 11 }}>{typeof s === 'string' ? s : s.name}</li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Right column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20, minWidth: 0 }}>
          {data.education?.length > 0 && (
            <section>
              <span style={{ background: black, color: '#fff', fontWeight: 700, fontSize: 11, padding: '6px 16px', borderRadius: 20, display: 'inline-block' }}>Formação</span>
              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
                {data.education.map((edu, i) => (
                  <div key={i} style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', gap: 8 }}>
                    <div style={{ flex: '1 1 180px', minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 12, color: '#111' }}>{edu.degree}</div>
                      <div style={{ fontSize: 11, color: '#333' }}>{edu.school}</div>
                    </div>
                    {edu.period && (
                      <span style={{ background: black, color: '#fff', fontWeight: 700, fontSize: 10, padding: '3px 8px', borderRadius: 12, whiteSpace: 'nowrap', marginLeft: 'auto' }}>
                        {edu.period}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </section>
          )}

          {data.experience?.length > 0 && (
            <section>
              <span style={{ background: black, color: '#fff', fontWeight: 700, fontSize: 11, padding: '6px 16px', borderRadius: 20, display: 'inline-block' }}>Experiência</span>
              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
                {data.experience.map((exp, i) => {
                  const bullets = (exp.bullets || []).filter((b) => b.trim());
                  return (
                    <div key={i}>
                      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', gap: 8 }}>
                        <div style={{ flex: '1 1 180px', minWidth: 0 }}>
                          <div style={{ fontWeight: 700, fontSize: 12, color: '#111' }}>{exp.role}</div>
                          <div style={{ fontSize: 11, color: '#333' }}>{exp.company}</div>
                        </div>
                        {exp.period && (
                          <span style={{ background: black, color: '#fff', fontWeight: 700, fontSize: 10, padding: '3px 8px', borderRadius: 12, whiteSpace: 'nowrap', marginLeft: 'auto' }}>
                            {exp.period}
                          </span>
                        )}
                      </div>
                      {bullets.length > 0 && (
                        <ul style={{ margin: '6px 0 0 16px', padding: 0, fontSize: 11, color: '#333', lineHeight: 1.5 }}>
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
    </div>
  );
}

export default LegalBlackPills;
