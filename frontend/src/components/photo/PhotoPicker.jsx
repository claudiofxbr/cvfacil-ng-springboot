'use client';

import { useRef, useState } from 'react';
import { Camera, Check, RotateCw, Upload } from 'lucide-react';

const FRAMES = [
  { id: 'none', label: 'Sem moldura' },
  { id: 'thin', label: 'Traço fino' },
  { id: 'shadow', label: 'Sombra suave' },
  { id: 'double', label: 'Borda dupla' },
  { id: 'rounded', label: 'Arredondada' },
  { id: 'polaroid', label: 'Polaroid' },
  { id: 'gradient', label: 'Gradiente da paleta' },
  { id: 'geometric', label: 'Geométrica' },
  { id: 'stripe', label: 'Listra diagonal' },
];

const MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

// Redesenha a imagem (com a rotação aplicada) num canvas e reexporta como JPEG.
// O canvas descarta todo metadado do arquivo original (EXIF, incl. geolocalização) porque
// só os pixels são copiados — é isso que torna real a promessa "EXIF é removido" abaixo.
function stripExifAndRotate(dataUrl, rotationDeg) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const swap = rotationDeg % 180 !== 0;
      const canvas = document.createElement('canvas');
      canvas.width = swap ? img.height : img.width;
      canvas.height = swap ? img.width : img.height;
      const ctx = canvas.getContext('2d');
      ctx.translate(canvas.width / 2, canvas.height / 2);
      ctx.rotate((rotationDeg * Math.PI) / 180);
      ctx.drawImage(img, -img.width / 2, -img.height / 2);
      resolve(canvas.toDataURL('image/jpeg', 0.92));
    };
    img.onerror = () => reject(new Error('Falha ao processar a imagem.'));
    img.src = dataUrl;
  });
}

export function PhotoPicker({ primary = '#1F3A5F', secondary = '#D8E3F2', resumes = [], onApply }) {
  const inputRef = useRef(null);
  const [rawPreview, setRawPreview] = useState(null);
  const [rotation, setRotation] = useState(0);
  const [frame, setFrame] = useState('thin');
  const [error, setError] = useState('');
  const [targetResumeId, setTargetResumeId] = useState('');
  const [applying, setApplying] = useState(false);
  const [applied, setApplied] = useState(false);

  const preview = rawPreview;

  function handleFile(file) {
    setError('');
    setApplied(false);
    if (!file) return;
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setError('Formato inválido. Use JPEG, PNG ou WebP.');
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      setError('Arquivo excede 5 MB.');
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => setRawPreview(e.target.result);
    reader.readAsDataURL(file);
  }

  async function handleApply() {
    if (!preview || !targetResumeId || !onApply) return;
    setError('');
    setApplying(true);
    setApplied(false);
    try {
      const finalDataUrl = await stripExifAndRotate(preview, rotation);
      await onApply(targetResumeId, finalDataUrl);
      setApplied(true);
    } catch {
      setError('Não foi possível salvar a foto no currículo. Tente novamente.');
    } finally {
      setApplying(false);
    }
  }

  return (
    <div className="grid gap-6 md:grid-cols-[320px_1fr]">
      <div>
        <h2 className="mb-2 text-lg font-semibold">Foto 3x4 com moldura</h2>
        <p className="mb-4 text-sm text-gray-600">
          A proporção é automaticamente ajustada para 3:4 (documento). Máximo 5 MB. Ao salvar, a
          imagem é reprocessada e os metadados EXIF (como geolocalização) são removidos.
        </p>

        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="btn-primary mb-3 w-full justify-center"
        >
          <Upload size={16} aria-hidden className="mr-2" /> Enviar foto
        </button>
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_TYPES.join(',')}
          className="sr-only"
          onChange={(e) => handleFile(e.target.files?.[0])}
        />

        <button
          type="button"
          onClick={() => setRotation((r) => (r + 90) % 360)}
          disabled={!preview}
          className="btn-secondary mb-4 w-full justify-center disabled:opacity-50"
        >
          <RotateCw size={16} aria-hidden className="mr-2" /> Girar 90°
        </button>

        <label htmlFor="frame-select" className="mb-1 block text-sm font-medium text-gray-700">
          Moldura
        </label>
        <select
          id="frame-select"
          value={frame}
          onChange={(e) => setFrame(e.target.value)}
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
        >
          {FRAMES.map((f) => (
            <option key={f.id} value={f.id}>
              {f.label}
            </option>
          ))}
        </select>

        {preview && onApply && (
          <div className="mt-4 border-t border-gray-200 pt-4">
            <label htmlFor="resume-target" className="mb-1 block text-sm font-medium text-gray-700">
              Salvar no currículo
            </label>
            {resumes.length === 0 ? (
              <p className="text-sm text-gray-500">
                Você ainda não tem currículos salvos — crie um em "Modelos de Currículos" antes de
                aplicar a foto.
              </p>
            ) : (
              <>
                <select
                  id="resume-target"
                  value={targetResumeId}
                  onChange={(e) => { setTargetResumeId(e.target.value); setApplied(false); }}
                  className="mb-3 w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
                >
                  <option value="">Selecione um currículo...</option>
                  {resumes.map((r) => (
                    <option key={r.id} value={r.id}>{r.name}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={handleApply}
                  disabled={!targetResumeId || applying}
                  className="btn-primary w-full justify-center disabled:opacity-50"
                >
                  {applied ? (
                    <><Check size={16} aria-hidden className="mr-2" /> Foto salva</>
                  ) : applying ? (
                    'Salvando...'
                  ) : (
                    'Usar esta foto no currículo'
                  )}
                </button>
              </>
            )}
          </div>
        )}

        {error && (
          <p role="alert" className="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
      </div>

      <div className="flex items-start justify-center p-4">
        <div className="relative">
          <PhotoFrame frame={frame} primary={primary} secondary={secondary}>
            {preview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={preview}
                alt="Pré-visualização"
                className="h-full w-full object-cover"
                style={{ transform: `rotate(${rotation}deg)` }}
              />
            ) : (
              <div className="flex h-full w-full flex-col items-center justify-center bg-gray-50 text-center text-sm text-gray-400">
                <Camera size={40} aria-hidden className="mb-2" />
                Nenhuma foto enviada
              </div>
            )}
          </PhotoFrame>
        </div>
      </div>
    </div>
  );
}

function PhotoFrame({ frame, primary, secondary, children }) {
  const base = { width: 240, height: 320 }; // 3:4 real
  switch (frame) {
    case 'none':
      return <div style={base} className="overflow-hidden">{children}</div>;
    case 'thin':
      return (
        <div
          style={{ ...base, border: `2px solid ${primary}` }}
          className="overflow-hidden"
        >
          {children}
        </div>
      );
    case 'shadow':
      return (
        <div style={{ ...base, boxShadow: '0 10px 25px rgba(0,0,0,0.15)' }} className="overflow-hidden rounded-md">
          {children}
        </div>
      );
    case 'double':
      return (
        <div
          style={{ ...base, border: `3px solid ${primary}`, outline: `2px solid ${secondary}`, outlineOffset: 4 }}
          className="overflow-hidden"
        >
          {children}
        </div>
      );
    case 'rounded':
      return (
        <div style={{ ...base, border: `3px solid ${primary}` }} className="overflow-hidden rounded-[32px]">
          {children}
        </div>
      );
    case 'polaroid':
      return (
        <div style={{ width: 260, paddingBottom: 40 }} className="bg-white p-3 shadow-lg">
          <div style={base} className="overflow-hidden">{children}</div>
        </div>
      );
    case 'gradient':
      return (
        <div
          style={{ ...base, background: `linear-gradient(135deg, ${primary}, ${secondary})`, padding: 4 }}
        >
          <div style={{ width: '100%', height: '100%', background: '#fff' }} className="overflow-hidden">{children}</div>
        </div>
      );
    case 'geometric':
      return (
        <div style={{ ...base, position: 'relative' }} className="overflow-hidden">
          <div style={{ position: 'absolute', inset: 0, border: `4px solid ${primary}`, clipPath: 'polygon(0 0, 100% 0, 100% 90%, 90% 100%, 0 100%)' }} />
          {children}
        </div>
      );
    case 'stripe':
      return (
        <div
          style={{ ...base, background: `repeating-linear-gradient(45deg, ${primary} 0 8px, ${secondary} 8px 16px)`, padding: 6 }}
        >
          <div style={{ width: '100%', height: '100%' }} className="overflow-hidden">{children}</div>
        </div>
      );
    default:
      return <div style={base}>{children}</div>;
  }
}
