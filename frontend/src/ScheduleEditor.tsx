import {useEffect,useState} from 'react';
import {api,errorText} from './api';
type Range={startMinute:number;endMinute:number};
type Definition={zoneId:string;weekly:Record<string,Range[]>;exceptions:Record<string,Range[]>};
const weekdays=['Понедельник','Вторник','Среда','Четверг','Пятница','Суббота','Воскресенье'];
function time(n:number){return String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0');}
function text(ranges:Range[]){return ranges.map(r=>time(r.startMinute)+'-'+time(r.endMinute)).join(', ');}
function parse(value:string):Range[]{
 if(!value.trim())return [];
 return value.split(',').map(part=>{
  const m=part.trim().match(/^(\d{2}):(\d{2})-(\d{2}):(\d{2})$/);
  if(!m)throw new Error('Формат интервала: 09:00-12:00, 13:00-18:00. Пустое поле — выходной.');
  const start=+m[1]*60+(+m[2]),end=+m[3]*60+(+m[4]);
  if(+m[2]>59||+m[4]>59||start>=end||end>1440)throw new Error('Проверьте часы и минуты интервалов.');
  return {startMinute:start,endMinute:end};
 });
}
export default function ScheduleEditor({id,onChange}:{id:string;onChange:()=>void}){
 const [canManage,setCanManage]=useState(false),[zone,setZone]=useState('UTC'),[weekly,setWeekly]=useState<string[]>([]),[exceptions,setExceptions]=useState<{date:string;value:string}[]>([]);
 const [error,setError]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{const controller=new AbortController();Promise.all([api<{canManage:boolean}>('/resources/'+id+'/permissions','GET',undefined,controller.signal),api<Definition>('/resources/'+id+'/schedule','GET',undefined,controller.signal)])
 .then(([permission,schedule])=>{setCanManage(permission.canManage);setZone(schedule.zoneId);setWeekly(weekdays.map((_,i)=>text(schedule.weekly[String(i+1)]??[])));setExceptions(Object.entries(schedule.exceptions).map(([date,ranges])=>({date,value:text(ranges)})));})
 .catch(e=>{if(!controller.signal.aborted)setError(errorText(e));});return()=>controller.abort();},[id]);
 async function save(){setError('');setMessage('');setBusy(true);try{
 const body:Definition={zoneId:zone,weekly:Object.fromEntries(weekly.map((value,i)=>[String(i+1),parse(value)])),exceptions:{}};
 for(const entry of exceptions){if(!entry.date||entry.date in body.exceptions)throw new Error('Даты исключений должны быть заполнены и не повторяться.');body.exceptions[entry.date]=parse(entry.value);}
 await api('/resources/'+id+'/schedule','PUT',body);setMessage('Расписание сохранено.');onChange();
 }catch(e){setError(errorText(e));}finally{setBusy(false);}}
 if(!canManage)return error?<p className="notice error">{error}</p>:null;
 return <details className="booking-form"><summary>Настроить расписание</summary><div className="schedule-settings">
 <label>Часовой пояс ресурса<input value={zone} onChange={e=>setZone(e.target.value)} placeholder="Europe/Samara"/></label>
 <p className="muted">Рабочие интервалы: 09:00-12:00, 13:00-18:00. Пусто — выходной. Полный день: 00:00-24:00.</p>
 {weekdays.map((name,i)=><label key={name}>{name}<input value={weekly[i]??''} onChange={e=>setWeekly(values=>values.map((v,n)=>n===i?e.target.value:v))}/></label>)}
 <h3>Исключения</h3><p className="muted">Исключение полностью заменяет расписание выбранного дня.</p>
 {exceptions.map((entry,i)=><div className="time-inputs" key={i}><label>Дата исключения<input type="date" value={entry.date} onChange={e=>setExceptions(entries=>entries.map((v,n)=>n===i?{...v,date:e.target.value}:v))}/></label><label>Интервалы<input value={entry.value} onChange={e=>setExceptions(entries=>entries.map((v,n)=>n===i?{...v,value:e.target.value}:v))}/></label><button onClick={()=>setExceptions(entries=>entries.filter((_,n)=>n!==i))}>Удалить исключение</button></div>)}
 <button onClick={()=>setExceptions(entries=>[...entries,{date:'',value:''}])}>Добавить исключение</button>
 {error&&<p role="alert" className="notice error">{error}</p>}{message&&<p role="status">{message}</p>}
 <button className="primary" disabled={busy} onClick={save}>Сохранить расписание</button></div></details>;
}

