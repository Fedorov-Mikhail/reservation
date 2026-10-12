import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {beforeEach,afterEach,test,expect,vi} from 'vitest';
import App from './App';
import SettingsPanel from './SettingsPanel';
import ResourceArchive from './ResourceArchive';
import MyBookings from './MyBookings';
import {localDate,requestKey,clearAttempt} from './api';
const room={id:'r1',name:'Room',location:'Floor 2',archivedAt:null};
const account={id:'u1',username:'mike',role:'USER' as const,enabled:true};
const page=(items:unknown[])=>({items,totalElements:items.length,page:0,size:20});
let requests:{url:string;init?:RequestInit}[], fail:boolean, reject:boolean;
beforeEach(()=>{sessionStorage.clear();requests=[];fail=false;reject=false;vi.stubGlobal('fetch',vi.fn(async(url:string,init?:RequestInit)=>{
 requests.push({url,init});
 if(url.endsWith('/csrf'))return Response.json({token:'test'});
 if(url.endsWith('/permissions'))return Response.json({canManage:false});
 if(url.endsWith('/schedule'))return Response.json({zoneId:'UTC',weekly:{},exceptions:{}});
 if(url.includes('/availability')){const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);const day=localDate(tomorrow);return Response.json({intervals:[{startsAt:new Date(day+'T10:00').toISOString(),endsAt:new Date(day+'T12:00').toISOString()}]});}
 if(url.endsWith('/bookings')&&init?.method==='POST'){if(fail)throw new TypeError('lost response');if(reject)return Response.json({code:'BOOKING_OVERLAP'},{status:409});return Response.json({id:'b1',...JSON.parse(String(init.body)),status:'CONFIRMED'});}
 if(url.includes('/bookings'))return Response.json(page([]));
 return Response.json(page([room]));
}));});
afterEach(()=>vi.unstubAllGlobals());
async function select(){await screen.findByRole('button',{name:/Room/});fireEvent.click(screen.getByRole('button',{name:/Room/}));await screen.findByRole('button',{name:'Забронировать'});}
const posts=()=>requests.filter(r=>r.init?.method==='POST'&&r.url.endsWith('/bookings'));
const key=(r:{init?:RequestInit})=>new Headers(r.init?.headers).get('Idempotency-Key');
test('lost booking response keeps key across remount and never retries automatically',async()=>{
 fail=true;const first=render(<App user={account}/>);await select();fireEvent.click(screen.getByRole('button',{name:'Забронировать'}));await screen.findByText(/Результат запроса неизвестен/);expect(posts()).toHaveLength(1);const original=key(posts()[0]);expect(original).toBeTruthy();first.unmount();render(<App user={account}/>);await select();expect(posts()).toHaveLength(1);fail=false;fireEvent.click(screen.getByRole('button',{name:'Забронировать'}));await screen.findByText('Бронь создана.');expect(key(posts()[1])).toBe(original);expect(sessionStorage.getItem('reservation:attempt:u1')).toBeNull();
});
test('definite rejection starts a new logical booking request',async()=>{
 reject=true;render(<App user={account}/>);await select();fireEvent.click(screen.getByRole('button',{name:'Забронировать'}));await screen.findByText(/Это время уже занято/);const original=key(posts()[0]);reject=false;fireEvent.click(screen.getByRole('button',{name:'Забронировать'}));await screen.findByText('Бронь создана.');expect(key(posts()[1])).not.toBe(original);
});
test('request identity is scoped to account and changes with request parameters',()=>{
 const original=requestKey('u1','r1','bookings','start','end');expect(requestKey('u1','r1','bookings','start','end')).toBe(original);expect(requestKey('u2','r1','bookings','start','end')).not.toBe(original);expect(requestKey('u1','r1','holds','start','end')).not.toBe(original);clearAttempt('u1');expect(sessionStorage.getItem('reservation:attempt:u2')).not.toBeNull();
});
test('week range duration search and free interval selection affect visible booking form',async()=>{
 render(<App user={account}/>);await select();fireEvent.change(screen.getByLabelText('Представление'),{target:{value:'week'}});fireEvent.change(screen.getByLabelText('Свободный интервал, минут'),{target:{value:'90'}});
 await waitFor(()=>expect(requests.some(r=>r.url.includes('minDurationMinutes=90'))).toBe(true));const request=requests.filter(r=>r.url.includes('/availability')).at(-1)!;const query=new URL(request.url,'http://localhost').searchParams;expect(Date.parse(query.get('to')!)-Date.parse(query.get('from')!)).toBe(7*86400000);
 const slot=await screen.findByRole('button',{name:/10:00.*12:00/});fireEvent.click(slot);expect((screen.getByLabelText('Начало брони') as HTMLInputElement).value).toMatch(/T10:00$/);expect((screen.getByLabelText('Конец брони') as HTMLInputElement).value).toMatch(/T11:00$/);
 fireEvent.change(screen.getByLabelText('Поиск ресурсов'),{target:{value:'Floor'}});await waitFor(()=>expect(requests.some(r=>r.url.includes('q=Floor'))).toBe(true));expect(requests.filter(r=>r.url.includes('/resources/r1/bookings')).length).toBeGreaterThan(0);
});
test('archive requires explicit confirmation and explains active bookings conflict',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>url.endsWith('/csrf')?Response.json({token:'x'}):Response.json({code:'RESOURCE_HAS_ACTIVE_BOOKINGS'},{status:409})));const saved=vi.fn();render(<ResourceArchive resource={room} onSaved={saved}/>);fireEvent.click(screen.getByText('Архивировать ресурс'));expect(vi.mocked(fetch)).not.toHaveBeenCalled();fireEvent.click(screen.getByText('Да, архивировать'));expect(await screen.findByRole('alert')).toHaveTextContent('будущие брони');expect(saved).not.toHaveBeenCalled();
});
test('settings clearly show unconfigured providers and save only writable preference fields',async()=>{
 const fetcher=vi.fn(async(url:string,init?:RequestInit)=>{if(url.endsWith('/csrf'))return Response.json({token:'x'});return Response.json(url.includes('notification-preferences')?{email:'mike@example.com',emailEnabled:false,reminderMinutes:30,providerEnabled:false}:{enabled:false,connected:false,status:'DISABLED'});});vi.stubGlobal('fetch',fetcher);render(<SettingsPanel/>);expect(await screen.findByText(/Email-доставка не настроена/)).toBeVisible();expect(await screen.findByText('Интеграция не настроена администратором.')).toBeVisible();fireEvent.click(screen.getByText('Сохранить настройки'));await screen.findByText('Настройки уведомлений сохранены.');const write=fetcher.mock.calls.find(c=>c[1]?.method==='PUT')!;expect(JSON.parse(String(write[1]?.body))).toEqual({email:'mike@example.com',emailEnabled:false,reminderMinutes:30});
});
test('notification target fetches exact authorized booking even outside current page',async()=>{
 const fetcher=vi.fn(async(url:string)=>Response.json(url.includes('/me/bookings')?page([]):url.endsWith('/bookings/b9')?{id:'b9',resourceId:'r1',startsAt:'2099-01-01T10:00:00Z',endsAt:'2099-01-01T11:00:00Z',status:'CANCELLED',cancellationReason:'Maintenance',cancelledBy:'admin'}:room));vi.stubGlobal('fetch',fetcher);render(<MyBookings focusId="b9"/>);expect(await screen.findByText(/Maintenance/)).toBeVisible();expect(document.getElementById('booking-b9')).toHaveClass('focused-booking');expect(fetcher).toHaveBeenCalledWith('/api/v1/bookings/b9',expect.objectContaining({method:'GET'}));
});

import EmailDeliveries from './EmailDeliveries';
test('admin sees failed deliveries and can explicitly return one to queue',async()=>{
 let retried=false;const fetcher=vi.fn(async(url:string,init?:RequestInit)=>{if(url.endsWith('/csrf'))return Response.json({token:'x'});if(init?.method==='POST'){retried=true;return Response.json({queued:true});}return Response.json([{id:'d1',bookingId:'b1',kind:'REMINDER',state:retried?'PENDING':'FAILED',attempts:retried?0:5,lastError:retried?null:'SMTP_FAILED',createdAt:'2030-01-01T10:00:00Z'}]);});vi.stubGlobal('fetch',fetcher);render(<EmailDeliveries/>);fireEvent.click(screen.getByText('Журнал email-доставки'));await screen.findByText('SMTP_FAILED');fireEvent.click(screen.getByText('Повторить отправку'));expect(await screen.findByText('Доставка возвращена в очередь.')).toBeVisible();await screen.findByText(/В очереди/);expect(fetcher).toHaveBeenCalledWith('/api/v1/admin/email-deliveries/d1/retry',expect.objectContaining({method:'POST'}));
});
test('disconnect explains retained external events before sending mutation',async()=>{
 const fetcher=vi.fn(async(url:string,init?:RequestInit)=>{if(url.endsWith('/csrf'))return Response.json({token:'x'});return Response.json(url.includes('notification-preferences')?{email:'',emailEnabled:false,reminderMinutes:30,providerEnabled:false}:{enabled:true,connected:init?.method!=='POST',reconnectRequired:false,status:'CONNECTED',remoteEventsRetained:true});});vi.stubGlobal('fetch',fetcher);render(<SettingsPanel/>);fireEvent.click(await screen.findByText('Отключить календарь'));expect(screen.getByText(/Уже созданные события останутся/)).toBeVisible();expect(fetcher.mock.calls.some(c=>c[1]?.method==='POST')).toBe(false);fireEvent.click(screen.getByText('Да, отключить'));await screen.findByText('Календарь отключён.');expect(fetcher).toHaveBeenCalledWith('/api/v1/me/calendar/disconnect',expect.objectContaining({method:'POST'}));
});
