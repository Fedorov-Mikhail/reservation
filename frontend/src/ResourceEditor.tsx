import {useEffect,useState} from 'react';
import type {FormEvent} from 'react';
import {api,errorText} from './api';
import type {Resource} from './api';
export default function ResourceEditor({resource,onSaved}:{resource:Resource;onSaved:(value:Resource)=>void}){
 const [allowed,setAllowed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 useEffect(()=>{const controller=new AbortController();api<{canManage:boolean}>('/resources/'+resource.id+'/permissions','GET',undefined,controller.signal).then(p=>{if(!controller.signal.aborted)setAllowed(p.canManage);}).catch(e=>{if(!controller.signal.aborted)setError(errorText(e));});return()=>controller.abort();},[resource.id]);
 async function save(e:FormEvent<HTMLFormElement>){e.preventDefault();const values=new FormData(e.currentTarget);const name=String(values.get('name')).trim();if(!name){setError('Введите название ресурса.');return;}setBusy(true);setError('');setMessage('');try{const result=await api<Resource>('/resources/'+resource.id,'PUT',{name,location:String(values.get('location')).trim(),description:String(values.get('description')).trim()});onSaved(result);setMessage('Ресурс сохранён.');}catch(e){setError(errorText(e));}finally{setBusy(false);}}
 if(!allowed)return error?<p role="alert" className="notice error">{error}</p>:null;
 return <details className="booking-form"><summary>Редактировать ресурс</summary><form onSubmit={save}><fieldset disabled={busy}><label>Название ресурса<input name="name" required maxLength={120} defaultValue={resource.name}/></label><label>Расположение ресурса<input name="location" maxLength={255} defaultValue={resource.location}/></label><label>Описание ресурса<textarea name="description" maxLength={2000} defaultValue={resource.description}/></label><button className="primary">Сохранить ресурс</button></fieldset>{error&&<p role="alert" className="notice error">{error}</p>}{message&&<p role="status">{message}</p>}</form></details>;
}
