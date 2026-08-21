import '@testing-library/jest-dom';

// jsdom nao implementa crypto.subtle (Web Crypto) — resumeListStore usa
// crypto.subtle.* para cifrar os dados persistidos em localStorage
// (lib/secureStorage.js); sem isso, qualquer teste que grave estado nesse
// store falha com "Cannot read properties of undefined (reading 'generateKey')".
if (typeof globalThis.crypto?.subtle === 'undefined') {
  const { webcrypto } = require('node:crypto');
  if (globalThis.crypto) {
    globalThis.crypto.subtle = webcrypto.subtle;
  } else {
    globalThis.crypto = webcrypto;
  }
}
