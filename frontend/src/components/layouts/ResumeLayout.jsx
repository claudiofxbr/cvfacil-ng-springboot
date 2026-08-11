'use client';

import { SAMPLE_RESUME } from '@/components/layouts/sampleData';

/**
 * ResumeLayout parametrizado por tema.
 * Variantes: 'band' | 'header' | 'minimal' | 'split' | 'badge'
 */
export function ResumeLayout({ theme, variant = 'band', data = SAMPLE_RESUME, scale = 1 }) {
  const style = {
    '--rl-primary':      theme.primary,
    '--rl-secondary':    theme.secondary,
    '--rl-on-primary':   theme.onPrimary,
    '--rl-on-secondary': theme.onSecondary,
  };

  return (
    <article
      className="resume-layout"
      style={{ ...style, transform: `scale(${scale})`, transformOrigin: 'top left' }}
      data-variant={variant}
      data-theme={theme.id}
    >
      <div className="rl-root">
        {variant === 'band'    && <BandVariant    data={data} theme={theme} />}
        {variant === 'header'  && <HeaderVariant  data={data} theme={theme} />}
        {variant === 'minimal' && <MinimalVariant data={data} theme={theme} />}
        {variant === 'split'   && <SplitVariant   data={data} theme={theme} />}
        {variant === 'badge'   && <BadgeVariant   data={data} theme={theme} />}
      </div>

      <style jsx>{`
        .resume-layout { width: 600px; min-height: 800px; font-family: Inter, system-ui, sans-serif; }
        .rl-root {
          width: 600px; min-height: 800px; background: #fff; color: #111827;
          border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.08);
        }
        :global(.resume-layout h2.rl-section) {
          font-size: 12px; font-weight: 700; text-transform: uppercase;
          letter-spacing: .08em; color: var(--rl-primary);
          margin: 16px 0 8px; border-bottom: 2px solid var(--rl-secondary); padding-bottom: 4px;
        }
        :global(.resume-layout .rl-name)     { font-size: 28px; font-weight: 800; letter-spacing: -.01em; }
        :global(.resume-layout .rl-headline) { font-size: 14px; opacity: .8; }
        :global(.resume-layout .rl-item-role){ font-weight: 700; font-size: 14px; }
        :global(.resume-layout .rl-item-meta){ font-size: 12px; color: #4b5563; }
        :global(.resume-layout ul.rl-bullets) {
          margin: 6px 0 0 18px; font-size: 12px; color: #374151; line-height: 1.5;
        }
        :global(.resume-layout .rl-chip) {
          display: inline-block; background: var(--rl-secondary); color: var(--rl-on-secondary);
          padding: 3px 8px; border-radius: 999px; font-size: 11px; margin: 2px 4px 2px 0;
        }

        /* ── Impressão: remove overflow que cortava seções longas ── */
        @media print {
          :global(.resume-layout) {
            width: 100% !important;
            transform: none !important;
            border-radius: 0 !important;
            page-break-inside: avoid;
          }
          :global(.rl-root) {
            overflow: visible !important;
            box-shadow: none !important;
            border-radius: 0 !important;
            width: 100% !important;
            min-height: auto !important;
          }
          :global(.rl-root > *) {
            overflow: visible !important;
          }
          :global(.resume-layout ul.rl-bullets) {
            page-break-inside: avoid;
          }
        }
      `}</style>
    </article>
  );
}

// ── Shared section wrapper ────────────────────────────────────────────────────

function Section({ title, children }) {
  return (
    <section>
      <h2 className="rl-section">{title}</h2>
      {children}
    </section>
  );
}

// ── Data blocks ───────────────────────────────────────────────────────────────

function Experience({ data }) {
  return (
    <Section title="Experiência">
      {data.experience.map((e, idx) => (
        <div key={idx} style={{ marginBottom: 12 }}>
          <div className="rl-item-role">{e.role}</div>
          <div className="rl-item-meta">{e.company}{e.company && e.period ? ' · ' : ''}{e.period}</div>
          <ul className="rl-bullets">
            {(e.bullets || []).filter(b => b.trim()).map((b, i) => <li key={i}>{b}</li>)}
          </ul>
        </div>
      ))}
    </Section>
  );
}

function Education({ data }) {
  return (
    <Section title="Formação">
      {data.education.map((ed, idx) => (
        <div key={idx} style={{ marginBottom: 8 }}>
          <div className="rl-item-role">{ed.degree}</div>
          <div className="rl-item-meta">{ed.school}{ed.school && ed.period ? ' · ' : ''}{ed.period}</div>
        </div>
      ))}
    </Section>
  );
}

/** Habilidades com barra de progresso */
function Skills({ data, barColor = 'var(--rl-primary)', trackColor = '#e5e7eb' }) {
  const skills = data.skills || [];
  if (!skills.length) return null;
  return (
    <Section title="Habilidades">
      <div>
        {skills.map((s, i) => {
          const name = typeof s === 'string' ? s : s.name;
          const pct  = typeof s === 'string' ? null : s.pct;
          return (
            <div key={i} style={{ marginBottom: 7 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, marginBottom: 3 }}>
                <span>{name}</span>
                {pct != null && <span style={{ color: barColor, fontWeight: 700 }}>{pct}%</span>}
              </div>
              {pct != null && (
                <div style={{ background: trackColor, borderRadius: 4, height: 5 }}>
                  <div style={{ background: barColor, width: `${pct}%`, height: 5, borderRadius: 4 }} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </Section>
  );
}

/** Habilidades como chips (variantes compactas) */
function SkillChips({ data }) {
  const skills = data.skills || [];
  if (!skills.length) return null;
  return (
    <Section title="Habilidades">
      <div>
        {skills.map((s, i) => {
          const name = typeof s === 'string' ? s : s.name;
          const pct  = typeof s === 'string' ? null : s.pct;
          return (
            <span className="rl-chip" key={i}>
              {name}{pct != null ? ` ${pct}%` : ''}
            </span>
          );
        })}
      </div>
    </Section>
  );
}

function Languages({ data }) {
  return (
    <Section title="Idiomas">
      <ul style={{ fontSize: 12, color: '#374151', margin: 0, padding: 0, listStyle: 'none' }}>
        {data.languages.map((l, i) => (
          <li key={i} style={{ marginBottom: 4 }}>
            {l.name}{l.name && l.level ? ': ' : ''}<b>{l.level}</b>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function Hobbies({ data }) {
  const hobbies = data.hobbies || [];
  if (!hobbies.length) return null;
  return (
    <Section title="Hobbies & Interesses">
      <div>{hobbies.map((h, i) => <span className="rl-chip" key={i}>{h}</span>)}</div>
    </Section>
  );
}

function ContactLine({ data }) {
  return (
    <div style={{ fontSize: 12, opacity: .9, lineHeight: 1.7 }}>
      {data.email    && <div>{data.email}</div>}
      {data.phone    && <div>{data.phone}</div>}
      {data.location && <div>{data.location}</div>}
      {data.website  && <div>{data.website}</div>}
    </div>
  );
}

function PhotoBlock({ src, size = 100, radius = 8, border = 'none' }) {
  if (!src) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt="Foto" style={{
      width: size, height: Math.round(size * 4 / 3),
      objectFit: 'cover', borderRadius: radius, border,
      display: 'block', marginBottom: 10,
    }} />
  );
}

// ── Variants ──────────────────────────────────────────────────────────────────

function BandVariant({ data, theme }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '200px 1fr', minHeight: 800 }}>
      <aside style={{ background: theme.primary, color: theme.onPrimary, padding: 20 }}>
        <PhotoBlock src={data.photo} size={90} radius={6} border={`2px solid ${theme.onPrimary}`} />
        <div className="rl-name" style={{ color: theme.onPrimary, fontSize: 20 }}>{data.fullName}</div>
        <div className="rl-headline" style={{ color: theme.onPrimary, opacity: .9, marginBottom: 14 }}>{data.headline}</div>
        <ContactLine data={data} />
        <div style={{ marginTop: 14 }}>
          <h2 className="rl-section" style={{ color: theme.onPrimary, borderBottomColor: theme.onPrimary, opacity: .9 }}>Idiomas</h2>
          <ul style={{ fontSize: 12, color: theme.onPrimary, margin: 0, padding: 0, listStyle: 'none', opacity: .95 }}>
            {data.languages.map((l, i) => (
              <li key={i} style={{ marginBottom: 4 }}>{l.name}{l.level ? ': ' : ''}<b>{l.level}</b></li>
            ))}
          </ul>
        </div>
      </aside>
      <main style={{ padding: 20 }}>
        <div style={{ fontSize: 12, color: '#4b5563', marginBottom: 8 }}>{data.summary}</div>
        <Experience data={data} />
        <Skills data={data} />
        <Education data={data} />
        <Hobbies data={data} />
      </main>
    </div>
  );
}

function HeaderVariant({ data, theme }) {
  return (
    <>
      <header style={{ background: theme.primary, color: theme.onPrimary, padding: '18px 22px', display: 'flex', alignItems: 'flex-start', gap: 14 }}>
        <PhotoBlock src={data.photo} size={64} radius={6} border={`2px solid ${theme.onPrimary}`} />
        <div>
          <div className="rl-name" style={{ color: theme.onPrimary }}>{data.fullName}</div>
          <div className="rl-headline" style={{ color: theme.onPrimary }}>{data.headline}</div>
          <div style={{ marginTop: 6, fontSize: 12, opacity: .95 }}>
            {[data.email, data.phone, data.location].filter(Boolean).join(' · ')}
          </div>
        </div>
      </header>
      <div style={{ padding: '18px 22px' }}>
        <Section title="Resumo"><p style={{ fontSize: 12, color: '#374151' }}>{data.summary}</p></Section>
        <Experience data={data} />
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <SkillChips data={data} />
          <Education data={data} />
        </div>
        <Languages data={data} />
        <Hobbies data={data} />
      </div>
    </>
  );
}

function MinimalVariant({ data, theme }) {
  return (
    <div style={{ padding: 28 }}>
      <div style={{ borderBottom: `3px solid ${theme.primary}`, paddingBottom: 10, marginBottom: 14, display: 'flex', alignItems: 'flex-start', gap: 14 }}>
        <PhotoBlock src={data.photo} size={64} radius={6} border={`2px solid ${theme.secondary}`} />
        <div>
          <div className="rl-name" style={{ color: theme.primary }}>{data.fullName}</div>
          <div className="rl-headline">{data.headline}</div>
          <div style={{ marginTop: 4, fontSize: 12, color: '#4b5563' }}>
            {[data.email, data.phone, data.location].filter(Boolean).join(' · ')}
          </div>
        </div>
      </div>
      <div style={{ fontSize: 12, color: '#374151', marginBottom: 6 }}>{data.summary}</div>
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20 }}>
        <div><Experience data={data} /><Education data={data} /></div>
        <div><Skills data={data} /><Languages data={data} /><Hobbies data={data} /></div>
      </div>
    </div>
  );
}

function SplitVariant({ data, theme }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr', minHeight: 800 }}>
      <aside style={{ background: theme.secondary, color: theme.onSecondary, padding: 20 }}>
        <PhotoBlock src={data.photo} size={90} radius={6} border={`2px solid ${theme.primary}`} />
        <div className="rl-name" style={{ color: theme.onSecondary, fontSize: 20 }}>{data.fullName}</div>
        <div className="rl-headline" style={{ color: theme.onSecondary }}>{data.headline}</div>
        <div style={{ marginTop: 12 }}><ContactLine data={data} /></div>
        <div style={{ marginTop: 12 }}>
          <h2 className="rl-section" style={{ color: theme.primary, borderBottomColor: theme.primary }}>Habilidades</h2>
          {(data.skills || []).map((s, i) => {
            const name = typeof s === 'string' ? s : s.name;
            const pct  = typeof s === 'string' ? null : s.pct;
            return (
              <div key={i} style={{ marginBottom: 7 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: theme.onSecondary, marginBottom: 2 }}>
                  <span>{name}</span>
                  {pct != null && <span style={{ fontWeight: 700 }}>{pct}%</span>}
                </div>
                {pct != null && (
                  <div style={{ background: 'rgba(0,0,0,.15)', borderRadius: 4, height: 4 }}>
                    <div style={{ background: theme.primary, width: `${pct}%`, height: 4, borderRadius: 4 }} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
        {(data.hobbies || []).length > 0 && (
          <div style={{ marginTop: 12 }}>
            <h2 className="rl-section" style={{ color: theme.primary, borderBottomColor: theme.primary }}>Hobbies</h2>
            {data.hobbies.map((h, i) => <div key={i} style={{ fontSize: 11, color: theme.onSecondary, marginBottom: 3 }}>• {h}</div>)}
          </div>
        )}
      </aside>
      <main style={{ padding: 20 }}>
        <Section title="Resumo"><p style={{ fontSize: 12, color: '#374151' }}>{data.summary}</p></Section>
        <Experience data={data} />
        <Education data={data} />
        <Languages data={data} />
      </main>
    </div>
  );
}

function BadgeVariant({ data, theme }) {
  const initials = data.fullName.split(' ').map(n => n[0]).filter(Boolean).slice(0, 2).join('');
  return (
    <div style={{ padding: 22 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 16 }}>
        {data.photo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={data.photo} alt="Foto" style={{ width: 70, height: 70, borderRadius: 12, objectFit: 'cover', flexShrink: 0 }} />
        ) : (
          <div aria-hidden style={{
            width: 70, height: 70, borderRadius: 12, flexShrink: 0,
            background: theme.primary, color: theme.onPrimary,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 800, fontSize: 24, letterSpacing: 1,
          }}>{initials}</div>
        )}
        <div>
          <div className="rl-name" style={{ color: theme.primary }}>{data.fullName}</div>
          <div className="rl-headline">{data.headline}</div>
          <div style={{ marginTop: 4, fontSize: 12, color: '#4b5563' }}>
            {[data.email, data.phone, data.location].filter(Boolean).join(' · ')}
          </div>
        </div>
      </div>
      <Section title="Resumo"><p style={{ fontSize: 12, color: '#374151' }}>{data.summary}</p></Section>
      <Experience data={data} />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Education data={data} />
        <Languages data={data} />
      </div>
      <SkillChips data={data} />
      <Hobbies data={data} />
    </div>
  );
}
