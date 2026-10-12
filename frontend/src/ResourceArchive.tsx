import {useState} from 'react';
import {api,errorText} from './api';
import type {Resource} from './api';
export default function ResourceArchive({resource,onSaved}:{resource:Resource;onSaved:(value:Resource)=>void}) {
 const [confirm,setConfirm]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 async function save(){setBusy(true);setError('');try{onSaved(await api<Resource>('/resources/'+resource.id+'/'+(resource.archivedAt?'restore':'archive'),'POST'));setConfirm(false);}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <div className="archive-controls">{resource.archivedAt&&<p className="notice">В архиве с {new Date(resource.archivedAt).toLocaleString('ru-RU')}</p>}{error&&<p role="alert" className="notice error">{error}</p>}{confirm?<div className="notice confirmation"><p>{resource.archivedAt?'Вернуть ресурс в каталог новых бронирований?':'Архивировать ресурс? История сохранится. Для архива сначала завершите активные брони и заявки.'}</p><div className="actions"><button disabled={busy} onClick={save}>{resource.archivedAt?'Да, восстановить':'Да, архивировать'}</button><button disabled={busy} onClick={()=>setConfirm(false)}>Назад</button></div></div>:<button onClick={()=>setConfirm(true)}>{resource.archivedAt?'Восстановить ресурс':'Архивировать ресурс'}</button>}</div>;
}
