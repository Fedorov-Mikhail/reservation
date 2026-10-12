import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {test,expect,vi,afterEach} from 'vitest';
import App from './App';
afterEach(()=>vi.unstubAllGlobals());
test('merged week availability never shows an empty message beside available slots',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>{
  if(url.endsWith('/permissions'))return Response.json({canManage:false});
  if(url.endsWith('/schedule'))return Response.json({zoneId:'UTC',weekly:{},exceptions:{}});
  if(url.includes('/availability')){const query=new URL(url,'http://localhost').searchParams;return Response.json({intervals:[{startsAt:query.get('from'),endsAt:query.get('to')}]});}
  if(url.includes('/bookings'))return Response.json({items:[],totalElements:0});
  return Response.json({items:[{id:'r1',name:'Week room'}],totalElements:1});
 }));
 render(<App/>);fireEvent.click(await screen.findByRole('button',{name:/Week room/}));
 fireEvent.change(screen.getByLabelText(/Представление/),{target:{value:'week'}});
 await waitFor(()=>expect(document.querySelectorAll('.calendar-day .slot')).toHaveLength(7));
 expect(screen.queryByText('Нет доступных интервалов.')).not.toBeInTheDocument();
});
