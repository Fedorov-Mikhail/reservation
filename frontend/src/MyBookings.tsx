import {useEffect,useState} from 'react';
import {api,displayTime,errorText} from './api';
import type {Booking,Page,Resource} from './api';
const labels={HELD:'Удержание',CONFIRMED:'Подтверждена',CANCELLED:'Отменена',EXPIRED:'Истекла'};
export default function MyBookings({focusId}:{focusId?:string}={}){
 const [actionError,setActionError]=useState('');
 const [data,setData]=useState<Page<Booking>>(),[names,setNames]=useState<Record<string,string>>({});
 const [page,setPage]=useState(0),[revision,setRevision]=useState(0),[error,setError]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[cancelId,setCancelId]=useState<string>(),[now,setNow]=useState(Date.now());
 useEffect(()=>{const refresh=()=>setRevision(n=>n+1);const timer=setInterval(refresh,15000);const clock=setInterval(()=>setNow(Date.now()),1000);window.addEventListener('reservation:changed',refresh);return()=>{clearInterval(timer);clearInterval(clock);window.removeEventListener('reservation:changed',refresh);};},[]);
 useEffect(()=>{const controller=new AbortController();setLoading(true);setError('');
 api<Page<Booking>>('/me/bookings?page='+page+'&size=20','GET',undefined,controller.signal).then(async value=>{
  if(focusId && !value.items.some(b=>b.id===focusId)){
    const focused=await api<Booking>('/bookings/'+encodeURIComponent(focusId),'GET',undefined,controller.signal);
    value={...value,items:[focused,...value.items]};
  }
  const ids=[...new Set(value.items.map(b=>b.resourceId).filter((id):id is string=>!!id))];
  const resources=await Promise.all(ids.map(async id=>{try{return await api<Resource>('/resources/'+id,'GET',undefined,controller.signal);}catch{return {id,name:'Ресурс '+id.slice(0,8)};}}));
  if(!controller.signal.aborted){setData(value);setNames(Object.fromEntries(resources.map(r=>[r.id,r.name])));}
 }).catch(e=>{if(!controller.signal.aborted){setData(undefined);setError(errorText(e));}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();},[page,revision,focusId]);
 async function action(id:string,operation:'confirm'|'cancel'){setBusy(true);setActionError('');setMessage('');try{await api('/bookings/'+id+'/'+operation,'POST');setCancelId(undefined);setMessage(operation==='confirm'?'Бронь подтверждена.':'Бронь отменена.');}catch(e){setActionError(errorText(e));}finally{setBusy(false);setRevision(n=>n+1);}}
 return <section className="card personal-bookings"><div className="section-title"><h2>Мои бронирования</h2><button disabled={busy} onClick={()=>setRevision(n=>n+1)}>Обновить</button></div>{focusId&&<p className="notice">Вы открыли бронь {focusId}. Она выделена в списке ниже.</p>}<p className="muted">Все ваши брони и удержания. Время: {Intl.DateTimeFormat().resolvedOptions().timeZone}.</p>
 {(error||actionError)&&<p className="notice error" role="alert">{actionError||error}</p>}{message&&<p className="notice success" role="status">{message}</p>}
 {loading&&<p role="status">Обновление бронирований…</p>}
 {cancelId&&<div className="notice confirmation"><p>Отменить выбранную бронь?</p><div className="actions"><button className="danger" disabled={busy} onClick={()=>action(cancelId,'cancel')}>Да, отменить</button><button disabled={busy} onClick={()=>setCancelId(undefined)}>Оставить бронь</button></div></div>}
 {!loading&&data&&<><ul className="bookings">{data.items.map(b=>{const expired=b.status==='HELD'&&!!b.expiresAt&&Date.parse(b.expiresAt)<=now;const seconds=b.expiresAt?Math.max(0,Math.ceil((Date.parse(b.expiresAt)-now)/1000)):0;return <li key={b.id} id={'booking-'+b.id} className={focusId===b.id?'focused-booking':undefined}><div><strong>{names[b.resourceId??'']??'Ресурс'}</strong><p>{displayTime(b.startsAt)} — {displayTime(b.endsAt)}</p>{b.cancellationReason&&<p className="muted">Причина отмены: {b.cancellationReason}. Автор: {b.cancelledBy??'—'}</p>}<span className={'badge '+b.status.toLowerCase()}>{expired?'Удержание истекло':labels[b.status]}</span>{b.status==='HELD'&&!expired&&<p className="muted">Осталось {Math.floor(seconds/60)} мин {seconds%60} сек</p>}</div><div className="actions">{b.status==='HELD'&&<button disabled={busy||expired} onClick={()=>action(b.id,'confirm')}>Подтвердить бронь</button>}{['HELD','CONFIRMED'].includes(b.status)&&<button disabled={busy||expired||Date.parse(b.startsAt)<=now} onClick={()=>setCancelId(b.id)}>Отменить бронь</button>}</div></li>;})}</ul>{!data.items.length&&<p className="empty">У вас пока нет бронирований. Выберите ресурс и подходящее время.</p>}<div className="pager"><button disabled={busy||page===0} onClick={()=>{setCancelId(undefined);setPage(n=>n-1);}}>Назад</button><span>Страница {page+1} · Всего {data.totalElements}</span><button disabled={busy||(page+1)*20>=data.totalElements} onClick={()=>{setCancelId(undefined);setPage(n=>n+1);}}>Далее</button></div></>}
 </section>;
}
