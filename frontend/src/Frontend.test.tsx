import {render,screen,fireEvent,waitFor,act} from '@testing-library/react';
import {test,expect,vi,afterEach} from 'vitest';
import AuthGate from './AuthGate';
import AdminUsers from './AdminUsers';
import ScheduleEditor from './ScheduleEditor';
const account={id:'u1',username:'mike',role:'USER',enabled:true};
afterEach(()=>vi.unstubAllGlobals());
test('expired session removes private content',async()=>{
 vi.stubGlobal('fetch',vi.fn(async()=>Response.json(account)));
 render(<AuthGate>{()=> <p>Private workspace</p>}</AuthGate>);
 await screen.findByText('Private workspace');
 act(()=>window.dispatchEvent(new Event('reservation:unauthorized')));
 expect(await screen.findByRole('button',{name:'Войти'})).toBeVisible();
 expect(screen.queryByText('Private workspace')).not.toBeInTheDocument();
});
test('admin can revoke resource manager permission',async()=>{
 const fetcher=vi.fn(async(url:string)=>Response.json(url.includes('/admin/users')?[account]:{token:'test'}));vi.stubGlobal('fetch',fetcher);
 render(<AdminUsers resourceId="r1"/>);
 fireEvent.click(screen.getByText('Управление пользователями'));
 fireEvent.click(await screen.findByRole('button',{name:'Отозвать управление'}));
 await waitFor(()=>expect(fetcher).toHaveBeenCalledWith('/api/v1/resources/r1/managers/u1',expect.objectContaining({method:'DELETE'})));
});
test('overlapping schedule intervals never reach server',async()=>{
 const fetcher=vi.fn(async(url:string)=>Response.json(url.endsWith('/permissions')?{canManage:true}:{zoneId:'UTC',weekly:{'1':[{startMinute:540,endMinute:1080}]},exceptions:{}}));vi.stubGlobal('fetch',fetcher);
 render(<ScheduleEditor id="r1" onChange={()=>{}}/>);
 fireEvent.click(await screen.findByText('Настроить расписание'));
 fireEvent.change(screen.getByLabelText('Понедельник'),{target:{value:'09:00-12:00, 11:00-14:00'}});
 fireEvent.click(screen.getByText('Сохранить расписание'));
 expect(await screen.findByRole('alert')).toHaveTextContent('пересекаться');
 expect(fetcher.mock.calls.some((call:any[])=>call[1]?.method==='PUT')).toBe(false);
});
import MyBookings from './MyBookings';
import {api} from './api';
test('personal bookings resolve resource names and disable expired hold',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>Response.json(url.includes('/me/bookings')?{items:[{id:'b1',resourceId:'r1',status:'HELD',startsAt:'2099-01-01T10:00:00Z',endsAt:'2099-01-01T11:00:00Z',expiresAt:'2000-01-01T00:00:00Z'}],totalElements:1}: {id:'r1',name:'Meeting room'})));
 render(<MyBookings/>);
 expect(await screen.findByText('Meeting room')).toBeVisible();
 expect(screen.getByRole('button',{name:'Подтвердить бронь'})).toBeDisabled();
});
test('unauthorized API response emits session event but wrong login does not',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>url.endsWith('/csrf')?Response.json({token:'x'}):Response.json({code:'UNAUTHENTICATED'},{status:401})));
 const listener=vi.fn();window.addEventListener('reservation:unauthorized',listener);
 try{await expect(api('/me/bookings')).rejects.toThrow();expect(listener).toHaveBeenCalledTimes(1);await expect(api('/auth/login','POST',new URLSearchParams())).rejects.toThrow();expect(listener).toHaveBeenCalledTimes(1);}finally{window.removeEventListener('reservation:unauthorized',listener);}
});
test('failed personal booking action keeps its error after refresh',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string,init?:RequestInit)=>{
  if(url.endsWith('/csrf'))return Response.json({token:'x'});
  if(init?.method==='POST')return Response.json({code:'HOLD_EXPIRED'},{status:409});
  return Response.json(url.includes('/me/bookings')?{items:[{id:'b1',resourceId:'r1',status:'HELD',startsAt:'2099-01-01T10:00:00Z',endsAt:'2099-01-01T11:00:00Z',expiresAt:'2099-01-01T00:00:00Z'}],totalElements:1}:{id:'r1',name:'Meeting room'});
 }));
 render(<MyBookings/>);fireEvent.click(await screen.findByRole('button',{name:'Подтвердить бронь'}));
 await waitFor(()=>expect(vi.mocked(fetch).mock.calls.filter(c=>String(c[0]).includes('/me/bookings')).length).toBeGreaterThanOrEqual(2));
 await screen.findByText('Meeting room');
 expect(await screen.findByRole('alert')).toHaveTextContent('Удержание истекло');
});
