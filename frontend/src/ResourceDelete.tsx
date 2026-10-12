import {useState} from 'react';
import {api,ApiError,errorText} from './api';
import type {Resource} from './api';
export default function ResourceDelete({resource,onDeleted}:{resource:Resource;onDeleted:()=>void}){
 const [confirm,setConfirm]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function remove(){setBusy(true);setError('');try{await api('/resources/'+resource.id,'DELETE');onDeleted();}catch(e){if(e instanceof ApiError&&e.status===404){onDeleted();return;}setError(errorText(e));}finally{setBusy(false);}}
 return <section className="resource-delete">{!confirm?<button className="danger" onClick={()=>{setConfirm(true);setError('');}}>Удалить ресурс</button>:<div className="notice confirmation" role="group" aria-label="Подтверждение удаления ресурса"><p>Удалить ресурс «{resource.name}»? Действие нельзя отменить.</p><p className="muted">Удаление возможно только при отсутствии броней и заявок, включая историю.</p><div className="actions"><button className="danger" disabled={busy} onClick={remove}>{busy?'Удаление…':'Да, удалить ресурс'}</button><button disabled={busy} onClick={()=>{setConfirm(false);setError('');}}>Оставить ресурс</button></div></div>}{error&&<p role="alert" className="notice error">{error}</p>}</section>;
}
