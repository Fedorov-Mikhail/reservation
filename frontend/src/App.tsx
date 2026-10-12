import SettingsPanel from './SettingsPanel';
import AuditPanel from './AuditPanel';
import ResourceArchive from './ResourceArchive';
import ResourceDelete from './ResourceDelete';
import ResourceEditor from './ResourceEditor';
import MyBookings from './MyBookings';
import ActivityPanel from './ActivityPanel';
import ScheduleEditor from './ScheduleEditor';
import type { Account } from './AuthGate';
import AdminUsers from './AdminUsers';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, bookingBody, calendarWindow, displayTime, errorText, localDate, localDateTime, requestKey, loadAttempt, clearAttempt } from './api';
import type { Booking, Interval, Page, Resource } from './api';

function useData<T>(path: string, revision: number) {
  const [state, setState] = useState<{data?: T; error?: string; loading: boolean}>({loading:true});
  useEffect(() => {
    const controller = new AbortController();
    setState({loading:true});
    api<T>(path, 'GET', undefined, controller.signal).then(data => {
      if (!controller.signal.aborted) setState({data, loading:false});
    }).catch(error => {
      if (!controller.signal.aborted) setState({error:errorText(error), loading:false});
    });
    return () => controller.abort();
  }, [path, revision]);
  return state;
}
function ErrorNotice({text}: {text?: string}) {
  return text ? <p className="notice error" role="alert">{text}</p> : null;
}
function Pager({page, total, change}: {page:number; total:number; change:(page:number)=>void}) {
  return <div className="pager"><button type="button" disabled={page===0} onClick={()=>change(page-1)}>Назад</button>
    <span>Страница {page+1} · Всего {total}</span>
    <button type="button" disabled={(page+1)*20>=total} onClick={()=>change(page+1)}>Далее</button></div>;
}
function ResourceForm({created, refresh}: {created:(resource:Resource)=>void; refresh:()=>void}) {
  const [busy,setBusy]=useState(false), [error,setError]=useState('');
  async function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form=event.currentTarget, data=new FormData(form);
    const name=String(data.get('name')).trim();
    if (!name) { setError('Введите название ресурса.'); return; }
    setBusy(true); setError('');
    try {
      const resource=await api<Resource>('/resources','POST',{name, location:String(data.get('location')).trim(),description:String(data.get('description')).trim()});
      form.reset(); created(resource);
    } catch(error) { setError(errorText(error)); refresh(); } finally { setBusy(false); }
  }
  return <details className="create-resource"><summary>＋ Новый ресурс</summary>
    <form onSubmit={submit}><label>Название<input name="name" required maxLength={120} placeholder="Переговорная А"/></label>
    <label>Расположение<input name="location" maxLength={255} placeholder="Второй этаж"/></label>
    <label>Описание<textarea name="description" maxLength={2000} rows={2}/></label>
    <ErrorNotice text={error}/><button className="primary" disabled={busy}>{busy?'Создание…':'Создать ресурс'}</button></form></details>;
}
function Schedule({resource,day,user}: {resource:Resource; day:string; user?:Account}) {
  const [revision,setRevision]=useState(0), [page,setPage]=useState(0);
  const accountId=user?.id ?? 'local';
  const pending=loadAttempt(accountId);
  const restored=pending?.resourceId===resource.id ? pending : undefined;
  const [start,setStart]=useState(restored?.start ?? day+'T10:00'), [end,setEnd]=useState(restored?.end ?? day+'T11:00');
  const [view,setView]=useState('day'), [duration,setDuration]=useState(1);
  const [busy,setBusy]=useState(false), [error,setError]=useState(''), [message,setMessage]=useState('');
  const [mode,setMode]=useState(restored?.mode ?? 'bookings'),[reason,setReason]=useState('');
  const [cancelId,setCancelId]=useState<string>();
  const window=calendarWindow(day,view==='week'?7:1);
  const days=Array.from({length:view==='week'?7:1},(_,index)=>{const date=new Date(day+'T12:00:00');date.setDate(date.getDate()+index);return localDate(date);});
  const availability=useData<{intervals:Interval[]}>(`/resources/${resource.id}/availability?${window}&minDurationMinutes=${duration}`,revision);
  const bookings=useData<Page<Booking>>(`/resources/${resource.id}/bookings?${window}&page=${page}&size=20`,revision);
  const visibleBookings=bookings.data?.items.filter(b=>(!user || user.role==='ADMIN' || b.resourceId===resource.id) && new Date(b.endsAt)>new Date(day+'T00:00:00') && new Date(b.startsAt)<new Date(new Date(day+'T00:00:00').setDate(new Date(day+'T00:00:00').getDate()+days.length)));
  useEffect(()=>{const attempt=loadAttempt(accountId);if(attempt && (attempt.resourceId!==resource.id || attempt.start!==start || attempt.end!==end || attempt.mode!==mode))clearAttempt(accountId);},[accountId,resource.id,start,end,mode]);
  function refresh() { setRevision(value=>value+1); }
  useEffect(()=>{const changed=()=>refresh();const timer=setInterval(()=>{if(!busy)refresh();},15000);globalThis.addEventListener('reservation:changed',changed);return()=>{clearInterval(timer);globalThis.removeEventListener('reservation:changed',changed);};},[busy]);
  async function confirm(id:string){setBusy(true);setError('');setMessage('');try{await api('/bookings/'+id+'/confirm','POST');setMessage('Бронь подтверждена.');}catch(e){setError(errorText(e));}finally{setBusy(false);refresh();}}
  async function create(event:FormEvent) {
    event.preventDefault(); setError(''); setMessage('');
    let body;
    try { body=bookingBody(start,end); } catch(error) {setError(errorText(error)); return;}
    const headers=mode==='waitlist'?undefined:{'Idempotency-Key':requestKey(accountId,resource.id,mode,start,end)};
    setBusy(true);
    try {
      await api<Booking>(`/resources/${resource.id}/${mode}`,'POST',body,undefined,headers);
      if(headers)clearAttempt(accountId,headers['Idempotency-Key']);
      setMessage(mode==='holds'?'Время удержано. Подтвердите бронь до истечения срока.':mode==='waitlist'?'Заявка добавлена в очередь.':'Бронь создана.'); setPage(0);
    } catch(error) { if(headers && error instanceof ApiError && error.status>0 && error.status<500)clearAttempt(accountId,headers['Idempotency-Key']); setError(errorText(error)); }
    finally { setBusy(false); refresh(); }
  }
  async function cancel() {
    if (!cancelId) return;
    setBusy(true);setError('');setMessage('');
    try {
      await api<Booking>(`/bookings/${cancelId}/cancel`,'POST',reason?{reason}:undefined);
      setMessage('Бронь отменена.');setCancelId(undefined);
    } catch(error) {setError(errorText(error));}
    finally {setBusy(false);refresh();}
  }
  return <div className="schedule">
    <ScheduleEditor id={resource.id} onChange={refresh}/><div className="section-title"><h3>Свободное время</h3><button onClick={refresh} disabled={busy}>Обновить данные</button></div>
    <div className="calendar-controls"><label>Представление<select value={view} onChange={e=>{setView(e.target.value);setPage(0);}}><option value="day">День</option><option value="week">Неделя</option></select></label><label>Свободный интервал, минут<input type="number" min={1} max={1440} value={duration} onChange={e=>setDuration(Math.max(1,Math.min(1440,Number(e.target.value)||1)))}/></label></div><p className="muted">{day} · {Intl.DateTimeFormat().resolvedOptions().timeZone}. Окончательное подтверждение — при создании брони. Занятость других пользователей показана без личных данных.</p>
    <ErrorNotice text={availability.error}/>
    {availability.loading?<p role="status">Загрузка доступности…</p>:availability.data && <div className={'calendar-grid '+view}>
      {days.map(date=><section className="calendar-day" key={date}><h4>{new Intl.DateTimeFormat('ru-RU',{weekday:'short',day:'numeric',month:'short'}).format(new Date(date+'T12:00:00'))}</h4>
      <p className="muted">Свободные интервалы</p><div className="slots">{availability.data!.intervals.filter(i=>new Date(i.endsAt)>new Date(date+'T00:00:00') && new Date(i.startsAt)<new Date(new Date(date+'T00:00:00').setDate(new Date(date+'T00:00:00').getDate()+1))).map(interval=>{
        const floor=new Date(date+'T00:00:00'), ceiling=new Date(floor);ceiling.setDate(ceiling.getDate()+1);
        const startAt=new Date(Math.max(Date.parse(interval.startsAt),+floor)),endAt=new Date(Math.min(Date.parse(interval.endsAt),+ceiling));
        if(+endAt-+startAt<duration*60000)return null;
        return <button type="button" className="slot" key={interval.startsAt} disabled={!!resource.archivedAt || +endAt<=Date.now()} onClick={()=>{setStart(localDateTime(startAt.toISOString()));setEnd(localDateTime(new Date(Math.min(+endAt,+startAt+3600000)).toISOString()));setMessage('Выбранный интервал перенесён в форму.');}}>{displayTime(startAt.toISOString())} — {displayTime(endAt.toISOString())}</button>;
      })}</div>{!availability.data!.intervals.some(i=>{const floor=new Date(date+'T00:00:00'),ceiling=new Date(floor);ceiling.setDate(ceiling.getDate()+1);return Math.min(Date.parse(i.endsAt),+ceiling)-Math.max(Date.parse(i.startsAt),+floor)>=duration*60000;})&&<p className="muted">Нет доступных интервалов.</p>}</section>)}
    </div>}
    <section className="booking-form"><h3>Новая бронь</h3>{resource.archivedAt&&<p className="notice">Ресурс в архиве. Новые бронирования недоступны.</p>}<form onSubmit={create}><fieldset disabled={busy || !!resource.archivedAt}>
      <label>Действие<select aria-label="Действие" value={mode} onChange={e=>setMode(e.target.value)}><option value="bookings">Забронировать сразу</option><option value="holds">Удержать на 5 минут</option><option value="waitlist">Встать в очередь</option></select></label><div className="time-inputs"><label>Начало брони<input type="datetime-local" required value={start} onChange={e=>setStart(e.target.value)}/></label>
      <label>Конец брони<input type="datetime-local" required value={end} onChange={e=>setEnd(e.target.value)}/></label></div>
      <p className="muted">От 1 минуты до 24 часов. Бронирование доступно на год вперёд.</p>
      <button className="primary" disabled={busy}>{busy?'Подождите…':mode==='holds'?'Удержать время':mode==='waitlist'?'Встать в очередь':'Забронировать'}</button>
    </fieldset></form></section>
    <ErrorNotice text={error}/>{message && <p className="notice success" role="status">{message}</p>}
    {cancelId && <div className="notice confirmation" role="group" aria-label="Подтверждение отмены">
      <p>Отменить выбранную бронь? Время снова станет доступно.</p>
      <label>Причина отмены (обязательна для чужой брони)<input maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label><div className="actions"><button className="danger" onClick={cancel} disabled={busy}>Да, отменить</button>
      <button onClick={()=>setCancelId(undefined)} disabled={busy}>Оставить бронь</button></div>
    </div>}
    <div className="section-title"><h3>Бронирования выбранного периода</h3><span className="muted">{bookings.data?.totalElements ?? '—'}</span></div>
    <ErrorNotice text={bookings.error}/>
    {bookings.loading?<p role="status">Загрузка бронирований…</p>:bookings.data && <>
      {visibleBookings?.length===0?<div className="empty">На этот день пока нет бронирований.</div>:<ul className="bookings">
        {visibleBookings?.map(booking=><li key={booking.id}><div><strong>{displayTime(booking.startsAt)} — {displayTime(booking.endsAt)}</strong>
          <span className={'badge '+(booking.status==='CANCELLED'?'cancelled':'')}>{{CONFIRMED:'Подтверждена',CANCELLED:'Отменена',HELD:'Удержание',EXPIRED:'Истекла'}[booking.status]}</span></div>
          {booking.cancellationReason&&<p className="muted">Причина отмены: {booking.cancellationReason}. Автор: {booking.cancelledBy ?? '—'}</p>}
          {booking.status==='HELD' && <div><p className="muted">До {booking.expiresAt?displayTime(booking.expiresAt):'—'}</p><button disabled={busy || (!!booking.expiresAt && Date.parse(booking.expiresAt)<=Date.now())} onClick={()=>confirm(booking.id)}>Подтвердить бронь</button></div>}
          {(booking.status==='CONFIRMED'||booking.status==='HELD') && <button disabled={busy || +new Date(booking.startsAt)<=Date.now()} onClick={()=>setCancelId(booking.id)}>Отменить бронь</button>}
        </li>)}</ul>}
      <Pager page={page} total={bookings.data.totalElements} change={value=>{setCancelId(undefined);setPage(value);}}/>
    </>}
  </div>;
}
export default function App({user, onLogout}: {user?: Account; onLogout?:()=>Promise<void>}) {
  const [revision,setRevision]=useState(0),[page,setPage]=useState(0),[selected,setSelected]=useState<Resource>();
  const [day,setDay]=useState(()=>{const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);return localDate(tomorrow);});
  const [resourceMessage,setResourceMessage]=useState(''),[search,setSearch]=useState(''),[archived,setArchived]=useState(false);
  const [bookingFocus,setBookingFocus]=useState<string>(()=>new URLSearchParams(location.search).get('booking')??'');
  const resources=useData<Page<Resource>>(`/resources?page=${page}&size=20&q=${encodeURIComponent(search)}&archived=${archived && user?.role==='ADMIN'}`,revision);
  const [section,setSection]=useState(()=>new URLSearchParams(location.search).get('calendar')==='connected'?'settings':new URLSearchParams(location.search).get('booking')?'bookings':'resources');
  const zone=Intl.DateTimeFormat().resolvedOptions().timeZone;
  return <><header className="header"><div className="brand"><span className="brand-mark">R</span><div><strong>Reservation</strong><span>Пространство для ваших планов</span></div></div>
    <div className="actions"><span className="local-tag">{user?.username ?? "Локальный сервис"}</span>{onLogout && <button onClick={onLogout}>Выйти</button>}</div></header>
    <main><div className="intro"><p className="eyebrow">БРОНИРОВАНИЕ РЕСУРСОВ</p><h1>Найдите время для важного.</h1><p>Выберите ресурс, проверьте доступность и запланируйте встречу.</p></div>
    {user&&<nav className="app-nav" aria-label="Разделы приложения">{[['resources','Ресурсы'],['bookings','Мои бронирования'],['activity','Заявки и уведомления'],['settings','Настройки'],...(user.role==='ADMIN'?[['users','Пользователи'],['audit','Журнал действий']]:[])].map(([id,label])=><button key={id} aria-current={section===id?'page':undefined} onClick={()=>setSection(id)}>{label}</button>)}</nav>}
    {section==='bookings'&&<MyBookings focusId={bookingFocus}/>}{section==='activity'&&<ActivityPanel onBooking={id=>{setBookingFocus(id);setSection('bookings');}}/>}{section==='settings'&&<SettingsPanel admin={user?.role==='ADMIN'}/>}{section==='audit'&&user?.role==='ADMIN'&&<AuditPanel entityId={selected?.id}/>}{section==='users'&&user?.role==='ADMIN'&&<><p className="muted">{selected?'Права управления для ресурса: '+selected.name:'Для назначения управляющего сначала выберите ресурс в разделе «Ресурсы».'}</p><AdminUsers resourceId={selected?.id}/></>}
    <div hidden={section!=='resources'}>{resourceMessage&&<p className="notice success" role="status">{resourceMessage}</p>}<div className="layout"><aside className="card resources"><div className="section-title"><h2>Ресурсы</h2><span className="count">{resources.data?.totalElements ?? '—'}</span></div>
      <label>Поиск ресурсов<input type="search" placeholder="Название или расположение" value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label>{user?.role==='ADMIN'&&<label className="checkbox"><input type="checkbox" checked={archived} onChange={e=>{setArchived(e.target.checked);setSelected(undefined);setPage(0);}}/> Архив ресурсов</label>}<button className="refresh" onClick={()=>setRevision(value=>value+1)}>Обновить ресурсы</button>
      <ErrorNotice text={resources.error}/>{resources.loading?<p role="status">Загрузка ресурсов…</p>:resources.data && <>
      {resources.data.items.length===0?<p className="empty">Ресурсов пока нет. Администратор может добавить их.</p>:<ul className="resource-list">{resources.data.items.map(resource=>
        <li key={resource.id}><button aria-pressed={selected?.id===resource.id} className={selected?.id===resource.id?'selected':''} onClick={()=>setSelected(resource)}>
          <strong>{resource.name}</strong><span>{resource.location || 'Расположение не указано'}</span></button></li>)}</ul>}
      <Pager page={page} total={resources.data.totalElements} change={setPage}/></>}
      {(!user || user.role === "ADMIN") && <ResourceForm refresh={()=>setRevision(value=>value+1)} created={resource=>{setSelected(resource);setPage(0);setRevision(value=>value+1);}}/>}
    </aside><section className="card detail">
      {!selected?<div className="welcome"><span className="welcome-icon">◷</span><h2>Начните с выбора ресурса</h2><p>Его расписание и бронирования появятся здесь.<br/>Выберите удобную дату для просмотра.</p></div>:<>
        <div className="detail-heading"><div><p className="eyebrow">РАСПИСАНИЕ РЕСУРСА</p><h2>{selected.name}</h2><p className="muted">{selected.location}</p></div>
          <label>Дата расписания<input type="date" required value={day} min="0001-01-01" max="9998-12-31" onChange={e=>{if (/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(e.target.value) && e.target.value>='0001-01-01' && e.target.value<='9998-12-31') setDay(e.target.value);}}/></label></div>
        {selected.description && <p className="description">{selected.description}</p>}
        <p className="timezone">Время показано в часовом поясе {zone}.</p>
        {user?.role==='ADMIN'&&<ResourceArchive resource={selected} onSaved={value=>{setSelected(value);setRevision(n=>n+1);}}/>}
        {user?.role==='ADMIN'&&<ResourceDelete key={'delete'+selected.id} resource={selected} onDeleted={()=>{setSelected(undefined);setPage(0);setRevision(n=>n+1);setResourceMessage('Ресурс удалён.');}}/>}
        <ResourceEditor key={selected.id} resource={selected} onSaved={value=>{setSelected(value);setRevision(n=>n+1);}}/>
        <Schedule key={selected.id+day} resource={selected} day={day} user={user}/>
      </>}
    </section></div></div><footer>Reservation · Ваше расписание в одном месте</footer></main></>;
}





