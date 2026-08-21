/**
 * Adaptador de storage para zustand/persist que criptografa o valor (AES-GCM) antes de gravar em
 * localStorage.
 *
 * Modelo de ameaça: localStorage persiste em disco indefinidamente e é legível por qualquer
 * extensão de navegador ou processo com acesso ao perfil do usuário, mesmo sem executar JS na
 * página (diferente de um ataque XSS, que também roubaria a própria chave). A chave AES-256 fica em
 * sessionStorage — sobrevive a reloads da mesma aba, mas é descartada ao fechar o navegador — então
 * o blob que resta em disco depois que o usuário fecha a aba fica ilegível sem a chave da sessão
 * anterior. Não protege contra XSS ativo durante a sessão (isso exigiria nunca manter dado sensível
 * client-side); reduz a janela de exposição de "para sempre" para "enquanto a aba estiver aberta".
 */

const SESSION_KEY_STORAGE_NAME = 'cvfacil-storage-key';

let cachedKeyPromise = null;

function bufferToBase64(buffer) {
  return btoa(String.fromCharCode(...new Uint8Array(buffer)));
}

function base64ToBuffer(base64) {
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

async function getOrCreateKey() {
  if (cachedKeyPromise) return cachedKeyPromise;
  cachedKeyPromise = (async () => {
    const existing = sessionStorage.getItem(SESSION_KEY_STORAGE_NAME);
    if (existing) {
      try {
        return await crypto.subtle.importKey(
          'raw',
          base64ToBuffer(existing),
          'AES-GCM',
          false,
          ['encrypt', 'decrypt']
        );
      } catch {
        // Chave corrompida — gera uma nova (dados antigos ficam ilegíveis, tratado como ausentes).
      }
    }
    const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, [
      'encrypt',
      'decrypt',
    ]);
    const exported = await crypto.subtle.exportKey('raw', key);
    sessionStorage.setItem(SESSION_KEY_STORAGE_NAME, bufferToBase64(exported));
    return key;
  })();
  return cachedKeyPromise;
}

/** Storage compatível com createJSONStorage() do zustand/persist — ver stores/resumeListStore.js */
export const encryptedLocalStorage = {
  getItem: async (name) => {
    if (typeof window === 'undefined') return null;
    const raw = window.localStorage.getItem(name);
    if (!raw) return null;
    try {
      const { iv, data } = JSON.parse(raw);
      const key = await getOrCreateKey();
      const decrypted = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: base64ToBuffer(iv) },
        key,
        base64ToBuffer(data)
      );
      return new TextDecoder().decode(decrypted);
    } catch {
      // Sessão anterior (chave descartada) ou dado corrompido — trata como storage vazio.
      return null;
    }
  },
  setItem: async (name, value) => {
    if (typeof window === 'undefined') return;
    try {
      const key = await getOrCreateKey();
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const encrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        key,
        new TextEncoder().encode(value)
      );
      window.localStorage.setItem(
        name,
        JSON.stringify({ iv: bufferToBase64(iv), data: bufferToBase64(encrypted) })
      );
    } catch {
      // zustand/persist não aguarda esta promise (fire-and-forget) — uma rejeição aqui
      // vira unhandled rejection em vez de erro tratável pelo chamador. Falha ao gravar não é
      // crítica (o state em memória já está correto; só a persistência entre reloads falha).
    }
  },
  removeItem: (name) => {
    if (typeof window === 'undefined') return;
    window.localStorage.removeItem(name);
  },
};
