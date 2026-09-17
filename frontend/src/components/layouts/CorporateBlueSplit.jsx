'use client';

import { User, GraduationCap, Briefcase, Trophy, Phone, Mail } from 'lucide-react';

/**
 * Template "Corporativo Azul Split" — portado de
 * cvfacil.ng-test-copy-20260916-194157/frontend/src/components/resume/templates/CorporateBlueSplit.js
 *
 * Adaptação de dados: o componente de origem consumia o formato do outro
 * repositório (`resume.profession`, `resume.photoUrl` + `getPhotoUrl()`,
 * `resume.educations[].{fieldOfStudy,institution,startDate,endDate}`,
 * `resume.experiences[].description`). Este port usa o formato real deste
 * app (ver `dashboard/editor/page.jsx`): `data.headline`, `data.photo`
 * (já é uma data URL, sem indireção), `data.education[].{school,period}`,
 * `data.experience[].bullets[]` (lista, não parágrafo único).
 */
export function CorporateBlueSplit({ theme, data }) {
  const primary = theme.primary;
  const secondary = theme.secondary;
  const onPrimary = theme.onPrimary;
  const lightGray = '#e8e8e8';
  const skills = data.skills || [];

  return (
    <div style={{ fontFamily: "'Inter', sans-serif", color: '#333', background: '#fff', minHeight: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <div style={{ display: 'flex', minHeight: 220 }}>
        <div style={{ flex: '0 0 38%', background: '#c8c8c8', overflow: 'hidden' }}>
          {data.photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={data.photo} alt="Foto" style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', minHeight: 220, background: '#c8c8c8' }} />
          )}
        </div>
        <div style={{
          flex: '0 0 62%', background: `linear-gradient(135deg, ${primary} 0%, ${secondary} 100%)`,
          position: 'relative', overflow: 'hidden', padding: '32px', display: 'flex',
          flexDirection: 'column', justifyContent: 'center',
        }}>
          <svg style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', opacity: 0.15 }} viewBox="0 0 400 260" preserveAspectRatio="none">
            <polyline points="0,200 40,180 70,210 110,150 150,170 190,120 230,140 270,90 310,110 350,60 400,80" fill="none" stroke="#ffffff" strokeWidth="3" />
            {[40, 70, 110, 150, 190, 230, 270, 310, 350].map((x, i) => (
              <rect key={i} x={x - 4} y={130 + (i % 3) * 20} width="8" height="30" fill="#ffffff" />
            ))}
            <path d="M20,40 Q60,10 100,40 T180,40 T260,30 T340,45 T400,30" fill="none" stroke="#ffffff" strokeWidth="1.5" />
          </svg>
          <h1 style={{ position: 'relative', fontSize: 34, fontWeight: 800, color: onPrimary, margin: 0, lineHeight: 1.15, wordBreak: 'break-word' }}>
            {data.fullName || 'Seu Nome'}
          </h1>
          <p style={{ position: 'relative', fontSize: 16, fontWeight: 600, color: onPrimary, marginTop: 8 }}>
            {data.headline || ''}
          </p>
        </div>
      </div>

      {/* Body */}
      <div style={{ display: 'flex', flex: 1 }}>
        <div style={{ flex: '0 0 50%', background: '#ffffff', padding: '28px' }}>
          {data.summary && (
            <section style={{ marginBottom: 24 }}>
              <PillHeader icon={<User size={16} />} label="Quem sou" bg={secondary} />
              <p style={{ marginTop: 14, color: '#333', fontSize: 12, lineHeight: 1.6 }}>{data.summary}</p>
            </section>
          )}

          {data.education?.length > 0 && (
            <section>
              <PillHeader icon={<GraduationCap size={16} />} label="Educação" bg={secondary} />
              <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 12 }}>
                {data.education.map((edu, i) => (
                  <div key={i} style={{ fontSize: 12, color: '#333' }}>
                    <div style={{ fontWeight: 700 }}>{edu.degree}</div>
                    <div>{edu.school}</div>
                    <div style={{ fontWeight: 700 }}>{edu.period}</div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>

        <div style={{ flex: '0 0 50%', background: lightGray, padding: '28px' }}>
          {data.experience?.length > 0 && (
            <section style={{ marginBottom: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#333', fontWeight: 700, fontSize: 14 }}>
                <Briefcase size={16} />
                <span>Experiência</span>
              </div>
              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 14 }}>
                {data.experience.map((exp, i) => {
                  const bullets = (exp.bullets || []).filter((b) => b.trim());
                  return (
                    <div key={i} style={{ fontSize: 12 }}>
                      <div style={{ fontWeight: 700, color: '#333' }}>{exp.period}</div>
                      <div style={{ fontWeight: 700, color: '#333' }}>{exp.role}{exp.role && exp.company ? ' — ' : ''}{exp.company}</div>
                      {bullets.length > 0 && (
                        <ul style={{ margin: '4px 0 0 16px', padding: 0, color: '#444', lineHeight: 1.5 }}>
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
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#333', fontWeight: 700, fontSize: 14 }}>
                <Trophy size={16} />
                <span>Habilidades</span>
              </div>
              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {skills.map((s, i) => (
                  <div key={i} style={{ fontSize: 12, color: '#333' }}>{typeof s === 'string' ? s : s.name}</div>
                ))}
              </div>
            </section>
          )}
        </div>
      </div>

      {/* Footer */}
      <div style={{ background: secondary, color: onPrimary, padding: '14px 28px', display: 'flex', alignItems: 'center', gap: 24, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, fontSize: 13 }}>
          <Phone size={15} /> Contato
        </div>
        {data.phone && <span style={{ fontSize: 12 }}>{data.phone}</span>}
        {data.email && (
          <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
            <Mail size={13} /> {data.email}
          </span>
        )}
        {data.location && <span style={{ fontSize: 12 }}>{data.location}</span>}
      </div>
    </div>
  );
}

function PillHeader({ icon, label, bg }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 8, background: bg, color: '#fff',
      fontWeight: 700, borderRadius: 50, height: 38, padding: '0 18px', width: 'fit-content', fontSize: 13,
    }}>
      {icon}
      <span>{label}</span>
    </div>
  );
}

export default CorporateBlueSplit;
