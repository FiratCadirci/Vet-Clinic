import { beforeEach, describe, expect, it, vi } from 'vitest';

const post = vi.fn();
vi.mock('../../lib/api', async (importActual) => ({
  ...(await importActual<typeof import('../../lib/api')>()),
  api: { post: (...args: unknown[]) => post(...args) },
}));
vi.mock('../../lib/auth', () => ({ useAuth: () => ({ can: () => true }) }));

const { ApiError } = await import('../../lib/api');
const { generateTemplateDraft } = await import('./vaccineApi');

describe('generateTemplateDraft', () => {
  beforeEach(() => { post.mockReset(); });

  it('hazır takvimi olan türde taslak üretir', async () => {
    post.mockResolvedValue([{ id: 'p1' }]);
    await expect(generateTemplateDraft('dog')).resolves.toEqual({ empty: false });
    expect(post).toHaveBeenCalledWith('/v1/vaccine-protocols/generate', { speciesId: 'dog' });
  });

  it('hazır takvimi olmayan türde (422) boş şablon açar', async () => {
    post.mockImplementation(() => Promise.reject(new ApiError(422, 'Bu tür için hazır aşı takvimi yok; kalemleri elle ekleyin.', null)));
    await expect(generateTemplateDraft('other')).resolves.toEqual({ empty: true });
  });

  it('diğer hataları yukarı iletir', async () => {
    post.mockImplementation(() => Promise.reject(new ApiError(403, 'Yetkiniz yok', null)));
    await expect(generateTemplateDraft('other')).rejects.toThrow('Yetkiniz yok');
  });
});
