import ActivityPanel from './ActivityPanel';
import ScheduleEditor from './ScheduleEditor';
import type { Account } from './AuthGate';
import AdminUsers from './AdminUsers';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api, bookingBody, dayWindow, displayTime, errorText, localDate } from './api';
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
function Schedule({resource,day}: {resource:Resource; day:string}) {
  const [revision,setRevision]=useState(0), [page,setPage]=useState(0);
  const [start,setStart]=useState(day+'T10:00'), [end,setEnd]=useState(day+'T11:00');
  const [busy,setBusy]=useState(false), [error,setError]=useState(''), [message,setMessage]=useState('');
  const [mode,setMode]=useState('bookings'),[reason,setReason]=useState('');
  const [cancelId,setCancelId]=useState<string>();
  const window=dayWindow(day);
  const availability=useData<{intervals:Interval[]}>(`/resources/${resource.id}/availability?${window}&minDurationMinutes=1`,revision);
  const bookings=useData<Page<Booking>>(`/resources/${resource.id}/bookings?${window}&page=${page}&size=20`,revision);
  function refresh() { setRevision(value=>value+1); }
  useEffect(()=>{const timer=setInterval(()=>{if(!busy)refresh();},15000);return()=>clearInterval(timer);},[busy]);
  async function confirm(id:string){setBusy(true);setError('');setMessage('');try{await api('/bookings/'+id+'/confirm','POST');setMessage('Бронь подтверждена.');}catch(e){setError(errorText(e));}finally{setBusy(false);refresh();}}
  async function create(event:FormEvent) {
    event.preventDefault(); setError(''); setMessage('');
    let body;
    try { body=bookingBody(start,end); } catch(error) {setError(errorText(error)); return;}
    setBusy(true);
    try {
      await api<Booking>(`/resources/${resource.id}/${mode}`,'POST',body);
      setMessage(mode==='holds'?'Время удержано. Подтвердите бронь до истечения срока.':mode==='waitlist'?'Заявка добавлена в очередь.':'Бронь создана.'); setPage(0);
    } catch(error) { setError(errorText(error)); }
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
    <p className="muted">Доступность на выбранный день. Окончательное подтверждение — при создании брони.</p>
    <ErrorNotice text={availability.error}/>
    {availability.loading?<p role="status">Загрузка доступности…</p>:availability.data && <div className="slots">
      {availability.data.intervals.length===0?<p>Свободных интервалов нет.</p>:availability.data.intervals.map(interval=>
        <span className="slot" key={interval.startsAt}>{displayTime(interval.startsAt)} — {displayTime(interval.endsAt)}</span>)}
    </div>}
    <section className="booking-form"><h3>Новая бронь</h3><form onSubmit={create}>
      <label>Действие<select value={mode} onChange={e=>setMode(e.target.value)}><option value="bookings">Забронировать сразу</option><option value="holds">Удержать на 5 минут</option><option value="waitlist">Встать в очередь</option></select></label><div className="time-inputs"><label>Начало брони<input type="datetime-local" required value={start} onChange={e=>setStart(e.target.value)}/></label>
      <label>Конец брони<input type="datetime-local" required value={end} onChange={e=>setEnd(e.target.value)}/></label></div>
      <p className="muted">От 1 минуты до 24 часов. Бронирование доступно на год вперёд.</p>
      <button className="primary" disabled={busy}>{busy?'Подождите…':mode==='holds'?'Удержать время':mode==='waitlist'?'Встать в очередь':'Забронировать'}</button>
    </form></section>
    <ErrorNotice text={error}/>{message && <p className="notice success" role="status">{message}</p>}
    {cancelId && <div className="notice confirmation" role="group" aria-label="Подтверждение отмены">
      <p>Отменить выбранную бронь? Время снова станет доступно.</p>
      <label>Причина отмены (обязательна для чужой брони)<input maxLength={500} value={reason} onChange={e=>setReason(e.target.value)}/></label><div className="actions"><button className="danger" onClick={cancel} disabled={busy}>Да, отменить</button>
      <button onClick={()=>setCancelId(undefined)} disabled={busy}>Оставить бронь</button></div>
    </div>}
    <div className="section-title"><h3>Бронирования за день</h3><span className="muted">{bookings.data?.totalElements ?? '—'}</span></div>
    <ErrorNotice text={bookings.error}/>
    {bookings.loading?<p role="status">Загрузка бронирований…</p>:bookings.data && <>
      {bookings.data.items.length===0?<div className="empty">На этот день пока нет бронирований.</div>:<ul className="bookings">
        {bookings.data.items.map(booking=><li key={booking.id}><div><strong>{displayTime(booking.startsAt)} — {displayTime(booking.endsAt)}</strong>
          <span className={'badge '+(booking.status==='CANCELLED'?'cancelled':'')}>{{CONFIRMED:'Подтверждена',CANCELLED:'Отменена',HELD:'Удержание',EXPIRED:'Истекла'}[booking.status]}</span></div>
          {booking.status==='HELD' && <div><p className="muted">До {booking.expiresAt?displayTime(booking.expiresAt):'—'}</p><button disabled={busy} onClick={()=>confirm(booking.id)}>Подтвердить бронь</button></div>}
          {(booking.status==='CONFIRMED'||booking.status==='HELD') && <button disabled={busy || +new Date(booking.startsAt)<=Date.now()} onClick={()=>setCancelId(booking.id)}>Отменить бронь</button>}
        </li>)}</ul>}
      <Pager page={page} total={bookings.data.totalElements} change={value=>{setCancelId(undefined);setPage(value);}}/>
    </>}
  </div>;
}
export default function App({user, onLogout}: {user?: Account; onLogout?:()=>Promise<void>}) {
  const [revision,setRevision]=useState(0),[page,setPage]=useState(0),[selected,setSelected]=useState<Resource>();
  const [day,setDay]=useState(()=>{const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);return localDate(tomorrow);});
  const resources=useData<Page<Resource>>(`/resources?page=${page}&size=20`,revision);
  const zone=Intl.DateTimeFormat().resolvedOptions().timeZone;
  return <><header className="header"><div className="brand"><span className="brand-mark">R</span><div><strong>Reservation</strong><span>Пространство для ваших планов</span></div></div>
    <div className="actions"><span className="local-tag">{user?.username ?? "Локальный сервис"}</span>{onLogout && <button onClick={onLogout}>Выйти</button>}</div></header>
    <main><div className="intro"><p className="eyebrow">БРОНИРОВАНИЕ РЕСУРСОВ</p><h1>Найдите время для важного.</h1><p>Выберите ресурс, проверьте доступность и запланируйте встречу.</p></div>
    <div className="layout"><aside className="card resources"><div className="section-title"><h2>Ресурсы</h2><span className="count">{resources.data?.totalElements ?? '—'}</span></div>
      <button className="refresh" onClick={()=>setRevision(value=>value+1)}>Обновить ресурсы</button>
      <ErrorNotice text={resources.error}/>{resources.loading?<p role="status">Загрузка ресурсов…</p>:resources.data && <>
      {resources.data.items.length===0?<p className="empty">Добавьте первый ресурс, чтобы начать.</p>:<ul className="resource-list">{resources.data.items.map(resource=>
        <li key={resource.id}><button aria-pressed={selected?.id===resource.id} className={selected?.id===resource.id?'selected':''} onClick={()=>setSelected(resource)}>
          <strong>{resource.name}</strong><span>{resource.location || 'Расположение не указано'}</span></button></li>)}</ul>}
      <Pager page={page} total={resources.data.totalElements} change={setPage}/></>}
      {(!user || user.role === "ADMIN") && <ResourceForm refresh={()=>setRevision(value=>value+1)} created={resource=>{setSelected(resource);setPage(0);setRevision(value=>value+1);}}/>}
    </aside><section className="card detail">
      {!selected?<div className="welcome"><span className="welcome-icon">◷</span><h2>Начните с выбора ресурса</h2><p>Его расписание и бронирования появятся здесь.<br/>Можно также создать новый ресурс слева.</p></div>:<>
        <div className="detail-heading"><div><p className="eyebrow">РАСПИСАНИЕ РЕСУРСА</p><h2>{selected.name}</h2><p className="muted">{selected.location}</p></div>
          <label>Дата расписания<input type="date" required value={day} min="0001-01-01" max="9998-12-31" onChange={e=>{if (/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(e.target.value) && e.target.value>='0001-01-01' && e.target.value<='9998-12-31') setDay(e.target.value);}}/></label></div>
        {selected.description && <p className="description">{selected.description}</p>}
        <p className="timezone">Время показано в часовом поясе {zone}.</p>
        <Schedule key={selected.id+day} resource={selected} day={day}/>
      </>}
    </section></div>{user && <ActivityPanel/>}{user?.role === "ADMIN" && <AdminUsers resourceId={selected?.id}/>}<footer>Reservation · Ваше расписание в одном месте</footer></main></>;
}





