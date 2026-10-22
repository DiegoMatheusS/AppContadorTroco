/* Interface e persistência local do Contador de Trocas. */
(() => {
  "use strict";
  const C = window.TrocoCore;
  const STORAGE_KEY = "contador-trocas:v1";
  const $ = selector => document.querySelector(selector);
  const formatMoney = cents => (cents / 100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
  const today = () => { const d = new Date(); return [d.getFullYear(),String(d.getMonth()+1).padStart(2,"0"),String(d.getDate()).padStart(2,"0")].join("-"); };
  const uuid = () => window.crypto?.randomUUID?.() || "id-"+Date.now()+"-"+Math.random().toString(36).slice(2);
  const defaultGifts = ["Caixa de bombom","Trufa","Bolo de pote","Desodorante","Kit sabonete","Escova de dente"].map((name,i)=>({id:"default-"+(i+1),name}));
  const defaultState = () => ({version:1,records:[],gifts:defaultGifts.map(g=>({...g})),awards:[]});

  let storageOk = true;
  function readState() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? C.validateBackup(JSON.parse(raw)) : defaultState();
    } catch(error) {
      storageOk = false;
      $("#storageWarning").hidden = false;
      $("#storageWarning").textContent = "Não foi possível ler os dados armazenados. Evite cadastrar ou importar até verificar o navegador e seus backups.";
      return defaultState();
    }
  }
  let state = readState();
  let month = today().slice(0,7);
  let view = "dashboard";
  let selectedKey = null;
  let drawing = false;
  let toastTimer;
  const draft = () => JSON.parse(JSON.stringify(state));
  function persist(next) {
    if (!storageOk) { showStorageWarning(); return false; }
    try {
      const verified = C.validateBackup(next);
      localStorage.setItem(STORAGE_KEY,JSON.stringify(verified));
      state = verified;
      render();
      return true;
    } catch(error) {
      showStorageWarning();
      return false;
    }
  }
  function showStorageWarning() {
    $("#storageWarning").hidden = false;
    $("#storageWarning").textContent = "Não foi possível salvar. Verifique o espaço ou as permissões do navegador; o novo registro não foi gravado.";
  }
  function toast(message) {
    const node = $("#toast");
    node.textContent = message; node.hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(()=>{node.hidden=true;},3000);
  }
  function element(tag, className, value) {
    const el = document.createElement(tag);
    if(className) el.className = className;
    if(value !== undefined) el.textContent = value;
    return el;
  }
  function button(label, className, dataName, dataValue) {
    const el = element("button",className,label);
    el.type = "button";
    if(dataName) el.dataset[dataName] = dataValue;
    return el;
  }
  function empty(target, icon, title, subtext) {
    target.replaceChildren();
    const box = element("div","empty-state");
    box.append(element("div","empty-icon",icon),element("strong","",title),element("p","",subtext));
    target.append(box);
  }
  function monthTitle(value) {
    const [y,m] = value.split("-").map(Number);
    return new Date(y,m-1,1).toLocaleDateString("pt-BR",{month:"long",year:"numeric"});
  }
  function monthlyRecords() {
    return state.records.filter(r=>r.date.slice(0,7)===month).sort((a,b)=>b.date.localeCompare(a.date)||b.id.localeCompare(a.id));
  }
  function ranks() { return C.ranking(state.records,month,state.awards); }
  function changeView(next) {
    view=next;
    document.querySelectorAll(".view").forEach(section=>{section.hidden=section.id!=="view-"+view;});
    document.querySelectorAll(".nav-item").forEach(tab=>{
      const active = tab.dataset.go===(view==="draw"?"ranking":view);
      tab.classList.toggle("active",active);
      if(active) tab.setAttribute("aria-current","page"); else tab.removeAttribute("aria-current");
    });
    if(view==="draw") renderDraw();
    window.scrollTo({top:0,behavior:"instant"});
  }
  function render() {
    $("#monthPicker").value=month; $("#monthLabel").textContent=monthTitle(month);
    const list=ranks();
    const total=list.reduce((sum,p)=>sum+p.totalCents,0);
    $("#statAmount").textContent=formatMoney(total);
    $("#statPeople").textContent=String(list.length);
    $("#statEligible").textContent=String(list.filter(p=>p.eligible).length);
    $("#rankingCount").textContent=list.length+" "+(list.length===1?"pessoa":"pessoas");
    const leader=$("#dashboardLeader");
    leader.replaceChildren();
    if(list.length) {
      leader.append(element("div","leader-name",list[0].name),element("div","leader-total",formatMoney(list[0].totalCents)));
      leader.append(element("p","body-muted",list[0].eligible?"Já ultrapassou a meta do mês.":"Ainda está acumulando trocas."));
    } else leader.append(element("p","body-muted","Cadastre sua primeira troca para ver quem está na frente."));
    renderEntries();
    renderRanking(list);
    renderGifts();
    renderSuggestions();
    if(view==="draw") renderDraw();
  }
  function makeEntryRow(record, allowDelete) {
    const row=element("div","list-row");
    const info=element("div","list-row-info");
    info.append(element("strong","",record.name),element("small","",new Date(record.date+"T12:00:00").toLocaleDateString("pt-BR")+" · "+(record.noteType==="mistas"?"Notas de R$ 5 e R$ 10":"Notas de R$ "+record.noteType)));
    const right=element("div","entry-amount-wrap");
    right.append(element("div","amount",formatMoney(record.amountCents)));
    if(allowDelete) right.append(button("Excluir","tiny-btn","deleteEntry",record.id));
    row.append(info,right);
    return row;
  }
  function renderEntries() {
    const rows=monthlyRecords();
    const recent=$("#recentEntries"); recent.replaceChildren();
    if(!rows.length) empty(recent,"↔","Nenhuma troca ainda","Os lançamentos deste mês aparecerão aqui.");
    else rows.slice(0,4).forEach(row=>recent.append(makeEntryRow(row,false)));
    const full=$("#entryList"); full.replaceChildren();
    if(!rows.length) empty(full,"▤","Nenhuma troca neste mês","Use o formulário acima para fazer o primeiro lançamento.");
    else rows.forEach(row=>full.append(makeEntryRow(row,true)));
  }
  function renderRanking(list) {
    const target=$("#rankingList"); target.replaceChildren();
    if(!list.length) {
      const surface=element("div","surface");
      empty(surface,"☆","Ranking vazio","Quando houver trocas neste mês, os participantes aparecerão aqui.");
      target.append(surface);
    }
    list.forEach((person,index)=>{
      const card=element("article","surface ranking-card");
      const main=element("div","ranking-main");
      main.append(element("div","rank-number",String(index+1).padStart(2,"0")));
      const detail=element("div","rank-detail");
      detail.append(element("strong","",person.name),element("small","",person.count+" "+(person.count===1?"troca registrada":"trocas registradas")));
      main.append(detail,element("div","rank-money",formatMoney(person.totalCents)));
      const footer=element("div","ranking-footer");
      const status=person.award?"Presente sorteado":person.eligible?"Pode sortear":"Ainda não atingiu a meta";
      footer.append(element("span","status-pill "+(person.award?"awarded":person.eligible?"eligible":""),status));
      if(person.award||person.eligible) {
        footer.append(button(person.award?"Ver presente →":"Sortear presente →","button button-secondary rank-action","drawPerson",person.key));
      } else {
        footer.append(element("small","body-muted","Faltam "+formatMoney(C.LIMIT_CENTS-person.totalCents+500)+" em notas de R$ 5 para superar R$ 150."));
      }
      card.append(main,footer); target.append(card);
    });
    const awards=$("#awardList");awards.replaceChildren();
    const all=state.awards.filter(a=>a.month===month).sort((a,b)=>b.at.localeCompare(a.at));
    if(!all.length) empty(awards,"🎁","Nenhum sorteio ainda","Os presentes sorteados no mês aparecerão aqui.");
    else all.forEach(award=>{
      const row=element("div","list-row");
      const info=element("div","list-row-info");
      info.append(element("strong","",award.name),element("small","",award.giftName+" · "+(award.delivered?"Entregue":"Pendente de entrega")));
      row.append(info,button(award.delivered?"Marcar pendente":"Marcar entregue","tiny-btn","toggleDelivered",award.month+"|"+award.personKey));
      awards.append(row);
    });
  }
  function renderSuggestions(){
    const suggestions=$("#personSuggestions"); suggestions.replaceChildren();
    const names=new Map();
    state.records.forEach(r=>names.set(C.personKey(r.name),r.name));
    [...names.values()].sort((a,b)=>a.localeCompare(b,"pt-BR")).forEach(name=>{
      const opt=document.createElement("option");opt.value=name;suggestions.append(opt);
    });
  }
  function renderGifts() {
    $("#giftCount").textContent=state.gifts.length+" "+(state.gifts.length===1?"opção":"opções");
    const list=$("#giftList");list.replaceChildren();
    if(!state.gifts.length) {empty(list,"🎁","Nenhum presente cadastrado","Adicione uma opção para habilitar os sorteios.");return;}
    state.gifts.forEach(gift=>{
      const row=element("div","list-row");
      const info=element("div","list-row-info");
      info.append(element("strong","",gift.name));
      row.append(element("div","gift-icon","🎁"),info);
      const actions=element("div","card-action-row");
      actions.append(button("Editar","tiny-btn","editGift",gift.id),button("Excluir","tiny-btn","deleteGift",gift.id));
      row.append(actions);list.append(row);
    });
  }
  function renderDraw() {
    const person=ranks().find(p=>p.key===selectedKey);
    const award=state.awards.find(a=>a.month===month&&a.personKey===selectedKey);
    if(!person && !award) {changeView("ranking");return;}
    $("#drawPerson").textContent=person?.name||award.name;
    $("#drawTotal").textContent=person?formatMoney(person.totalCents):"Histórico do mês";
    const result=$("#drawResult"), draw=$("#drawButton"), delivery=$("#drawDelivery");
    result.className="draw-result"; result.replaceChildren(); delivery.replaceChildren();
    if(award) {
      result.classList.add("winner");
      result.textContent="🎁 "+award.giftName;
      draw.hidden=true; delivery.hidden=false;
      delivery.append(element("strong","",award.delivered?"Presente entregue ✓":"Aguardando entrega"));
      delivery.append(button(award.delivered?"Desfazer entrega":"Marcar como entregue","button button-secondary","toggleDelivered",award.month+"|"+award.personKey));
    } else {
      draw.hidden=false;delivery.hidden=true;
      result.textContent=state.gifts.length?"Pronto para descobrir o presente?":"Cadastre um presente antes do sorteio.";
      draw.disabled=drawing||!person?.eligible||!state.gifts.length;
    }
  }
  function randomIndex(count) {
    if(window.crypto?.getRandomValues) {
      const values=new Uint32Array(1);
      const range=0x100000000;
      const limit=range-(range%count);
      do {window.crypto.getRandomValues(values);} while(values[0]>=limit);
      return values[0]%count;
    }
    return Math.floor(Math.random()*count);
  }
  async function runDraw() {
    if(drawing) return;
    const person=ranks().find(p=>p.key===selectedKey);
    if(!person?.eligible||person.award||!state.gifts.length) return;
    const drawnMonth=month, key=person.key, displayName=person.name;
    drawing=true; $("#drawButton").disabled=true;
    const result=$("#drawResult");
    result.className="draw-result spinning";
    const animate=()=>{ result.textContent=state.gifts[randomIndex(state.gifts.length)]?.name || "🎁"; };
    animate();
    const tick=setInterval(animate,90);
    await new Promise(resolve=>setTimeout(resolve,1500));
    clearInterval(tick);drawing=false;
    if(state.awards.some(a=>a.month===drawnMonth&&a.personKey===key)) {render();return;}
    const eligible=C.ranking(state.records,drawnMonth,state.awards).find(p=>p.key===key)?.eligible;
    if(!eligible||!state.gifts.length){render();return;}
    const gift=state.gifts[randomIndex(state.gifts.length)];
    const next=draft();
    next.awards.push({month:drawnMonth,personKey:key,name:displayName,giftId:gift.id,giftName:gift.name,at:new Date().toISOString(),delivered:false});
    if(persist(next)) toast("Presente sorteado e salvo!");
    else render();
  }
  function toggleDelivered(key) {
    const sep=key.indexOf("|");
    const targetMonth=key.slice(0,sep),person=key.slice(sep+1);
    const next=draft();
    const award=next.awards.find(a=>a.month===targetMonth&&a.personKey===person);
    if(!award)return;
    award.delivered=!award.delivered;
    if(persist(next)) toast(award.delivered?"Entrega registrada.":"Entrega desmarcada.");
  }
  $("#entryForm").addEventListener("submit",event=>{
    event.preventDefault();
    const form=event.currentTarget;
    const data=C.validateEntry({name:$("#personName").value,date:$("#entryDate").value,amount:$("#entryAmount").value,noteType:$("#noteType").value});
    const error=$("#entryError");error.hidden=data.ok;
    if(!data.ok){error.textContent=data.errors.join(" ");return;}
    const next=draft();
    next.records.push({id:uuid(),...data.data});
    if(persist(next)) {
      error.hidden=true;
      $("#personName").value="";$("#entryAmount").value="";$("#entryDate").value=today();
      month=data.data.date.slice(0,7);render();
      toast("Troca registrada com sucesso!");
    }
  });
  $("#giftForm").addEventListener("submit",event=>{
    event.preventDefault();
    const input=$("#giftName"),name=input.value.trim().replace(/\s+/g," ");
    if(!name||name.length>80)return;
    if(state.gifts.some(g=>g.name.toLocaleLowerCase("pt-BR")===name.toLocaleLowerCase("pt-BR"))) {toast("Esse presente já existe.");return;}
    const next=draft();next.gifts.push({id:uuid(),name});
    if(persist(next)){input.value="";toast("Presente adicionado.");}
  });
  $("#monthPicker").addEventListener("change",event=>{
    if(!C.validMonth(event.target.value)){event.target.value=month;return;}
    month=event.target.value;
    if(view==="draw")changeView("ranking");
    render();
  });
  $("#drawButton").addEventListener("click",runDraw);
  $("#drawBack").addEventListener("click",()=>changeView("ranking"));
  document.addEventListener("click",event=>{
    const nav=event.target.closest("[data-go]");
    if(nav){changeView(nav.dataset.go);return;}
    const del=event.target.closest("[data-delete-entry]");
    if(del){
      const record=state.records.find(r=>r.id===del.dataset.deleteEntry);if(!record)return;
      const award=state.awards.find(a=>a.month===record.date.slice(0,7)&&a.personKey===C.personKey(record.name));
      const warning=award?" O sorteio já realizado permanecerá registrado.":"";
      if(!confirm("Excluir a troca de "+record.name+" ("+formatMoney(record.amountCents)+")?"+warning))return;
      const next=draft();next.records=next.records.filter(r=>r.id!==record.id);
      if(persist(next))toast("Troca excluída.");
      return;
    }
    const participant=event.target.closest("[data-draw-person]");
    if(participant){selectedKey=participant.dataset.drawPerson;changeView("draw");return;}
    const delivery=event.target.closest("[data-toggle-delivered]");
    if(delivery){toggleDelivered(delivery.dataset.toggleDelivered);return;}
    const editGift=event.target.closest("[data-edit-gift]");
    if(editGift){
      const gift=state.gifts.find(g=>g.id===editGift.dataset.editGift);if(!gift)return;
      const answer=prompt("Nome do presente:",gift.name);
      if(answer===null)return;
      const name=answer.trim().replace(/\s+/g," ");
      if(!name||name.length>80){toast("Informe um nome de até 80 caracteres.");return;}
      if(state.gifts.some(g=>g.id!==gift.id&&g.name.toLocaleLowerCase("pt-BR")===name.toLocaleLowerCase("pt-BR"))){toast("Esse presente já existe.");return;}
      const next=draft();next.gifts.find(g=>g.id===gift.id).name=name;
      if(persist(next))toast("Presente atualizado.");
      return;
    }
    const delGift=event.target.closest("[data-delete-gift]");
    if(delGift){
      const gift=state.gifts.find(g=>g.id===delGift.dataset.deleteGift);if(!gift)return;
      if(!confirm("Excluir \""+gift.name+"\" das opções? Sorteios antigos serão preservados."))return;
      const next=draft();next.gifts=next.gifts.filter(g=>g.id!==gift.id);
      if(persist(next))toast("Presente excluído.");
    }
  });
  $("#exportButton").addEventListener("click",()=>{
    const blob=new Blob([JSON.stringify(state,null,2)],{type:"application/json"});
    const url=URL.createObjectURL(blob);
    const link=document.createElement("a");link.href=url;link.download="contador-trocas-backup-"+today()+".json";
    document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1500);
    toast("Backup preparado para download.");
  });
  $("#importInput").addEventListener("change",async event=>{
    const input=event.target;
    const file=input.files?.[0];if(!file)return;
    try{
      if(file.size>10_000_000)throw new Error("O arquivo excede 10 MB.");
      const restored=C.validateBackup(JSON.parse(await file.text()));
      if(!confirm("Importar backup? Isso substituirá TODAS as trocas, os presentes e os sorteios salvos neste navegador."))return;
      if(persist(restored)){selectedKey=null;changeView("dashboard");toast("Backup restaurado com sucesso.");}
    }catch(error){toast(error instanceof SyntaxError?"O arquivo não contém JSON válido.":error.message||"Falha ao importar arquivo.");}
    finally{input.value="";}
  });
  window.addEventListener("storage",event=>{
    if(event.key!==STORAGE_KEY)return;
    state=readState();render();toast("Dados atualizados de outra aba.");
  });
  $("#entryDate").value=today();
  render();
  changeView("dashboard");
  if("serviceWorker" in navigator && window.location.protocol!=="file:") {
    window.addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));
  }
})();
