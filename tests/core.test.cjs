const test = require("node:test");
const assert = require("node:assert/strict");
const core = require("../core.js");
const rec = (id, name, date, amountCents, noteType="5") => ({id,name,date,amountCents,noteType});

test("R$ 150 exatos não dão prêmio; R$ 155 dão", () => {
  const rows = [rec("a","Ana","2026-10-02",10000),rec("b","Ana","2026-10-09",5000)];
  assert.equal(core.ranking(rows,"2026-10")[0].eligible,false);
  rows.push(rec("c"," ANA ","2026-10-12",500));
  const result = core.ranking(rows,"2026-10");
  assert.equal(result.length,1);
  assert.equal(result[0].totalCents,15500);
  assert.equal(result[0].eligible,true);
});
test("meses são independentes e ranking ordena por total", () => {
  const rows = [rec("1","Bia","2026-10-10",20000),rec("2","Ana","2026-10-11",25000),rec("3","Bia","2026-09-10",5000)];
  assert.deepEqual(core.ranking(rows,"2026-10").map(r=>r.name),["Ana","Bia"]);
  assert.equal(core.ranking(rows,"2026-09")[0].totalCents,5000);
});
test("valores e tipos de notas são verificados", () => {
  assert.equal(core.parseCents("150,00"),15000);
  assert.equal(core.parseCents("10,257"),null);
  assert.equal(core.validateEntry({name:"João",date:"2026-02-28",amount:"155",noteType:"mistas"}).ok,true);
  assert.equal(core.validateEntry({name:"João",date:"2026-02-30",amount:"150",noteType:"5"}).ok,false);
  assert.equal(core.validateEntry({name:"João",date:"2026-02-28",amount:"155",noteType:"10"}).ok,false);
  assert.equal(core.validateEntry({name:"João",date:"2026-02-28",amount:"153",noteType:"mistas"}).ok,false);
});
test("sorteio já registrado aparece no ranking", () => {
  const awards = [{month:"2026-10",personKey:"ana",giftId:"p",giftName:"Trufa",name:"Ana",at:"2026-10-22T10:00:00Z",delivered:false}];
  assert.equal(core.ranking([rec("1","Ana","2026-10-01",20000)],"2026-10",awards)[0].award.giftName,"Trufa");
});
test("backup recusa entradas inválidas e duplicatas", () => {
  const backup = {version:1,records:[rec("1","Ana","2026-10-02",20000)],gifts:[{id:"p",name:"Trufa"}],awards:[]};
  assert.deepEqual(core.validateBackup(backup).records,backup.records);
  assert.throws(()=>core.validateBackup({...backup,records:[...backup.records,...backup.records]}));
  assert.throws(()=>core.validateBackup({...backup,records:[rec("a","X","2026-10-01",12300)]}));
});
