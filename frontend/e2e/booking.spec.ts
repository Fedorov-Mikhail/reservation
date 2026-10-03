import { test, expect } from '@playwright/test';
test('real API: create resource, book, reject duplicate, cancel, mobile layout', async ({ page, request }) => {
  const errors: string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/');
  await page.getByText('＋ Новый ресурс', {exact:true}).click();
  const name='Browser test '+Date.now();
  await page.getByLabel('Название',{exact:true}).fill(name);
  await page.getByLabel('Расположение',{exact:true}).fill('Демонстрационная комната');
  await page.getByLabel('Описание',{exact:true}).fill('Создано браузерной проверкой. Бронь отменяется после теста.');
  await page.getByRole('button',{name:'Создать ресурс',exact:true}).click();
  await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();
  let createdId: string | undefined;
  try {
    const createdPromise=page.waitForResponse(r=>r.request().method()==='POST' && r.url().endsWith('/bookings'));
    await page.getByRole('button',{name:'Забронировать',exact:true}).click();
    const created=await createdPromise;
    expect(created.status()).toBe(201);
    createdId=(await created.json()).id;
    await expect(page.getByText('Бронь создана.',{exact:true})).toBeVisible();
    await expect(page.getByText('Подтверждена',{exact:true})).toBeVisible();
    await expect(page.locator('.slot')).toHaveCount(2);
    const duplicate=page.waitForResponse(r=>r.request().method()==='POST' && r.url().endsWith('/bookings'));
    await page.getByRole('button',{name:'Забронировать',exact:true}).click();
    expect((await duplicate).status()).toBe(409);
    await expect(page.getByRole('alert')).toContainText('Это время уже занято');
    await page.getByRole('button',{name:'Отменить бронь',exact:true}).click();
    await page.getByRole('button',{name:'Да, отменить',exact:true}).click();
    await expect(page.getByText('Бронь отменена.',{exact:true})).toBeVisible();
    await expect(page.getByText('Отменена',{exact:true})).toBeVisible();
    await expect(page.locator('.slot')).toHaveCount(1);
    await page.screenshot({path:'test-results/desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    await expect(page.getByRole('button',{name:'Забронировать',exact:true})).toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.screenshot({path:'test-results/mobile.png',fullPage:true});
    expect(errors).toEqual([]);
  } finally {
    if(createdId) await request.post('/api/v1/bookings/'+createdId+'/cancel');
  }
});


test('rejects a nonexistent local time during daylight-saving transition', async ({browser}) => {
  const context=await browser.newContext({timezoneId:'America/New_York'});
  const page=await context.newPage();
  let posts=0;
  await page.route('**/api/v1/**',async route=>{
    const url=route.request().url();
    if(route.request().method()==='POST') posts++;
    const json=url.includes('/availability')?{intervals:[]}:url.includes('/bookings')?{items:[],totalElements:0}:{items:[{id:'dst-room',name:'DST room'}],totalElements:1};
    await route.fulfill({json});
  });
  try {
    await page.goto('http://127.0.0.1:5173');
    await page.getByRole('button',{name:/DST room/}).click();
    const now=new Date();
    let year=now.getUTCFullYear();
    function transition(y:number) {const first=new Date(Date.UTC(y,2,1));return 8+(7-first.getUTCDay())%7;}
    if(new Date(Date.UTC(year,2,transition(year)))<=now) year++;
    const day=year+'-03-'+String(transition(year)).padStart(2,'0');
    await page.getByLabel('Начало брони').fill(day+'T02:30');
    await page.getByLabel('Конец брони').fill(day+'T04:00');
    await page.getByRole('button',{name:'Забронировать',exact:true}).click();
    await expect(page.getByRole('alert')).toContainText('не существует');
    expect(posts).toBe(0);
  } finally {await context.close();}
});
