export interface Resource { id: string; name: string; description?: string; location?: string }
export interface Booking { id: string; startsAt: string; endsAt: string; status: 'CONFIRMED' | 'CANCELLED' }
export interface Page<T> { items: T[]; totalElements: number; page: number; size: number }
export interface Interval { startsAt: string; endsAt: string }
export class ApiError extends Error {
  constructor(message: string, public status = 0) { super(message); }
}
export async function api<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch('/api/v1' + path, {
      method, signal, headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error;
    throw new ApiError(method === 'GET' ? 'Не удалось связаться с сервером. Проверьте, запущен ли Java backend.' : 'Результат запроса неизвестен: ответ сервера не получен. Перед повтором проверьте обновлённый список — операция могла выполниться.');
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const messages: Record<string, string> = {
      BOOKING_OVERLAP: 'Это время уже занято. Выберите другой интервал.',
      BOOKING_CANNOT_BE_CANCELLED: 'Начавшуюся бронь отменить нельзя.',
      RESOURCE_NOT_FOUND: 'Ресурс не найден. Обновите список ресурсов.',
      BOOKING_NOT_FOUND: 'Бронь не найдена. Обновите список.',
      TEMPORARILY_UNAVAILABLE: 'Сервер временно недоступен. Попробуйте позднее.',
      VALIDATION_FAILED: 'Проверьте заполненные поля.',
    };
    const message = messages[problem.code] ?? (response.status === 400
      ? 'Проверьте даты: длительность от минуты до 24 часов, начало в будущем, конец в пределах 365 дней.'
      : response.status >= 500 ? 'Сервер недоступен или произошла ошибка. Проверьте backend и обновите данные.'
      : 'Не удалось выполнить запрос.');
    throw new ApiError(message + (problem.requestId ? ' Код запроса: ' + problem.requestId : ''), response.status);
  }
  return response.json();
}
export const errorText = (error: unknown) => error instanceof Error ? error.message : 'Неизвестная ошибка.';
export function localDate(date = new Date()) {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
}
export function dayWindow(day: string) {
  const start = new Date(day + 'T00:00:00');
  const end = new Date(start); end.setDate(end.getDate() + 1);
  return new URLSearchParams({ from: start.toISOString(), to: end.toISOString() }).toString();
}
export function bookingBody(start: string, end: string) {
  const a = new Date(start), b = new Date(end);
  if (!start || !end || !Number.isFinite(+a) || !Number.isFinite(+b)) throw new Error('Укажите начало и конец брони.');
  if (localDate(a) + 'T' + String(a.getHours()).padStart(2, '0') + ':' + String(a.getMinutes()).padStart(2, '0') !== start || localDate(b) + 'T' + String(b.getHours()).padStart(2, '0') + ':' + String(b.getMinutes()).padStart(2, '0') !== end) throw new Error('Выбранное местное время не существует. Проверьте дату и переход на летнее время.');
  if (+b <= +a) throw new Error('Конец должен быть позже начала.');
  if (+a < Date.now()) throw new Error('Начало брони должно быть в будущем.');
  if (+b - +a < 60000 || +b - +a > 86400000) throw new Error('Длительность брони — от 1 минуты до 24 часов.');
  if (+b > Date.now() + 365 * 86400000) throw new Error('Конец брони должен быть в пределах 365 дней.');
  return { startsAt: a.toISOString(), endsAt: b.toISOString() };
}
export function displayTime(value: string) {
  return new Intl.DateTimeFormat('ru-RU', { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit' }).format(new Date(value));
}


