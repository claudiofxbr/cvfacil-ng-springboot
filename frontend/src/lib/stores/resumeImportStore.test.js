import { useResumeImportStore } from './resumeImportStore';

describe('resumeImportStore', () => {
  beforeEach(() => {
    sessionStorage.clear();
    useResumeImportStore.setState({ importedData: null });
  });

  it('persiste importedData em sessionStorage', async () => {
    const data = { fullName: 'Claudio Freitas Xavier' };
    useResumeImportStore.getState().setImportedData(data);

    // zustand/persist grava de forma assíncrona mesmo com storage síncrono (createJSONStorage
    // envolve setItem em Promise) — dar um tick pro flush terminar antes de checar o storage.
    await Promise.resolve();
    await Promise.resolve();

    const raw = sessionStorage.getItem('cvfacil-resume-import');
    expect(raw).toBeTruthy();
    expect(JSON.parse(raw).state.importedData).toEqual(data);
  });

  it('clearImportedData remove o dado do estado e do sessionStorage', async () => {
    useResumeImportStore.getState().setImportedData({ fullName: 'X' });
    await Promise.resolve();
    await Promise.resolve();

    useResumeImportStore.getState().clearImportedData();
    await Promise.resolve();
    await Promise.resolve();

    expect(useResumeImportStore.getState().importedData).toBeNull();
    const raw = sessionStorage.getItem('cvfacil-resume-import');
    expect(JSON.parse(raw).state.importedData).toBeNull();
  });

  it('recupera importedData numa instância de store recriada (regressão: reload/HMR entre dashboard e editor não perde a importação)', async () => {
    useResumeImportStore.getState().setImportedData({ fullName: 'Claudio Freitas Xavier' });
    await Promise.resolve();
    await Promise.resolve();

    // Simula o que acontece em dev quando o Next.js recompila a rota do editor bem no momento
    // do redirect pós-importação: o módulo JS (e o state em memória de um store não-persistido)
    // é reavaliado do zero. Um store só-em-memória perderia importedData aqui.
    jest.resetModules();
    // eslint-disable-next-line global-require
    const { useResumeImportStore: reloadedStore } = require('./resumeImportStore');
    await reloadedStore.persist.rehydrate();

    expect(reloadedStore.getState().importedData).toEqual({ fullName: 'Claudio Freitas Xavier' });
  });
});
