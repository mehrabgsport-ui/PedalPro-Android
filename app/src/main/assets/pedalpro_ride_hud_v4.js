(function(){
'use strict';
if(window.__PEDALPRO_RIDE_HUD_4__){try{window.__PEDALPRO_RIDE_HUD_4__.refresh();}catch(e){}return;}
var ROOT='pp-hud4',STORE='pedalpro_ride_hud_v4',POS='pedalpro_ride_hud_pos_v4';
var settings=read(STORE,{top:true,start:true,speed:true,bottom:true});
var positions=read(POS,{});
var root=null,panel=null,startedAt=0,lastPoint=null,totalMeters=0,altitude=0,speedKmh=0,samples=[];
function read(k,d){try{var x=localStorage.getItem(k);return x?Object.assign({},d,JSON.parse(x)):Object.assign({},d);}catch(e){return Object.assign({},d);}}
function save(){try{localStorage.setItem(STORE,JSON.stringify(settings));localStorage.setItem(POS,JSON.stringify(positions));}catch(e){}}
function norm(x){return String(x||'').replace(/\s+/g,' ').trim();}
function all(s,b){try{return Array.prototype.slice.call((b||document).querySelectorAll(s));}catch(e){return[];}}
function ours(el){return !!(el&&el.closest&&el.closest('#'+ROOT));}
function find(labels){
 var c=all('button,a,[role="button"],.btn,.button,[onclick]');
 for(var i=0;i<c.length;i++){var el=c[i];if(ours(el))continue;var t=norm(el.innerText||el.textContent);if(!t)continue;
  for(var j=0;j<labels.length;j++){if(t===labels[j]||t.indexOf(labels[j])>=0)return el;}
 }
 return null;
}
function clickOld(labels){var el=find(labels);if(!el)return false;try{el.click();return true;}catch(e){return false;}}
function hideOldButton(labels){var el=find(labels);if(!el)return;el.dataset.ppHudHidden='1';el.style.setProperty('display','none','important');}
function smallest(words){
 var best=null,area=1e30;
 all('div,section,aside').forEach(function(el){if(ours(el))return;var t=norm(el.innerText);if(!t)return;
  for(var i=0;i<words.length;i++)if(t.indexOf(words[i])<0)return;
  var r=el.getBoundingClientRect();if(r.width<80||r.height<40||r.width>innerWidth*.98||r.height>innerHeight*.55)return;
  var a=r.width*r.height;if(a<area){best=el;area=a;}
 });
 return best;
}
function hideOriginal(){
 [['شروع برنامه'],['مرکز روی من'],['نمایش کامل مسیر'],['توقف ثبت'],['توقف'],['پایان'],['بازگشت به حالت عادی']].forEach(hideOldButton);
 var s=smallest(['سرعت دوچرخه','km/h']);if(s){var r=s.getBoundingClientRect();if(r.width<innerWidth*.56&&r.height<innerHeight*.35)s.style.setProperty('display','none','important');}
 var b=smallest(['ارتفاع','میانگین','مسافت','زمان']);if(b){var q=b.getBoundingClientRect();if(q.width>innerWidth*.55&&q.height<innerHeight*.42)b.style.setProperty('display','none','important');}
}
function row(k,label){return '<label class="pp-row"><span>'+label+'</span><span class="pp-switch"><input type="checkbox" data-setting="'+k+'" '+(settings[k]?'checked':'')+'><span></span></span></label>';}
function html(){
 return [
 '<div class="pp-top pp-ui" id="pp-top">',
 '<button class="pp-chip live"><span class="pp-live-dot"></span><span>درحال ثبت رکورد زنده</span><span class="pp-grip pp-drag" data-drag="pp-top">⠿</span></button>',
 '<button class="pp-chip end" id="pp-end"><span style="font-size:24px">■</span><span>پایان</span></button>',
 '<button class="pp-chip home" id="pp-home"><span style="font-size:25px">⌂</span><span>برگشت به خانه</span></button>',
 '<button class="pp-chip eco" id="pp-eco"><span style="font-size:24px">❧</span><span>ورود به حالت کم مصرف</span></button>',
 '</div>',
 '<button class="pp-start pp-ui" id="pp-start"><span style="font-size:25px">▣</span><span>شروع برنامه</span><span class="pp-grip pp-drag" data-drag="pp-start">⠿</span></button>',
 '<section class="pp-speed pp-ui" id="pp-speed"><div class="pp-speed-head"><div class="pp-speed-title"><span class="pp-speed-ico">◴</span><span>سرعت دوچرخه</span></div><span class="pp-grip pp-drag" data-drag="pp-speed">⠿</span></div><div class="pp-speed-value" id="pp-speed-value">0</div><div class="pp-speed-unit">km/h</div></section>',
 '<button class="pp-edit pp-ui" id="pp-edit"><span style="font-size:25px;color:#50bfff">✎</span><span>ویرایش صفحه</span><span class="pp-grip pp-drag" data-drag="pp-edit">⠿</span></button>',
 '<section class="pp-bottom pp-ui" id="pp-bottom"><div class="pp-bottom-handle pp-drag" data-drag="pp-bottom"></div>',
 '<div class="pp-stat elev"><div class="pp-stat-ico">▲</div><div class="lab">ارتفاع</div><div class="num" id="pp-alt">0</div><div class="unit">m</div><div class="spark"></div></div>',
 '<div class="pp-stat avg"><div class="pp-stat-ico">◴</div><div class="lab">میانگین سرعت</div><div class="num" id="pp-avg">0.0</div><div class="unit">km/h</div><div class="spark"></div></div>',
 '<div class="pp-stat dist"><div class="pp-stat-ico">▰</div><div class="lab">مسافت</div><div class="num" id="pp-dist">0.00</div><div class="unit">km</div><div class="spark"></div></div>',
 '<div class="pp-stat time"><div class="pp-stat-ico">◷</div><div class="lab">زمان</div><div class="num" id="pp-time">00:00:00</div><div class="unit">مدت رکاب زدن</div><div class="spark"></div></div>',
 '</section>',
 '<div class="pp-settings" id="pp-settings"><div class="pp-sheet"><div class="pp-title">ویرایش صفحه نقشه</div>',
 row('top','نوار بالای نقشه'),row('start','دکمه شروع برنامه'),row('speed','کارت سرعت دوچرخه'),row('bottom','پنل اطلاعات پایین'),
 '<div class="pp-actions"><button class="pp-action gray" id="pp-reset">بازنشانی چیدمان</button><button class="pp-action" id="pp-close">تأیید و بستن</button></div></div></div>',
 '<div class="pp-toast" id="pp-toast"></div>'
 ].join('');
}
function toast(m){var e=document.getElementById('pp-toast');if(!e)return;e.textContent=m;e.classList.add('show');clearTimeout(e._t);e._t=setTimeout(function(){e.classList.remove('show');},2100);}
function visible(){
 var map={top:'pp-top',start:'pp-start',speed:'pp-speed',bottom:'pp-bottom'};
 Object.keys(map).forEach(function(k){var e=document.getElementById(map[k]);if(e)e.style.display=settings[k]?'':'none';});
}
function applyPos(id){
 var p=positions[id],e=document.getElementById(id);if(!p||!e)return;
 e.style.left=Math.max(0,Math.min(innerWidth-40,p.x))+'px';e.style.top=Math.max(0,Math.min(innerHeight-40,p.y))+'px';
 e.style.right='auto';e.style.bottom='auto';if(p.w)e.style.width=Math.min(innerWidth,p.w)+'px';
}
function drag(h){
 var id=h.dataset.drag,e=document.getElementById(id);if(!e)return;
 var pid=null,sx=0,sy=0,ox=0,oy=0;
 h.addEventListener('pointerdown',function(v){if(v.button!=null&&v.button!==0)return;pid=v.pointerId;var r=e.getBoundingClientRect();sx=v.clientX;sy=v.clientY;ox=r.left;oy=r.top;e.style.left=r.left+'px';e.style.top=r.top+'px';e.style.width=r.width+'px';e.style.right='auto';e.style.bottom='auto';try{h.setPointerCapture(pid);}catch(x){}v.preventDefault();v.stopPropagation();});
 h.addEventListener('pointermove',function(v){if(pid!==v.pointerId)return;var r=e.getBoundingClientRect();var x=Math.max(0,Math.min(innerWidth-r.width,ox+v.clientX-sx));var y=Math.max(0,Math.min(innerHeight-r.height,oy+v.clientY-sy));e.style.left=x+'px';e.style.top=y+'px';v.preventDefault();});
 function end(v){if(pid!==v.pointerId)return;var r=e.getBoundingClientRect();positions[id]={x:r.left,y:r.top,w:r.width};save();pid=null;v.preventDefault();}
 h.addEventListener('pointerup',end);h.addEventListener('pointercancel',end);
}
function dist(a,b){var R=6371000,p1=a.lat*Math.PI/180,p2=b.lat*Math.PI/180,dp=(b.lat-a.lat)*Math.PI/180,dl=(b.lng-a.lng)*Math.PI/180,h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return 2*R*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
var nativeElapsedMs=0,nativePaused=false;
function update(payload){
 var p=payload;try{if(typeof p==='string')p=JSON.parse(p);}catch(e){return;}if(!p||typeof p!=='object')return;
 var now=Date.now(),lat=Number(p.lat),lng=Number(p.lng),sp=Number(p.speed_mps),sk=Number(p.speed_kmh);
 nativePaused=!!p.paused;
 if(nativePaused)speedKmh=0;else if(Number.isFinite(sp))speedKmh=Math.max(0,sp*3.6);else if(Number.isFinite(sk))speedKmh=Math.max(0,sk);
 var al=Number(p.altitude);if(Number.isFinite(al))altitude=al;if(!startedAt)startedAt=now;
 var dm=Number(p.distance_m);if(Number.isFinite(dm)&&dm>=0){totalMeters=dm;}else if(Number.isFinite(lat)&&Number.isFinite(lng)){
   var cur={lat:lat,lng:lng,t:Number(p.timestamp)||now};
   if(lastPoint){var d=dist(lastPoint,cur),dt=Math.max(0,(cur.t-lastPoint.t)/1000);if(d<90&&dt<20&&speedKmh<130)totalMeters+=d;}
   lastPoint=cur;
 }
 var em=Number(p.elapsed_ms);if(Number.isFinite(em)&&em>=0)nativeElapsedMs=em;
 if(speedKmh>=0&&speedKmh<120){samples.push(speedKmh);if(samples.length>900)samples.shift();}
 render();
}
function render(){
 var e=document.getElementById('pp-speed-value');if(e)e.textContent=speedKmh<10?speedKmh.toFixed(1).replace('.0',''):Math.round(speedKmh);
 e=document.getElementById('pp-alt');if(e)e.textContent=Math.round(altitude||0);
 e=document.getElementById('pp-dist');if(e)e.textContent=(totalMeters/1000).toFixed(2);
 e=document.getElementById('pp-avg');if(e){var a=samples.filter(function(x){return x>.8;});var v=a.length?a.reduce(function(x,y){return x+y;},0)/a.length:0;e.textContent=v.toFixed(1);}
 e=document.getElementById('pp-time');if(e){var ms=nativeElapsedMs>0?nativeElapsedMs:(startedAt?Date.now()-startedAt:0);var s=Math.floor(ms/1000),h=Math.floor(s/3600),m=Math.floor((s%3600)/60),q=s%60;e.textContent=[h,m,q].map(function(x){return String(x).padStart(2,'0');}).join(':');}
}
function wrapNative(){
 if(window.__PP_HUD4_WRAPPED__)return;var old=window.PedalProNativeLocation;
 window.PedalProNativeLocation=function(p){try{update(p);}catch(e){}if(typeof old==='function'){try{return old.apply(this,arguments);}catch(x){}}};
 window.__PP_HUD4_WRAPPED__=true;
}
function bind(){
 document.getElementById('pp-end').onclick=function(){if(!clickOld(['پایان','توقف ثبت','توقف']))toast('دکمه پایان فعلی پیدا نشد');};
 document.getElementById('pp-home').onclick=function(){if(!clickOld(['خانه','صفحه اصلی']))location.href='/';};
 document.getElementById('pp-eco').onclick=function(){try{if(window.AndroidBridge&&AndroidBridge.openLowPowerMode){AndroidBridge.openLowPowerMode();return;}}catch(e){}toast('حالت کم‌مصرف در این دستگاه در دسترس نیست');};
 document.getElementById('pp-start').addEventListener('click',function(v){if(v.target.closest('.pp-grip'))return;if(!clickOld(['شروع برنامه']))toast('شروع برنامه در دسترس نیست');});
 document.getElementById('pp-edit').addEventListener('click',function(v){if(v.target.closest('.pp-grip'))return;panel.classList.add('open');});
 document.getElementById('pp-close').onclick=function(){panel.classList.remove('open');};
 panel.addEventListener('click',function(v){if(v.target===panel)panel.classList.remove('open');});
 all('input[data-setting]',panel).forEach(function(i){i.addEventListener('change',function(){settings[i.dataset.setting]=i.checked;save();visible();});});
 document.getElementById('pp-reset').onclick=function(){positions={};save();['pp-top','pp-start','pp-speed','pp-bottom','pp-edit'].forEach(function(id){var e=document.getElementById(id);if(e)e.removeAttribute('style');});visible();toast('چیدمان به حالت اولیه برگشت');};
 all('.pp-drag',root).forEach(drag);
}
function ridePage(){
 if(document.getElementById(ROOT))return true;
 var maps=all('#map,.map,[class*="map"],[id*="map"]');for(var i=0;i<maps.length;i++){var r=maps[i].getBoundingClientRect();if(r.width>innerWidth*.65&&r.height>innerHeight*.45)return true;}
 var t=norm(document.body&&document.body.innerText);return t.indexOf('سرعت دوچرخه')>=0||t.indexOf('شروع برنامه')>=0||t.indexOf('مدت رکاب')>=0;
}
function mount(){
 if(document.getElementById(ROOT))return;root=document.createElement('div');root.id=ROOT;root.innerHTML=html();document.body.appendChild(root);panel=document.getElementById('pp-settings');bind();visible();['pp-top','pp-start','pp-speed','pp-bottom','pp-edit'].forEach(applyPos);hideOriginal();wrapNative();render();
 var ob=new MutationObserver(function(){hideOriginal();wrapNative();});ob.observe(document.body,{childList:true,subtree:true});root._ob=ob;
}
function boot(){if(ridePage())mount();else{var n=0,t=setInterval(function(){n++;if(ridePage()){clearInterval(t);mount();}if(n>40)clearInterval(t);},500);}}
window.__PEDALPRO_RIDE_HUD_4__={refresh:function(){try{hideOriginal();visible();wrapNative();}catch(e){}},openSettings:function(){if(panel)panel.classList.add('open');}};
boot();setInterval(render,1000);
})();