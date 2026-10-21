#!/usr/bin/env node
"use strict";
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const assert=require("node:assert/strict");

const root=path.resolve(__dirname,"..");
const source=fs.readFileSync(path.join(root,"docs","app.js"),"utf8");
const html=fs.readFileSync(path.join(root,"docs","index.html"),"utf8");
const style=fs.readFileSync(path.join(root,"docs","style.css"),"utf8");
for(const asset of ["style.css","app.js","icon.svg","manifest.webmanifest"]) {
  assert(html.includes('./'+asset),"Index does not reference "+asset);
  assert(fs.existsSync(path.join(root,"docs",asset)),asset+" missing");
}
assert(style.includes("@media(max-width:760px)"),"Mobile layout missing");
assert(html.includes('class="phone-preview"'),"Web preview must be wrapped in a smartphone frame");
assert(style.includes("width: 390px;") && style.includes("height: min(844px"),"PC preview must simulate smartphone dimensions");
assert(style.includes(".phone-preview .sidebar { display: none; }"),"No desktop navigation should appear");
assert(style.includes(".phone-preview .bottom-nav"),"Phone preview must show the mobile navigation");
assert(source.includes("Tutti gli ingredienti")||source.includes("tutti gli ingredienti"),"AND filter description missing");

const storage=new Map();
const listeners={};
const lookup=new Map();
function element(id) {
  if(!lookup.has(id))lookup.set(id,{
    id,innerHTML:"",textContent:"",value:"",open:false,
    classList:{toggle(){},add(){},remove(){}},
    addEventListener(){},scrollIntoView(){},insertAdjacentHTML(where,markup){this.innerHTML=markup+this.innerHTML;},
    showModal(){this.open=true;},close(){this.open=false;}
  });
  return lookup.get(id);
}
const dom={
  body:{classList:{toggle(){}}},
  getElementById:element,
  querySelectorAll(selector) {
    if(selector==="[data-view]")return [];
    if(selector==="[data-filter-ingredient]")return this.filters||[];
    return [];
  },
  querySelector(selector) {
    if(selector==='#archive-form [name="search"]')return {value:""};
    if(selector==='#archive-form [name="category"]')return {value:"Tutte"};
    return null;
  },
  addEventListener(name,callback){listeners[name]=callback;}
};
let context=vm.createContext({
  document:dom,window:{print(){}},
  localStorage:{getItem(k){return storage.get(k)||null;},setItem(k,v){storage.set(k,v);}},
  Date,Intl,Math,JSON,Array,Blob,URL,
  setTimeout(){return 1;},clearTimeout(){},
  console
});
vm.runInContext(source,context,{filename:"docs/app.js",timeout:2000});
assert(element("app").innerHTML.includes("Il tuo calendario"),"Calendar failed to render");
assert(element("app").innerHTML.includes("Pranzo"),"Lunch missing");
assert(element("app").innerHTML.includes("Cena"),"Dinner missing");
function clickAction(action,dataset) {
  listeners.click({target:{closest(sel){return sel==="[data-action]"?{dataset:Object.assign({action},dataset||{})}:null;}}});
}
function clickView(view) {
  listeners.click({target:{closest(sel){return sel==="[data-view]"?{dataset:{view}}:null;}}});
}
clickView("archive");
assert(element("app").innerHTML.includes("← Indietro"),"Back control missing from archive");
assert(element("app").innerHTML.includes("Pasta alle zucchine"),"Recipes not displayed");
clickAction("go-back");
assert(element("app").innerHTML.includes("Il tuo calendario"),"Back did not return to calendar");
clickView("archive");
dom.filters=[{value:"riso"},{value:"funghi"}];
listeners.submit({target:{id:"archive-form"},preventDefault(){}});
assert(element("app").innerHTML.includes("Risotto ai funghi"),"All-ingredient match missing");
assert(!element("app").innerHTML.includes('<h3>Pasta alle zucchine</h3>'),"AND ingredient filter returned an unrelated recipe");

clickView("sources");
assert(element("app").innerHTML.includes("GialloZafferano"),"Default sources missing");
clickAction("go-back");
assert(element("app").innerHTML.includes("Ricettario"),"Sources back did not return to archive");
clickView("sources");
clickAction("remove-source",{index:"0"});
assert(element("modal").open,"Delete source confirmation missing");
clickAction("confirm-remove-source",{index:"0"});
assert(!element("app").innerHTML.includes("GialloZafferano"),"Deleted source still visible");
assert(!JSON.parse(storage.get("oggisimangia_web_preview_v1")).sources.some(s=>s.domain==="giallozafferano.it"),"Deletion not persisted");

clickView("calendar");
clickAction("plan-recipe",{id:"demo-pasta"});
assert(element("modal").open,"Planner dialog missing");
const oldGet=dom.getElementById.bind(dom);
dom.getElementById=id=>{
  if(id==="plan-recipe")return {value:"demo-pasta"};
  if(id==="f-date")return {value:"2026-10-21"};
  if(id==="f-meal")return {value:"Pranzo"};
  if(id==="f-alternative")return {value:"Principale"};
  return oldGet(id);
};
clickAction("save-plan",{mode:"add",id:"demo-pasta",aid:""});
let recipes=JSON.parse(storage.get("oggisimangia_web_preview_v1")).recipes;
let recipe=recipes.find(r=>r.id==="demo-pasta");
assert.equal(recipe.assignments.length,1,"Meal assignment not saved");
assert.equal(recipe.assignments[0].date,"2026-10-21");
assert(element("app").innerHTML.includes("Pasta alle zucchine"),"Meal did not appear in calendar");
clickAction("clone-plan",{id:"demo-pasta",aid:recipe.assignments[0].id});
dom.getElementById=id=>{
  if(id==="plan-recipe")return {value:"demo-pasta"};
  if(id==="f-date")return {value:"2026-10-23"};
  if(id==="f-meal")return {value:"Cena"};
  if(id==="f-alternative")return {value:"Alternativa"};
  return oldGet(id);
};
clickAction("save-plan",{mode:"clone",id:"demo-pasta",aid:recipe.assignments[0].id});
recipes=JSON.parse(storage.get("oggisimangia_web_preview_v1")).recipes;
recipe=recipes.find(r=>r.id==="demo-pasta");
assert.equal(recipe.assignments.length,2,"Clone did not preserve original");
assert(recipe.assignments.some(a=>a.date==="2026-10-21"&&a.meal==="Pranzo"),"Original planning was replaced");
assert(recipe.assignments.some(a=>a.date==="2026-10-23"&&a.meal==="Cena"&&a.alternative),"Clone meal and role lost");
clickAction("reset-day");
assert(element("modal").open,"Reset day should request confirmation");
clickAction("confirm-reset-day");
recipes=JSON.parse(storage.get("oggisimangia_web_preview_v1")).recipes;
recipe=recipes.find(r=>r.id==="demo-pasta");
assert.equal(recipe.assignments.length,1,"Reset day did not remove only that day's entries");
assert.equal(recipe.assignments[0].date,"2026-10-21","Reset day deleted assignments from a different day");
assert(recipes.some(r=>r.id==="demo-pasta"),"Reset day must retain recipe in archive");
clickAction("select-day",{date:"2026-10-21"});
assert(element("app").innerHTML.includes("Pasta alle zucchine"),"Collapsed meal title should be visible");
assert(!element("app").innerHTML.includes('data-action="delete-plan"'),"Meal controls should be collapsed");
clickAction("toggle-meal",{meal:"Pranzo"});
assert(element("app").innerHTML.includes('data-action="delete-plan"'),"Expanding meal should reveal removal control");
clickAction("delete-plan",{id:"demo-pasta",aid:recipe.assignments[0].id});
assert(element("modal").open,"Removing a single day requires confirmation");
clickAction("confirm-delete-plan",{id:"demo-pasta",aid:recipe.assignments[0].id});
recipes=JSON.parse(storage.get("oggisimangia_web_preview_v1")).recipes;
assert.equal(recipes.find(r=>r.id==="demo-pasta").assignments.length,0,"Single day unplanning failed");
assert(recipes.some(r=>r.id==="demo-pasta"),"Unplanning must not delete the recipe");

console.log("PASS: Pages assets, back navigation, compact meals, day reset/unplan, archive filtering, persistent site deletion, planning and cloning.");
