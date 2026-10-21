#!/usr/bin/env node
"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const config=JSON.parse(fs.readFileSync(path.join(root,"vercel.json"),"utf8"));
assert.equal(config.framework,null,"Vercel must use Framework Preset: Other");
assert.equal(config.buildCommand,null,"Static preview must not build the Android app");
assert.equal(config.outputDirectory,"docs","Output directory must be docs from repository root");
const html=fs.readFileSync(path.join(root,"docs/index.html"),"utf8");
for(const file of ["index.html","app.js","style.css","manifest.webmanifest","icon.svg"]){
  assert(fs.existsSync(path.join(root,"docs",file)),"Missing deployment asset "+file);
  if(file!=="index.html")assert(html.includes("./"+file),"Index must use relative asset "+file);
}
assert(html.includes('id="app"'),"Web application mount missing");
console.log("PASS: Vercel deploys docs/ as a static site from repository root, no Android build needed.");
