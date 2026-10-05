import {useEffect,useState} from 'react';
import type {ReactNode,FormEvent} from 'react';
import {api,ApiError,errorText} from './api';
export interface Account {id:string;username:string;role:'USER'|'ADMIN';enabled:boolean}
export default function AuthGate({children}:{children:(user:Account,logout:()=>Promise<void>)=>ReactNode}){
 const [user,setUser]=useState<Account>(),[loading,setLoading]=useState(true),[register,setRegister]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{api<Account>('/auth/me').then(setUser).catch(e=>{if(!(e instanceof ApiError&&e.status===401))setError(errorText(e));}).finally(()=>setLoading(false));},[]);
 async function submit(e:FormEvent<HTMLFormElement>){
  e.preventDefault();const values=new FormData(e.currentTarget);const username=String(values.get('username')).toLowerCase(),password=String(values.get('password'));
  setBusy(true);setError('');
  try{
   if(register)await api('/auth/register','POST',{username,password});
   await api('/auth/login','POST',new URLSearchParams({username,password}));
   setUser(await api<Account>('/auth/me'));
  }catch(e){setError(errorText(e));}finally{setBusy(false);}
 }
 async function logout(){try{await api('/auth/logout','POST');setUser(undefined);}catch(e){setError(errorText(e));}}
 if(loading)return <main><p role="status">Проверка входа…</p></main>;
 if(user)return <>{error&&<p role="alert" className="notice error">{error}</p>}{children(user,logout)}</>;
 return <main className="auth-screen"><section className="card"><p className="eyebrow">RESERVATION</p><h1>{register?'Создать аккаунт':'Добро пожаловать'}</h1>
 <p className="muted">Войдите, чтобы управлять своими бронированиями.</p>
 <form onSubmit={submit}><label>Логин<input name="username" required pattern="[a-zA-Z0-9_-]{3,64}" autoComplete="username"/></label>
 <label>Пароль<input name="password" type="password" required minLength={register?12:undefined} maxLength={64} autoComplete={register?'new-password':'current-password'}/></label>
 {register&&<p className="muted">Логин: латинские буквы, цифры, дефис или подчёркивание. Пароль: от 12 символов.</p>}
 {error&&<p role="alert" className="notice error">{error}</p>}
 <button className="primary" disabled={busy}>{busy?'Подождите…':register?'Зарегистрироваться':'Войти'}</button></form>
 <button className="auth-toggle" disabled={busy} onClick={()=>{setRegister(!register);setError('');}}>{register?'Уже есть аккаунт':'Создать аккаунт'}</button>
 </section></main>;
}

