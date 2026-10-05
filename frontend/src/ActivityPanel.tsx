import {useEffect,useState} from 'react';
import {api,displayTime,errorText} from './api';
interface Entry{id:string;resourceName:string;startsAt:string;endsAt:string;status:string;offeredBookingId?:string}
interface Notice{id:string;bookingId:string;status:string;createdAt:string;readAt?:string}
const statuses:Record<string,string>={WAITING:'Ожидание',OFFERED:'Предложено время',FULFILLED:'Подтверждено',CANCELLED:'Отменено',EXPIRED:'Истекло',HELD:'Время удержано',CONFIRMED:'Бронь подтверждена'};
export default function ActivityPanel(){
 const [entries,setEntries]=useState<Entry[]>([]),[notices,setNotices]=useState<Notice[]>([]),[error,setError]=useState(''),[busy,setBusy]=useState(false),[revision,setRevision]=useState(0),[queuePage,setQueuePage]=useState(0),[noticePage,setNoticePage]=useState(0);
 useEffect(()=>{const timer=setInterval(()=>setRevision(n=>n+1),15000);return()=>clearInterval(timer);},[]);
 useEffect(()=>{const controller=new AbortController();Promise.all([api<Entry[]>('/me/waitlist?page='+queuePage,'GET',undefined,controller.signal),api<Notice[]>('/me/notifications?page='+noticePage,'GET',undefined,controller.signal)]).then(([queue,inbox])=>{setEntries(queue);setNotices(inbox);setError('');}).catch(e=>{if(!controller.signal.aborted){setEntries([]);setNotices([]);setError(errorText(e));}});return()=>controller.abort();},[queuePage,noticePage,revision]);
 async function act(path:string){setBusy(true);setError('');try{await api(path,'POST');setRevision(n=>n+1);}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <section className="card activity"><div className="section-title"><h2>Мои заявки и уведомления</h2><button onClick={()=>setRevision(n=>n+1)}>Обновить</button></div>{error&&<p role="alert" className="notice error">{error}</p>}
 <details><summary>Лист ожидания</summary><ul className="bookings">{entries.map(entry=><li key={entry.id}><div><strong>{entry.resourceName}</strong><p>{displayTime(entry.startsAt)} — {displayTime(entry.endsAt)}</p><span className="badge">{statuses[entry.status]}</span></div>
 <div className="actions">{entry.status==='OFFERED'&&<button disabled={busy} onClick={()=>act('/bookings/'+entry.offeredBookingId+'/confirm')}>Принять предложение</button>}{['WAITING','OFFERED'].includes(entry.status)&&<button disabled={busy} onClick={()=>act('/waitlist/'+entry.id+'/cancel')}>Отменить заявку</button>}</div></li>)}</ul>{entries.length===0&&<p className="empty">Заявок пока нет.</p>}
 <div className="pager"><button disabled={!queuePage} onClick={()=>setQueuePage(n=>n-1)}>Назад</button><span>{queuePage+1}</span><button disabled={entries.length<20} onClick={()=>setQueuePage(n=>n+1)}>Далее</button></div></details>
 <details><summary>Уведомления</summary><ul className="bookings">{notices.map(notice=><li key={notice.id}><div><strong>{statuses[notice.status]??notice.status}</strong><p className="muted">{displayTime(notice.createdAt)}</p><small>Бронь {notice.bookingId.slice(0,8)}</small></div>{!notice.readAt&&<button disabled={busy} onClick={()=>act('/me/notifications/'+notice.id+'/read')}>Прочитано</button>}</li>)}</ul>{notices.length===0&&<p className="empty">Уведомлений пока нет.</p>}
 <div className="pager"><button disabled={!noticePage} onClick={()=>setNoticePage(n=>n-1)}>Назад</button><span>{noticePage+1}</span><button disabled={notices.length<20} onClick={()=>setNoticePage(n=>n+1)}>Далее</button></div></details></section>;
}

