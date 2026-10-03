import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, afterEach, expect, test, vi } from 'vitest';
import App from './App';

const resource = { id: 'room-1', name: 'Переговорная А', description: 'Для встреч', location: 'Этаж 2' };
let bookings: any[];
let overlap: boolean;
let calls: {url: string; method: string; body: any}[];
const page = (items: any[]) => ({items, page: 0, size: 20, totalElements: items.length});
beforeEach(() => {
  bookings = []; overlap = false; calls = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    calls.push({url, method, body});
    if (url.includes('/cancel')) { bookings[0].status = 'CANCELLED'; return Response.json(bookings[0]); }
    if (url.includes('/availability')) return Response.json({intervals: [{startsAt: '2030-01-01T09:00:00Z', endsAt: '2030-01-01T12:00:00Z'}]});
    if (url.includes('/bookings') && method === 'POST') {
      if (overlap) return Response.json({code: 'BOOKING_OVERLAP', requestId: 'test-id'}, {status: 409});
      bookings.push({id: 'booking-1', resourceId: resource.id, ...body, status: 'CONFIRMED'});
      return Response.json(bookings[0], {status: 201});
    }
    if (url.includes('/bookings')) return Response.json(page(bookings));
    if (method === 'POST') return Response.json({...resource, ...body}, {status: 201});
    return Response.json(page([resource]));
  }));
});
afterEach(() => vi.unstubAllGlobals());
async function selectResource() {
  render(<App/>);
  await userEvent.click(await screen.findByRole('button', {name: /Переговорная А/}));
}
function fillBooking() {
  const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1);
  const date = [tomorrow.getFullYear(), String(tomorrow.getMonth()+1).padStart(2,'0'), String(tomorrow.getDate()).padStart(2,'0')].join('-');
  fireEvent.change(screen.getByLabelText('Начало брони'), {target:{value:date+'T10:00'}});
  fireEvent.change(screen.getByLabelText('Конец брони'), {target:{value:date+'T11:00'}});
}
test('creates a booking in UTC, shows it and cancels it', async () => {
  await selectResource(); fillBooking();
  await userEvent.click(screen.getByRole('button', {name:'Забронировать'}));
  expect(await screen.findByText('Бронь создана.')).toBeVisible();
  const sent = calls.find(c => c.method === 'POST' && c.url.endsWith('/bookings'))!;
  expect(sent.body.startsAt).toMatch(/Z$/);
  expect(new Date(sent.body.endsAt).getTime()-new Date(sent.body.startsAt).getTime()).toBe(3600000);
  await userEvent.click(await screen.findByRole('button', {name:'Отменить бронь'}));
  await userEvent.click(screen.getByRole('button', {name:'Да, отменить'}));
  expect(await screen.findByText('Бронь отменена.')).toBeVisible();
  expect(await screen.findByText('Отменена')).toBeVisible();
});
test('explains overlap and refreshes availability after rejection', async () => {
  overlap = true; await selectResource(); fillBooking();
  await waitFor(() => expect(calls.some(c=>c.url.includes('/availability'))).toBe(true));
  const before = calls.filter(c=>c.url.includes('/availability')).length;
  await userEvent.click(screen.getByRole('button', {name:'Забронировать'}));
  expect(await screen.findByText(/Это время уже занято/)).toBeVisible();
  await waitFor(() => expect(calls.filter(c=>c.url.includes('/availability')).length).toBeGreaterThan(before));
  expect(screen.queryByText('Бронь создана.')).not.toBeInTheDocument();
});
test('rejects inverted interval without sending it to API', async () => {
  await selectResource(); fillBooking();
  const start = (screen.getByLabelText('Начало брони') as HTMLInputElement).value;
  fireEvent.change(screen.getByLabelText('Конец брони'), {target:{value:start}});
  await userEvent.click(screen.getByRole('button', {name:'Забронировать'}));
  expect(await screen.findByText(/Конец должен быть позже начала/)).toBeVisible();
  expect(calls.filter(c=>c.method==='POST')).toHaveLength(0);
});
test('shows recoverable backend connection error', async () => {
  vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
  render(<App/>);
  expect(await screen.findByText(/Не удалось связаться с сервером/)).toBeVisible();
  expect(screen.getByRole('button', {name:'Обновить ресурсы'})).toBeEnabled();
});


test('resource POST with lost response warns about uncertain result and refreshes list', async () => {
  render(<App/>);
  await screen.findByRole('button', {name:/Переговорная А/});
  const before=calls.filter(c=>c.method==='GET' && !c.url.includes('/bookings')).length;
  await userEvent.click(screen.getByText('＋ Новый ресурс'));
  await userEvent.type(screen.getByLabelText('Название'), 'Lost response room');
  vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));
  await userEvent.click(screen.getByRole('button',{name:'Создать ресурс'}));
  expect(await screen.findByText(/Результат запроса неизвестен/)).toBeVisible();
  await waitFor(()=>expect(calls.filter(c=>c.method==='GET').length).toBeGreaterThan(before));
});
