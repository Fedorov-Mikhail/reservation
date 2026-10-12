import {render,screen,fireEvent,waitFor} from '@testing-library/react';
import {test,expect,vi,afterEach} from 'vitest';
import ResourceDelete from './ResourceDelete';
const resource={id:'r1',name:'Переговорная'};
afterEach(()=>vi.unstubAllGlobals());
test('resource deletion requires confirmation and handles 204 without JSON',async()=>{
 const fetcher=vi.fn(async(url:string)=>url.endsWith('/csrf')?Response.json({token:'x'}):new Response(null,{status:204}));vi.stubGlobal('fetch',fetcher);const deleted=vi.fn();
 render(<ResourceDelete resource={resource} onDeleted={deleted}/>);
 fireEvent.click(screen.getByRole('button',{name:'Удалить ресурс'}));expect(fetcher).not.toHaveBeenCalled();
 fireEvent.click(screen.getByRole('button',{name:'Оставить ресурс'}));expect(deleted).not.toHaveBeenCalled();
 fireEvent.click(screen.getByRole('button',{name:'Удалить ресурс'}));fireEvent.click(screen.getByRole('button',{name:'Да, удалить ресурс'}));
 await waitFor(()=>expect(deleted).toHaveBeenCalledTimes(1));expect(fetcher).toHaveBeenCalledWith('/api/v1/resources/r1',expect.objectContaining({method:'DELETE'}));
});
test('resource history conflict leaves resource selected and explains refusal',async()=>{
 vi.stubGlobal('fetch',vi.fn(async(url:string)=>url.endsWith('/csrf')?Response.json({token:'x'}):Response.json({code:'RESOURCE_IN_USE'},{status:409})));const deleted=vi.fn();
 render(<ResourceDelete resource={resource} onDeleted={deleted}/>);fireEvent.click(screen.getByRole('button',{name:'Удалить ресурс'}));fireEvent.click(screen.getByRole('button',{name:'Да, удалить ресурс'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('у него есть брони или заявки');expect(deleted).not.toHaveBeenCalled();
});
