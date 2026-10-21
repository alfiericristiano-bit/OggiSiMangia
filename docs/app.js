/* OggiSiMangia... — GitHub Pages interactive preview. No external API, no server sync. */
(function () {
  "use strict";
  const STORAGE = "oggisimangia_web_preview_v1";
  const DEFAULT_SOURCES = [
    ["GialloZafferano","giallozafferano.it"],["Il Cucchiaio d'Argento","cucchiaio.it"],
    ["Fatto in casa da Benedetta","fattoincasadabenedetta.it"],["Misya","misya.info"],
    ["Cookaround","cookaround.com"],["La Cucina Italiana","lacucinaitaliana.it"],
    ["Tavolartegusto","tavolartegusto.it"],["Ricetta.it","ricetta.it"],
    ["Sale&Pepe","salepepe.it"],["RicetteDalMondo","ricettedalmondo.it"],
    ["Agrodolce","agrodolce.it"]
  ].map(function (p) { return { name:p[0], domain:p[1] }; });
  const CATEGORIES = ["Antipasto","Primo","Secondo","Contorno","Piatto unico","Dolce"];
  const INITIAL_RECIPES = [
    {id:"demo-pasta",title:"Pasta alle zucchine",description:"Un primo semplice per tutti i giorni.",category:"Primo",difficulty:"Facile",portions:"2 persone",prepTime:"10 min",cookTime:"15 min",totalTime:"25 min",ingredients:["180 g pasta","2 zucchine","2 cucchiai olio extravergine","Sale"],steps:["Lava le zucchine e tagliale a rondelle.","Scalda l'olio e fai cuocere le zucchine.","Cuoci la pasta e condiscila con le zucchine."],tips:"Aggiungi basilico fresco.",photo:"",assignments:[],demo:true},
    {id:"demo-frittata",title:"Frittata alle erbette",description:"Una cena veloce.",category:"Secondo",difficulty:"Facile",portions:"2 persone",prepTime:"8 min",cookTime:"12 min",totalTime:"20 min",ingredients:["4 uova","50 g spinaci","1 cucchiaio olio","Sale"],steps:["Sbatti le uova con un pizzico di sale.","Salta gli spinaci in padella.","Versa le uova e cuoci da entrambi i lati."],photo:"",assignments:[],demo:true},
    {id:"demo-risotto",title:"Risotto ai funghi",description:"Piatto cremoso per le giornate fresche.",category:"Primo",difficulty:"Media",portions:"4 persone",prepTime:"15 min",cookTime:"20 min",ingredients:["320 g riso","250 g funghi","1 l brodo vegetale","40 g burro"],steps:["Pulisci i funghi e tagliali.","Tosta il riso e aggiungi gradualmente il brodo.","Unisci i funghi e manteca con il burro."],photo:"",assignments:[],demo:true}
  ];
  function copy(value) { return JSON.parse(JSON.stringify(value)); }
  function todayISO() { const d = new Date(); return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0"); }
  function iso(date) { return date.getFullYear()+"-"+String(date.getMonth()+1).padStart(2,"0")+"-"+String(date.getDate()).padStart(2,"0"); }
  function fromISO(value) { return new Date(String(value).slice(0,4)*1,String(value).slice(5,7)*1-1,String(value).slice(8,10)*1); }
  function escape(value) { return String(value == null ? "" : value).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch];}); }
  function slug(value) { return String(value).toLocaleLowerCase("it").normalize("NFD").replace(/[\u0300-\u036f]/g,""); }
  function ingredientMatch(line,requested) {
    const normalized=slug(line).replace(/[^a-z0-9]+/g," ").trim();
    const term=slug(requested).replace(/[^a-z0-9]+/g," ").trim();
    return term!=="" && (" "+normalized+" ").includes(" "+term+" ");
  }
  function formatDate(value, options) { return new Intl.DateTimeFormat("it-IT",options || {weekday:"long",day:"numeric",month:"long"}).format(fromISO(value)); }
  function uid() { return "r-"+Date.now().toString(36)+"-"+Math.random().toString(36).slice(2,8); }
  const fresh = () => ({recipes:copy(INITIAL_RECIPES),sources:copy(DEFAULT_SOURCES),tipsHidden:false,dark:false});
  function load() {
    try {
      const saved=JSON.parse(localStorage.getItem(STORAGE)||"null");
      if (saved && Array.isArray(saved.recipes) && Array.isArray(saved.sources))
        return {recipes:saved.recipes,sources:saved.sources,tipsHidden:!!saved.tipsHidden,dark:!!saved.dark};
    } catch(e) { /* Revert to demo without writing over unreadable saved data. */ }
    return fresh();
  }
  let state=load(), view="calendar", selected=todayISO(), month=fromISO(todayISO());
  let editingId=null,photoDraft="",formIngredients=[""],formSteps=[""];
  let archiveSearch="",archiveCategory="Tutte",archiveIngredientFields=[""];
  let toastTimer=0,pressTimer=0,pressedLong=false,pressPoint=null;
  const app=document.getElementById("app"), dialog=document.getElementById("modal"),body=document.getElementById("modal-body");
  function persist() {
    try { localStorage.setItem(STORAGE,JSON.stringify(state));return true; }
    catch(e) { toast("Memoria del browser piena: riduci le fotografie o esporta il backup.");return false; }
  }
  function toast(msg) {
    const el=document.getElementById("toast");
    el.textContent=msg;el.classList.add("show");
    clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),3300);
  }
  function modal(title,html) {
    document.getElementById("modal-title").textContent=title;body.innerHTML=html;
    if(dialog.open)dialog.close();
    dialog.showModal();
  }
  function closeModal() {if(dialog.open)dialog.close();}
  function button(text,action,cls,extra) { return '<button type="button" class="btn '+(cls||"")+'" data-action="'+action+'" '+(extra||"")+'>'+text+'</button>'; }
  function field(name,title,value,opts) {
    opts=opts||{};
    let input;
    const props='name="'+escape(name)+'" id="f-'+escape(name)+'" '+(opts.required?"required ":"");
    if(opts.textarea) input='<textarea class="textarea" '+props+' rows="'+(opts.rows||3)+'">'+escape(value||"")+'</textarea>';
    else if(opts.select) input='<select class="select" '+props+'>'+opts.select.map(c=>'<option value="'+escape(c)+'" '+(c===(value||"")?"selected":"")+'>'+escape(c)+'</option>').join("")+'</select>';
    else input='<input class="input" type="'+(opts.type||"text")+'" '+props+' value="'+escape(value||"")+'" placeholder="'+escape(opts.placeholder||"")+'">';
    return '<div class="field '+(opts.wide?"wide":"")+'"><label for="f-'+escape(name)+'">'+escape(title)+'</label>'+input+(opts.hint?'<small>'+escape(opts.hint)+'</small>':"")+'</div>';
  }
  function saveAndRender() {persist();render();}
  function setView(next) {
    view=next;editingId=null;
    render();
    document.getElementById("app").scrollIntoView({behavior:"smooth",block:"start"});
  }
  function render() {
    document.body.classList.toggle("dark",state.dark);
    document.querySelectorAll("[data-view]").forEach(btn=>btn.classList.toggle("active",btn.dataset.view===view));
    document.getElementById("today-caption").textContent=formatDate(todayISO(),{weekday:"long",day:"numeric",month:"long",year:"numeric"});
    if(view==="calendar") renderCalendar();
    else if(view==="archive") renderArchive();
    else if(view==="editor") renderEditor();
    else if(view==="detail") renderDetail();
    else if(view==="sources") renderSources();
    else renderAbout();
  }
  function assignmentsFor(day,meal) {
    const out=[];
    for(const recipe of state.recipes)
      for(const entry of (recipe.assignments||[]))
        if(entry.date===day && (!meal || entry.meal===meal || entry.meal==="Entrambi"))
          out.push({recipe:recipe,assignment:entry});
    return out;
  }
  function renderCalendar() {
    const y=month.getFullYear(),m=month.getMonth(),first=new Date(y,m,1);
    const number=new Date(y,m+1,0).getDate(),leading=(first.getDay()+6)%7;
    let cells="",days=new Set(),lunch=0,dinner=0;
    for(let i=0;i<leading;i++)cells+='<span class="day blank"></span>';
    for(let n=1;n<=number;n++){
      const date=iso(new Date(y,m,n)),assignments=assignmentsFor(date);
      const hasLunch=assignments.some(x=>x.assignment.meal==="Pranzo"||x.assignment.meal==="Entrambi");
      const hasDinner=assignments.some(x=>x.assignment.meal==="Cena"||x.assignment.meal==="Entrambi");
      if(hasLunch)lunch++;if(hasDinner)dinner++;if(assignments.length)days.add(date);
      cells+='<button type="button" class="day '+(date===selected?"selected ":"")+(date===todayISO()?"today":"")+'" data-action="select-day" data-date="'+date+'" title="'+escape(formatDate(date))+' — tocco: seleziona, tieni premuto oltre 2 secondi: aggiungi"><span class="day-number">'+n+'</span><span class="dots">'+(hasLunch?'<i class="dot"></i>':"")+(hasDinner?'<i class="dot dinner"></i>':"")+'</span></button>';
    }
    function mealCard(meal) {
      const items=assignmentsFor(selected,meal).sort((a,b)=>Number(a.assignment.alternative)-Number(b.assignment.alternative));
      return '<div class="meal-section"><div class="meal-title"><h3><span class="emoji">'+(meal==="Pranzo"?"☀️":"🌙")+'</span>'+meal+'</h3>'+
        button("＋ Aggiungi","add-to-day","tiny", 'data-meal="'+meal+'"')+'</div>'+
        (items.length?items.map(function(x) {
          const a=x.assignment, r=x.recipe;
          return '<div class="meal-entry"><strong>'+escape(r.title)+'</strong><span class="meal-meta">'+escape(r.category||"Piatto")+' · '+(a.alternative?"Alternativa":"Principale")+' · '+escape(r.totalTime||r.cookTime||"")+'</span><div class="actions">'+
            button("Apri","open-recipe","tiny", 'data-id="'+escape(r.id)+'"')+
            button("Modifica","edit-plan","tiny", 'data-id="'+escape(r.id)+'" data-aid="'+escape(a.id)+'"')+
            button("Clona","clone-plan","tiny", 'data-id="'+escape(r.id)+'" data-aid="'+escape(a.id)+'"')+
            button("✕","delete-plan","tiny danger", 'data-id="'+escape(r.id)+'" data-aid="'+escape(a.id)+'"')+
            '</div></div>';
        }).join(""):'<div class="empty">Nessuna ricetta assegnata a '+meal.toLowerCase()+'.</div>')+'</div>';
    }
    app.innerHTML=
      '<section class="headline"><div><p class="kicker">Calendario dei pasti</p><h1>Organizza i tuoi pasti</h1><p class="subtitle">Tocca un giorno per selezionarlo; tienilo premuto per più di 2 secondi per aggiungere un piatto.</p></div>'+
      '<div class="head-actions">'+button("ⓘ Come funziona","show-help")+button("＋ Nuova ricetta","new-recipe","primary")+'</div></section>'+
      '<div class="calendar-layout"><section class="card"><div class="card-head"><h2>Il tuo calendario</h2><div class="month-picker">'+
      button("‹","prev-month","icon")+'<strong>'+escape(new Intl.DateTimeFormat("it-IT",{month:"long",year:"numeric"}).format(first))+'</strong>'+button("›","next-month","icon")+'</div></div>'+
      '<div class="week-grid">'+["L","M","M","G","V","S","D"].map(s=>'<span>'+s+'</span>').join("")+'</div><div class="day-grid" id="calendar-grid">'+cells+'</div>'+
      '<div class="legend"><span><i class="dot"></i> Pranzo</span><span><i class="dot dinner"></i> Cena</span></div>'+
      '<p class="card-hint">💡 Premi per oltre 2 secondi un giorno per aggiungere una ricetta; un tocco breve seleziona il giorno.</p></section>'+
      '<section class="card"><div class="card-head"><h2 class="selected-day-title">'+escape(formatDate(selected))+'</h2>'+
      button("Oggi","go-today","tiny")+'</div>'+mealCard("Pranzo")+mealCard("Cena")+
      '<div class="head-actions">'+button("＋ Crea ricetta per questo giorno","new-for-day","soft")+'</div></section></div>'+
      '<div class="quick-stat"><div class="stat"><strong>'+state.recipes.length+'</strong><span>Ricette nel browser</span></div>'+
      '<div class="stat"><strong>'+days.size+'</strong><span>Giorni pianificati nel mese</span></div>'+
      '<div class="stat"><strong>'+state.sources.length+'</strong><span>Fonti configurate</span></div></div>';
  }
  function renderArchive() {
    const query=slug(archiveSearch.trim()),category=archiveCategory;
    const terms=archiveIngredientFields.map(x=>slug(x.trim())).filter(Boolean);
    const filtered=state.recipes.filter(function(r){
      return (!query||slug(r.title+" "+(r.description||"")).includes(query)) &&
        (category==="Tutte"||r.category===category) &&
        terms.every(function(term){return (r.ingredients||[]).some(i=>ingredientMatch(i,term));});
    });
    app.innerHTML='<section class="headline"><div><p class="kicker">Il tuo archivio</p><h1>Ricettario</h1><p class="subtitle">Cerca tra le ricette salvate in questo browser. Tutti gli ingredienti inseriti sono obbligatori.</p></div>'+
      button("＋ Scrivi ricetta","new-recipe","primary")+'</section>'+
      '<section class="card" style="margin-bottom:19px"><form id="archive-form"><div class="filters">'+
      field("search","Nome del piatto",archiveSearch,{placeholder:"Es. pasta alle zucchine"})+
      field("category","Categoria",archiveCategory,{select:["Tutte"].concat(CATEGORIES)})+'</div>'+
      '<div class="field"><label>Ingredienti richiesti — tutti devono essere presenti</label><div id="filter-ingredient-rows" class="select-ingredients">'+archiveIngredientFields.map((x,i)=>ingredientFilterLine(x,i)).join("")+'</div></div>'+
      '<div class="head-actions" style="margin-top:12px">'+button("＋ Aggiungi ingrediente","add-filter-ingredient","soft")+
      '<button type="submit" class="btn primary">🔎 Cerca</button>'+button("Azzera filtri","reset-filters")+'</div></form></section>'+
      '<div class="card-head"><h2>'+filtered.length+' '+(filtered.length===1?"ricetta":"ricette")+'</h2><span class="muted">Solo piatti, non guide generiche</span></div>'+
      (filtered.length?'<div class="recipe-grid">'+filtered.map(recipeTile).join("")+'</div>':
        '<section class="card empty">Non ci sono ricette che contengono <strong>tutti</strong> gli ingredienti richiesti. Prova a modificare i filtri oppure scrivi una nuova ricetta.</section>');
  }
  function ingredientFilterLine(value,index) {
    return '<div class="list-line"><span class="number">'+(index+1)+'.</span><input class="input" data-filter-ingredient="'+index+'" aria-label="Ingrediente '+(index+1)+'" value="'+escape(value)+'" placeholder="Es. zucchine">'+
      button("✕","remove-filter-ingredient","tiny",'data-index="'+index+'"')+'</div>';
  }
  function recipeTile(r) {
    return '<article class="card recipe-card">'+
      (r.photo?'<img src="'+escape(r.photo)+'" alt="Foto di '+escape(r.title)+'">':'<div class="recipe-placeholder" aria-hidden="true">🍽️</div>')+
      '<div class="body"><span class="chip">'+escape(r.category||"Piatto")+(r.demo?" · Esempio":"")+'</span><h3>'+escape(r.title)+'</h3>'+
      '<p>'+escape(r.description||"La tua ricetta personale")+'</p>'+
      '<p>⏱ '+escape(r.totalTime||r.cookTime||"Da definire")+' · '+escape(r.difficulty||"Facile")+'</p>'+
      button("Apri ricetta →","open-recipe","soft",'data-id="'+escape(r.id)+'"')+'</div></article>';
  }
  function renderEditor() {
    const r=state.recipes.find(x=>x.id===editingId)||{};
    const edit=!!r.id;
    app.innerHTML='<section class="headline"><div><p class="kicker">Ricettario personale</p><h1>'+(edit?"Modifica la tua ricetta":"Scrivi una nuova ricetta")+'</h1>'+
      '<p class="subtitle">Inserisci i dati del tuo piatto. I campi contrassegnati da * sono obbligatori.</p></div>'+
      button("← Torna al ricettario","back-archive")+'</section>'+
      '<form id="recipe-form" class="card recipe-form"><div class="form-grid">'+
      field("title","Nome del piatto *",r.title,{required:true,wide:true})+
      field("description","Descrizione",r.description,{textarea:true,wide:true})+
      field("category","Portata",r.category||"Primo",{select:CATEGORIES})+
      field("difficulty","Difficoltà",r.difficulty||"Facile",{select:["Facile","Media","Difficile"]})+
      field("cuisine","Cucina / provenienza",r.cuisine)+field("portions","Dosi / numero persone",r.portions)+
      field("author","Autore",r.author)+field("cost","Costo indicativo",r.cost)+
      field("tags","Etichette",r.tags)+field("allergens","Allergeni",r.allergens)+'</div>'+
      '<div class="form-section"><h2>Ingredienti *</h2><p class="subtitle">Un ingrediente per riga, con quantità e unità di misura.</p>'+
      '<div id="ingredients-list" class="inline-list" style="margin:15px 0">'+makeListRows("ingredients",formIngredients)+'</div>'+
      button("＋ Aggiungi ingrediente","add-row","soft",'data-list="ingredients"')+'</div>'+
      '<div class="form-section"><h2>Preparazione · passaggi numerati *</h2><p class="subtitle">Ogni passaggio avrà un numero progressivo.</p>'+
      '<div id="steps-list" class="inline-list" style="margin:15px 0">'+makeListRows("steps",formSteps)+'</div>'+
      button("＋ Aggiungi passaggio","add-row","soft",'data-list="steps"')+'</div>'+
      '<div class="form-section"><h2>Tempi e cottura</h2><div class="form-grid">'+
      field("prepTime","Preparazione",r.prepTime,{placeholder:"Es. 15 min"})+
      field("cookTime","Cottura",r.cookTime,{placeholder:"Es. 25 min"})+
      field("restTime","Riposo / lievitazione",r.restTime)+
      field("totalTime","Tempo totale",r.totalTime)+
      field("temperature","Temperatura",r.temperature,{placeholder:"Es. 180 °C"})+
      field("equipment","Attrezzatura",r.equipment)+'</div></div>'+
      '<div class="form-section"><h2>Consigli e altre informazioni</h2><div class="form-grid">'+
      field("tips","Consigli e accorgimenti",r.tips,{textarea:true})+
      field("variations","Varianti / sostituzioni",r.variations,{textarea:true})+
      field("storage","Conservazione",r.storage,{textarea:true})+
      field("nutrition","Valori nutrizionali",r.nutrition,{textarea:true})+
      field("notes","Note personali",r.notes,{textarea:true,wide:true})+'</div></div>'+
      '<div class="form-section"><h2>Fotografia del piatto</h2><p class="subtitle">Viene compressa a massimo 960 pixel e salvata localmente nel browser.</p>'+
      '<div id="editor-photo-area" style="margin:14px 0">'+photoElement(photoDraft)+'</div>'+
      '<div class="head-actions">'+
      '<label class="btn">🖼️ Scegli foto<input type="file" id="gallery-input" accept="image/*" hidden></label>'+
      '<label class="btn">📷 Fotocamera<input type="file" id="camera-input" accept="image/*" capture="environment" hidden></label>'+
      button("✕ Rimuovi foto","clear-photo","danger")+'</div></div>'+
      '<div class="form-section"><h2>Calendario</h2><label class="field" style="display:flex;flex-direction:row;align-items:center;gap:9px">'+
      '<input type="checkbox" id="plan-on-save" '+(r.planOnSave?"checked":"")+'> Aggiungi anche al calendario</label>'+
      '<div class="form-grid" style="margin-top:15px">'+
      field("planDate","Giorno",r.planDate||selected,{type:"date"})+
      field("meal","Pasto",r.meal||"Pranzo",{select:["Pranzo","Cena","Entrambi"]})+
      field("alternative","Scelta",r.alternative||"Principale",{select:["Principale","Alternativa"]})+
      '</div></div>'+
      '<div class="head-actions" style="margin-top:22px"><button type="submit" class="btn primary">✓ Salva ricetta</button>'+
      button("Annulla","back-archive")+'</div></form>';
  }
  function photoElement(data) {
    return data?'<img src="'+escape(data)+'" alt="Anteprima fotografica della ricetta" class="photo-large" style="max-height:225px;max-width:350px">':
      '<div class="empty">Nessuna fotografia selezionata</div>';
  }
  function makeListRows(type,entries) {
    return entries.map(function(value,index) {
      return '<div class="list-line"><span class="number">'+(index+1)+'.</span>'+
        '<input type="text" class="input" data-entry="'+type+'" data-index="'+index+'" value="'+escape(value)+'" aria-label="'+(type==="steps"?"Passaggio ":"Ingrediente ")+(index+1)+'" placeholder="'+(type==="steps"?"Descrivi il passaggio":"Es. 200 g di farina")+'">'+
        (type==="steps"?button("↑","move-row","tiny",'data-list="'+type+'" data-index="'+index+'" data-dir="-1"')+
          button("↓","move-row","tiny",'data-list="'+type+'" data-index="'+index+'" data-dir="1"'):"")+
        button("✕","remove-row","tiny danger",'data-list="'+type+'" data-index="'+index+'"')+'</div>';
    }).join("");
  }
  function readRows(type) {return Array.from(document.querySelectorAll('[data-entry="'+type+'"]')).map(x=>x.value);}
  function refreshRows(type,items) {
    const target=document.getElementById(type+"-list");
    target.innerHTML=makeListRows(type,items);
    if(type==="ingredients")formIngredients=items;
    else formSteps=items;
  }
  function beginEditor(recipeId,scheduled) {
    editingId=recipeId||null;
    const original=state.recipes.find(r=>r.id===editingId);
    photoDraft=original?.photo||"";
    formIngredients=original?.ingredients?.slice()||[""];
    formSteps=original?.steps?.slice()||[""];
    if(!formIngredients.length)formIngredients=[""];
    if(!formSteps.length)formSteps=[""];
    view="editor";render();
    if(scheduled)document.getElementById("plan-on-save").checked=true;
  }
  function renderDetail() {
    const r=state.recipes.find(x=>x.id===editingId);
    if(!r){setView("archive");return;}
    function info(label,value){return value?'<div class="item">'+escape(label)+'<strong>'+escape(value)+'</strong></div>':"";}
    function paragraph(label,value){return value?'<div class="form-section"><h2>'+escape(label)+'</h2><p>'+escape(value).replace(/\n/g,"<br>")+'</p></div>':"";}
    app.innerHTML='<section class="headline"><div><p class="kicker">Scheda ricetta</p><h1>'+escape(r.title)+'</h1><p class="subtitle">'+escape(r.description||"Dal tuo ricettario personale")+'</p></div><div class="head-actions">'+
      button("✎ Modifica","edit-recipe","soft",'data-id="'+escape(r.id)+'"')+
      button("📅 Pianifica","plan-recipe","primary",'data-id="'+escape(r.id)+'"')+
      button("🖨 Stampa","print-recipe")+'</div></section>'+
      '<div class="details"><article class="card">'+
      (r.photo?'<img class="photo-large" src="'+escape(r.photo)+'" alt="Foto di '+escape(r.title)+'">':"")+
      '<div class="info-grid" style="margin:15px 0">'+info("Portata",r.category)+info("Difficoltà",r.difficulty)+info("Porzioni",r.portions)+
      info("Preparazione",r.prepTime)+info("Cottura",r.cookTime)+info("Riposo",r.restTime)+info("Totale",r.totalTime)+info("Temperatura",r.temperature)+info("Cucina",r.cuisine)+'</div>'+
      '<div class="form-section"><h2>Ingredienti</h2><ul class="recipe-lines">'+(r.ingredients||[]).map(x=>'<li>'+escape(x)+'</li>').join("")+'</ul></div>'+
      '<div class="form-section"><h2>Preparazione</h2><ol class="recipe-lines">'+(r.steps||[]).map((x,i)=>'<li class="step-line"><span class="step-number">'+(i+1)+'</span><span>'+escape(x)+'</span></li>').join("")+'</ol></div>'+
      paragraph("Consigli",r.tips)+paragraph("Varianti",r.variations)+paragraph("Conservazione",r.storage)+
      paragraph("Note personali",r.notes)+paragraph("Attrezzatura",r.equipment)+paragraph("Allergeni",r.allergens)+
      paragraph("Valori nutrizionali",r.nutrition)+paragraph("Etichette",r.tags)+
      '<div class="head-actions" style="margin-top:22px">'+button("Elimina ricetta","delete-recipe","danger",'data-id="'+escape(r.id)+'"')+'</div></article>'+
      '<aside class="card"><h2>Giorni pianificati</h2><p class="subtitle">Assegnazioni presenti nel calendario web.</p>'+
      ((r.assignments||[]).length?r.assignments.map(a=>'<div class="meal-entry"><strong>'+escape(formatDate(a.date))+'</strong><span class="meal-meta">'+escape(a.meal)+" · "+(a.alternative?"Alternativa":"Principale")+'</span></div>').join(""):'<div class="empty" style="margin-top:17px">Nessun giorno assegnato.</div>')+
      '<div style="margin-top:14px">'+button("＋ Aggiungi al calendario","plan-recipe","soft",'data-id="'+escape(r.id)+'"')+'</div></aside></div>';
  }
  function renderSources() {
    app.innerHTML='<section class="headline"><div><p class="kicker">Preferenze di ricerca</p><h1>Fonti delle ricette</h1>'+
      '<p class="subtitle">Puoi ordinare, aggiungere ed eliminare i siti da consultare nell’app Android.</p></div>'+
      button("＋ Aggiungi sito","add-source","primary")+'</section>'+
      '<section class="card"><div class="notice warn" style="margin-bottom:16px"><strong>Solo anteprima:</strong> la configurazione qui serve a provare i comandi nel browser. Non modifica le fonti presenti nell’APK e non interroga i siti esterni.</div>'+
      '<div class="source-list">'+(state.sources.length?state.sources.map(function(s,i) {
        return '<div class="source-item"><span class="number">'+(i+1)+'°</span><div class="grow"><strong>'+escape(s.name)+'</strong><small>'+escape(s.domain)+'</small></div>'+
          button("↑","move-source","tiny",'data-index="'+i+'" data-dir="-1" '+(i===0?"disabled":""))+
          button("↓","move-source","tiny",'data-index="'+i+'" data-dir="1" '+(i===state.sources.length-1?"disabled":""))+
          button("Elimina","remove-source","tiny danger",'data-index="'+i+'"')+'</div>';
      }).join(""):'<div class="empty">Nessun sito configurato. Puoi aggiungerne uno oppure ripristinare quelli iniziali.</div>')+'</div>'+
      '<div style="margin-top:19px" class="head-actions">'+button("↻ Ripristina elenco iniziale","restore-sources")+
      button("＋ Aggiungi un sito","add-source","soft")+'</div></section>';
  }
  function renderAbout() {
    app.innerHTML='<section class="headline"><div><p class="kicker">Informazioni</p><h1>Anteprima web di OggiSiMangia...</h1>'+
      '<p class="subtitle">Una versione dimostrativa in HTML, CSS e JavaScript, aggiornata dal repository GitHub.</p></div></section>'+
      '<section class="card"><h2>Cosa puoi provare qui</h2><p>Calendario mensile, inserimento manuale delle ricette con foto compressa, ricettario, ricerca per tutti gli ingredienti, assegnazioni pranzo/cena, alternative, clonazione e preferenze dei siti.</p>'+
      '<h2 style="margin-top:23px">Differenze rispetto all’APK Android</h2><p>Questo sito non esegue l’APK. Non utilizza SQLite Android né sincronizza i dati del telefono. Le ricette sono salvate nello spazio locale del browser; pulire i dati del browser può eliminarle. La ricerca automatica nelle fonti esterne, le notifiche e la stampa Android non sono riprodotte. Puoi stampare una scheda usando la stampa del browser.</p>'+
      '<div class="notice"><strong>I tuoi dati di prova restano su questo dispositivo.</strong> Non è presente alcun server che raccolga le ricette. Ti consiglio di esportare un backup se vuoi conservarle.</div>'+
      '<div class="head-actions" style="margin-top:20px">'+button("↓ Esporta backup JSON","export-json","primary")+
      '<label class="btn">↑ Importa backup<input id="import-json" type="file" accept=".json,application/json" hidden></label>'+
      button("◐ Tema chiaro / scuro","toggle-theme")+
      button("ⓘ Rivedi guida","show-help")+
      button("Reimposta i dati di prova","reset-data","danger")+'</div>'+
      '<p class="muted" style="font-size:12px;margin-top:18px">Ultima versione Android di riferimento: 1.5.0. Le modifiche del browser non passano automaticamente all’APK.</p></section>';
  }
  function showHelp(force) {
    if(!force&&state.tipsHidden)return;
    modal("Come funziona il calendario",'<p>Tocca una data per selezionarla. <strong>Tieni premuto oltre 2 secondi</strong> per aggiungere una ricetta a quel giorno.</p>'+
      '<p>Puoi assegnare una ricetta a pranzo o cena, come principale oppure alternativa, e clonarla in altre date.</p>'+
      '<label style="display:flex;gap:10px;align-items:center;margin:14px 0"><input type="checkbox" id="hide-help" '+(state.tipsHidden?"checked":"")+
      '> Non mostrare più questo messaggio</label><div class="modal-actions">'+button("Ho capito","accept-help","primary")+'</div>');
  }
  function planner(recipeId,assignmentId,mode,meal) {
    const selectedRecipe=state.recipes.find(r=>r.id===recipeId);
    const assignment=selectedRecipe?.assignments?.find(x=>x.id===assignmentId);
    const isEdit=mode==="edit";
    const defaultMeal=meal||(assignment?.meal)||"Pranzo";
    const item='<div class="field"><label for="plan-recipe">Ricetta</label><select class="select" id="plan-recipe" '+(isEdit?"disabled":"")+'>'+
      state.recipes.map(r=>'<option value="'+escape(r.id)+'" '+(r.id===recipeId?"selected":"")+'>'+escape(r.title)+'</option>').join("")+'</select></div>';
    modal(isEdit?"Modifica l'assegnazione":mode==="clone"?"Clona ricetta su un altro giorno":"Assegna una ricetta",
      (state.recipes.length?item:'<p class="notice warn">Per prima cosa devi salvare almeno una ricetta.</p>')+
      field("date","Giorno",isEdit?assignment.date:mode==="clone"?selected:selected,{type:"date"})+
      field("meal","Pasto",defaultMeal,{select:["Pranzo","Cena","Entrambi"]})+
      field("alternative","Tipo",assignment?.alternative?"Alternativa":"Principale",{select:["Principale","Alternativa"]})+
      '<div class="modal-actions">'+button("Annulla","close-modal")+
      (state.recipes.length?button(isEdit?"Salva modifiche":"Salva assegnazione","save-plan","primary",
      'data-mode="'+mode+'" data-id="'+escape(recipeId||"")+'" data-aid="'+escape(assignmentId||"")+'"'):"")+'</div>');
  }
  function addPlan(recipeId,data,editId) {
    const r=state.recipes.find(x=>x.id===recipeId);
    if(!r){toast("Scegli una ricetta valida.");return false;}
    r.assignments=r.assignments||[];
    if(editId){const a=r.assignments.find(x=>x.id===editId);if(!a)return false;Object.assign(a,data);}
    else r.assignments.push(Object.assign({id:uid()},data));
    selected=data.date;month=fromISO(selected);return persist();
  }
  function sourceDialog() {
    modal("Aggiungi fonte di ricerca",field("name","Nome del sito","",{placeholder:"Es. Ricette preferite"})+
      field("domain","Dominio HTTPS","",{placeholder:"https://www.esempio.it"})+
      '<div class="modal-actions">'+button("Annulla","close-modal")+button("Memorizza","save-source","primary")+'</div>');
  }
  function currentArchiveValues() {
    const q=document.querySelector('#archive-form [name="search"]');
    if(q)archiveSearch=q.value;
    const cat=document.querySelector('#archive-form [name="category"]');
    if(cat)archiveCategory=cat.value;
    archiveIngredientFields=Array.from(document.querySelectorAll("[data-filter-ingredient]")).map(x=>x.value);
  }
  function click(e) {
    const nav=e.target.closest("[data-view]");
    if(nav){setView(nav.dataset.view);return;}
    const b=e.target.closest("[data-action]");if(!b)return;
    const action=b.dataset.action,id=b.dataset.id,aid=b.dataset.aid,idx=Number(b.dataset.index);
    if(action==="close-modal"){closeModal();return;}
    if(action==="show-help"){showHelp(true);return;}
    if(action==="accept-help"){state.tipsHidden=!!document.getElementById("hide-help")?.checked;persist();closeModal();return;}
    if(action==="prev-month"||action==="next-month"){month=new Date(month.getFullYear(),month.getMonth()+(action==="prev-month"?-1:1),1);render();return;}
    if(action==="go-today"){selected=todayISO();month=fromISO(selected);render();return;}
    if(action==="select-day"){
      if(pressedLong){pressedLong=false;return;}
      selected=b.dataset.date;render();return;
    }
    if(action==="new-recipe"){beginEditor(null,false);return;}
    if(action==="new-for-day"){beginEditor(null,true);return;}
    if(action==="back-archive"){setView("archive");return;}
    if(action==="open-recipe"){editingId=id;view="detail";render();return;}
    if(action==="edit-recipe"){beginEditor(id,false);return;}
    if(action==="print-recipe"){window.print();return;}
    if(action==="add-to-day"){planner(null,null,"add",b.dataset.meal);return;}
    if(action==="plan-recipe"){planner(id,null,"add");return;}
    if(action==="edit-plan"||action==="clone-plan"){planner(id,aid,action==="edit-plan"?"edit":"clone");return;}
    if(action==="save-plan"){
      const date=document.getElementById("f-date").value,meal=document.getElementById("f-meal").value;
      const alternative=document.getElementById("f-alternative").value==="Alternativa";
      const recipeId=b.dataset.mode==="edit"?id:document.getElementById("plan-recipe").value;
      if(!/^\d{4}-\d{2}-\d{2}$/.test(date)){toast("Seleziona una data valida.");return;}
      if(addPlan(recipeId,{date,meal,alternative},b.dataset.mode==="edit"?aid:null)){closeModal();view="calendar";render();toast("Assegnazione salvata.");}
      return;
    }
    if(action==="delete-plan"){
      modal("Rimuovi assegnazione",'<p>Rimuovere questa ricetta dal giorno selezionato? La ricetta rimarrà nel ricettario.</p>'+
        '<div class="modal-actions">'+button("Annulla","close-modal")+button("Rimuovi","confirm-delete-plan","danger",'data-id="'+escape(id)+'" data-aid="'+escape(aid)+'"')+'</div>');return;
    }
    if(action==="confirm-delete-plan"){
      const r=state.recipes.find(x=>x.id===id);if(r){r.assignments=r.assignments.filter(x=>x.id!==aid);saveAndRender();}
      closeModal();return;
    }
    if(action==="delete-recipe"){
      modal("Eliminare la ricetta?",'<p>La ricetta e tutte le sue assegnazioni verranno cancellate dall’anteprima web.</p><div class="modal-actions">'+button("Annulla","close-modal")+
        button("Elimina definitivamente","confirm-delete-recipe","danger",'data-id="'+escape(id)+'"')+'</div>');return;
    }
    if(action==="confirm-delete-recipe"){state.recipes=state.recipes.filter(x=>x.id!==id);persist();closeModal();setView("archive");toast("Ricetta eliminata.");return;}
    if(action==="add-row"||action==="remove-row"||action==="move-row"){
      const type=b.dataset.list,lines=readRows(type);let pos=Number(b.dataset.index);
      if(action==="add-row")lines.push("");
      if(action==="remove-row")lines.length>1?lines.splice(pos,1):lines[0]="";
      if(action==="move-row"){const other=pos+Number(b.dataset.dir);if(other>=0&&other<lines.length){[lines[pos],lines[other]]=[lines[other],lines[pos]];pos=other;}}
      refreshRows(type,lines);
      const focus=document.querySelector('[data-entry="'+type+'"][data-index="'+(action==="add-row"?lines.length-1:Math.max(0,Math.min(pos,lines.length-1)))+'"]');
      focus?.focus();return;
    }
    if(action==="clear-photo"){photoDraft="";document.getElementById("editor-photo-area").innerHTML=photoElement(photoDraft);return;}
    if(action==="add-filter-ingredient"){
      currentArchiveValues();
      if(archiveIngredientFields.length>=10){toast("Massimo dieci ingredienti nella ricerca.");return;}
      archiveIngredientFields.push("");render();return;
    }
    if(action==="remove-filter-ingredient"){
      currentArchiveValues();
      archiveIngredientFields.length>1?archiveIngredientFields.splice(idx,1):archiveIngredientFields=[""];
      render();return;
    }
    if(action==="reset-filters"){archiveSearch="";archiveCategory="Tutte";archiveIngredientFields=[""];render();return;}
    if(action==="add-source"){sourceDialog();return;}
    if(action==="move-source"){
      const other=idx+Number(b.dataset.dir);
      if(other<0||other>=state.sources.length)return;
      [state.sources[idx],state.sources[other]]=[state.sources[other],state.sources[idx]];saveAndRender();return;
    }
    if(action==="remove-source"){
      const item=state.sources[idx];if(!item)return;
      modal("Eliminare il sito?",'<p><strong>'+escape(item.name)+'</strong> non comparirà più nell’elenco delle fonti web.</p>'+
        '<div class="modal-actions">'+button("Annulla","close-modal")+button("Elimina sito","confirm-remove-source","danger",'data-index="'+idx+'"')+'</div>');return;
    }
    if(action==="confirm-remove-source"){state.sources.splice(idx,1);persist();closeModal();render();toast("Sito eliminato.");return;}
    if(action==="restore-sources"){
      modal("Ripristinare i siti iniziali?",'<p>Questo sostituirà il tuo elenco web con le 11 fonti predefinite.</p><div class="modal-actions">'+button("Annulla","close-modal")+button("Ripristina","confirm-restore-sources","primary")+'</div>');return;
    }
    if(action==="confirm-restore-sources"){state.sources=copy(DEFAULT_SOURCES);persist();closeModal();render();return;}
    if(action==="save-source"){
      const name=document.getElementById("f-name").value.trim(),raw=document.getElementById("f-domain").value.trim();
      let domain="";
      try {const url=new URL(raw.includes("://")?raw:"https://"+raw);if(url.protocol!=="https:"||url.username||url.password||url.port||url.hostname.split(".").length<2)throw Error();domain=url.hostname.toLowerCase().replace(/^www\./,"");}
      catch(e){toast("Inserisci un dominio HTTPS valido.");return;}
      if(!name){toast("Inserisci il nome del sito.");return;}
      if(state.sources.some(x=>x.domain===domain||x.name.toLowerCase()===name.toLowerCase())){toast("Questo sito o nome è già presente.");return;}
      state.sources.push({name,domain});persist();closeModal();render();toast("Sito aggiunto.");return;
    }
    if(action==="toggle-theme"){state.dark=!state.dark;saveAndRender();return;}
    if(action==="export-json"){
      const data=JSON.stringify({format:"oggisimangia-preview",version:1,recipes:state.recipes,sources:state.sources},null,2);
      const url=URL.createObjectURL(new Blob([data],{type:"application/json"}));
      const a=document.createElement("a");a.href=url;a.download="OggiSiMangia-web-backup.json";document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),5000);return;
    }
    if(action==="reset-data"){
      modal("Ripristinare i dati dimostrativi?",'<p>Le ricette e le preferenze salvate in questo browser saranno cancellate. Esporta prima un backup se vuoi conservarle.</p>'+
        '<div class="modal-actions">'+button("Annulla","close-modal")+button("Cancella dati di prova","confirm-reset","danger")+'</div>');return;
    }
    if(action==="confirm-reset"){state=fresh();persist();closeModal();selected=todayISO();month=fromISO(selected);view="calendar";render();toast("Dati dimostrativi ripristinati.");return;}
  }
  async function compressPhoto(file) {
    if(!file||!file.type.startsWith("image/"))throw Error("Formato non supportato");
    if(file.size>20_000_000)throw Error("Foto troppo grande");
    const objectUrl=URL.createObjectURL(file);
    try {
      const img=await new Promise((ok,no)=>{const image=new Image();image.onload=()=>ok(image);image.onerror=()=>no(new Error("Foto illeggibile"));image.src=objectUrl;});
      const scale=Math.min(1,960/Math.max(img.naturalWidth,img.naturalHeight));
      const canvas=document.createElement("canvas");canvas.width=Math.max(1,Math.round(img.naturalWidth*scale));canvas.height=Math.max(1,Math.round(img.naturalHeight*scale));
      const ctx=canvas.getContext("2d");if(!ctx)throw Error("Compressione non disponibile");
      ctx.fillStyle="#fff";ctx.fillRect(0,0,canvas.width,canvas.height);
      ctx.drawImage(img,0,0,canvas.width,canvas.height);
      return canvas.toDataURL("image/jpeg",.68);
    } finally {URL.revokeObjectURL(objectUrl);}
  }
  async function change(e) {
    if(e.target.id==="gallery-input"||e.target.id==="camera-input"){
      const file=e.target.files?.[0];if(!file)return;
      try {photoDraft=await compressPhoto(file);document.getElementById("editor-photo-area").innerHTML=photoElement(photoDraft);toast("Foto compressa e pronta.");}
      catch(err){toast(err.message||"Impossibile leggere la foto.");}
      return;
    }
    if(e.target.id==="import-json"){
      const file=e.target.files?.[0];if(!file)return;
      try {
        const parsed=JSON.parse(await file.text());
        if(parsed.format!=="oggisimangia-preview"||!Array.isArray(parsed.recipes)||!Array.isArray(parsed.sources)||parsed.recipes.length>2000)throw Error();
        const safeRecipes=parsed.recipes.filter(r=>r&&typeof r.id==="string"&&typeof r.title==="string"&&Array.isArray(r.ingredients)&&Array.isArray(r.steps))
          .map(r=>Object.assign({},r,{photo:/^data:image\/jpeg;base64,/.test(r.photo||"")?r.photo:"",assignments:Array.isArray(r.assignments)?r.assignments:[]}));
        state.recipes=safeRecipes;state.sources=parsed.sources.filter(s=>s&&typeof s.name==="string"&&typeof s.domain==="string");
        if(!persist())return;setView("archive");toast("Backup importato.");return;
      } catch(err){toast("File non valido: usa un backup JSON dell’anteprima.");}
    }
  }
  function submit(e) {
    if(e.target.id==="archive-form"){e.preventDefault();currentArchiveValues();render();return;}
    if(e.target.id!=="recipe-form")return;
    e.preventDefault();
    const form=e.target, f=new FormData(form);
    const val=n=>String(f.get(n)||"").trim();
    const ingredients=readRows("ingredients").map(x=>x.trim()).filter(Boolean),steps=readRows("steps").map(x=>x.trim()).filter(Boolean);
    if(!val("title")){toast("Indica il nome della ricetta.");return;}
    if(!ingredients.length||!steps.length){toast("Inserisci almeno un ingrediente e un passaggio.");return;}
    const original=state.recipes.find(x=>x.id===editingId);
    const id=original?.id||uid();
    const recipe=Object.assign({},original||{},{
      id,title:val("title"),description:val("description"),category:val("category"),difficulty:val("difficulty"),
      cuisine:val("cuisine"),portions:val("portions"),author:val("author"),cost:val("cost"),
      tags:val("tags"),allergens:val("allergens"),ingredients,steps,prepTime:val("prepTime"),
      cookTime:val("cookTime"),restTime:val("restTime"),totalTime:val("totalTime"),
      temperature:val("temperature"),equipment:val("equipment"),tips:val("tips"),
      variations:val("variations"),storage:val("storage"),nutrition:val("nutrition"),
      notes:val("notes"),photo:photoDraft,assignments:original?.assignments||[],demo:false
    });
    if(document.getElementById("plan-on-save").checked){
      const date=val("planDate"),meal=val("meal");
      if(!/^\d{4}-\d{2}-\d{2}$/.test(date)){toast("Seleziona il giorno.");return;}
      recipe.assignments.push({id:uid(),date,meal,alternative:val("alternative")==="Alternativa"});
      selected=date;month=fromISO(date);
    }
    if(original)state.recipes=state.recipes.map(x=>x.id===id?recipe:x);
    else state.recipes.unshift(recipe);
    if(!persist())return;
    editingId=id;view="detail";render();toast("Ricetta salvata nel browser.");
  }
  document.addEventListener("click",click);
  document.addEventListener("change",change);
  document.addEventListener("submit",submit);
  document.getElementById("theme-toggle").addEventListener("click",()=>{state.dark=!state.dark;saveAndRender();});
  const grid=document;
  grid.addEventListener("pointerdown",function(e) {
    const cell=e.target.closest("#calendar-grid .day[data-date]");if(!cell)return;
    pressedLong=false;pressPoint={x:e.clientX,y:e.clientY};
    clearTimeout(pressTimer);
    pressTimer=setTimeout(()=>{pressedLong=true;selected=cell.dataset.date;month=fromISO(selected);planner(null,null,"add");},2100);
  });
  grid.addEventListener("pointermove",function(e){if(pressPoint&&Math.hypot(e.clientX-pressPoint.x,e.clientY-pressPoint.y)>12){clearTimeout(pressTimer);pressPoint=null;}});
  ["pointerup","pointercancel","scroll"].forEach(name=>grid.addEventListener(name,()=>{clearTimeout(pressTimer);pressPoint=null;},true));
  document.addEventListener("keydown",function(e) {
    if(e.key==="Escape"&&dialog.open)closeModal();
    if(e.key==="Enter"&&(e.target.matches("#archive-form input")||e.target.matches("#recipe-form input:not([type=file])"))&&e.target.id!=="f-title"){
      if(e.target.closest("#recipe-form")){e.preventDefault();e.target.blur();}
    }
  });
  render();
  if(!state.tipsHidden)setTimeout(()=>{if(view==="calendar"&&!dialog.open)showHelp(false);},650);
})();
