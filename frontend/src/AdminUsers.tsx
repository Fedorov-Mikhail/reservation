import {useEffect,useState} from 'react';
import {api,errorText} from './api';
import type {Account} from './AuthGate';
export default function AdminUsers({resourceId}:{resourceId?:string}){
 const [users,setUsers]=useState<Account[]>([]),[error,setError]=useState(''),[page,setPage]=useState(0),[revision,setRevision]=useState(0),[busy,setBusy]=useState(false);
 useEffect(()=>{const controller=new AbortController();api<Account[]>('/admin/users?page='+page+'&size=20','GET',undefined,controller.signal).then(setUsers).catch(e=>{if(!controller.signal.aborted)setError(errorText(e));});return()=>controller.abort();},[page,revision]);
 async function update(user:Account,role:string,enabled:boolean){setBusy(true);setError('');try{await api('/admin/users/'+user.id,'PUT',{role,enabled});setRevision(x=>x+1);}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 async function assign(id:string){setBusy(true);setError('');try{await api('/resources/'+resourceId+'/managers/'+id,'PUT');}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <details className="card admin-users"><summary>Управление пользователями</summary>{error&&<p role="alert" className="notice error">{error}</p>}
 <ul className="bookings">{users.map(user=><li key={user.id}><div><strong>{user.username}</strong><small>{user.id}</small></div>
 <select aria-label={'Роль '+user.username} value={user.role} disabled={busy||user.username==='legacy-system'} onChange={e=>update(user,e.target.value,user.enabled)}><option value="USER">Пользователь</option><option value="ADMIN">Администратор</option></select>
 <button disabled={busy||user.username==='legacy-system'} onClick={()=>update(user,user.role,!user.enabled)}>{user.enabled?'Отключить':'Включить'}</button>{resourceId && user.enabled && <button disabled={busy} onClick={()=>assign(user.id)}>Управляющий выбранным ресурсом</button>}</li>)}</ul>
 <div className="pager"><button disabled={page===0} onClick={()=>setPage(page-1)}>Назад</button><span>Страница {page+1}</span><button disabled={users.length<20} onClick={()=>setPage(page+1)}>Далее</button></div></details>;
}


