/* Regras puras do aplicativo — também usadas nos testes automatizados. */
(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.TrocoCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";
  const LIMIT_CENTS = 15000;
  const NOTE_TYPES = ["5", "10", "mistas"];

  function normalizeName(value) {
    return String(value || "").trim().replace(/\s+/g, " ");
  }
  function personKey(value) {
    return normalizeName(value).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR");
  }
  function validDate(value) {
    if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const [y,m,d] = value.split("-").map(Number);
    const date = new Date(Date.UTC(y,m-1,d));
    return date.getUTCFullYear() === y && date.getUTCMonth() + 1 === m && date.getUTCDate() === d && y >= 2000 && y <= 2100;
  }
  function validMonth(value) {
    return typeof value === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(value);
  }
  function parseCents(value) {
    const raw = String(value ?? "").trim().replace(/\s/g, "").replace(/^R\$/i, "").trim();
    if (!/^\d+(?:[.,]\d{1,2})?$/.test(raw)) return null;
    const [whole, decimals = ""] = raw.replace(",", ".").split(".");
    const cents = Number(whole) * 100 + Number(decimals.padEnd(2,"0"));
    return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
  }
  function validNotes(amountCents, noteType) {
    return Number.isSafeInteger(amountCents) && amountCents > 0 &&
      NOTE_TYPES.includes(noteType) && amountCents % (noteType === "10" ? 1000 : 500) === 0;
  }
  function validateEntry({name, date, amount, noteType}) {
    const cleanName = normalizeName(name);
    const amountCents = parseCents(amount);
    const errors = [];
    if (!cleanName || cleanName.length > 80) errors.push("Informe um nome de até 80 caracteres.");
    if (!validDate(date)) errors.push("Informe uma data válida.");
    if (!validNotes(amountCents, noteType)) errors.push("Informe um valor compatível com as notas escolhidas (múltiplos de R$ 5 ou R$ 10).");
    return {ok: errors.length === 0, errors, data: {name: cleanName, date, amountCents, noteType}};
  }
  function ranking(records, month, awards = []) {
    const people = new Map();
    for (const record of records) {
      if (!record || record.date?.slice(0,7) !== month ||
          !validDate(record.date) || !validNotes(record.amountCents, record.noteType)) continue;
      const name = normalizeName(record.name);
      const key = personKey(name);
      if (!key) continue;
      if (!people.has(key)) people.set(key, {key, name, totalCents: 0, count: 0});
      const person = people.get(key);
      person.totalCents += record.amountCents;
      person.count++;
    }
    return [...people.values()].map(person => {
      const award = awards.find(a => a.month === month && a.personKey === person.key) || null;
      return {...person, eligible: person.totalCents > LIMIT_CENTS, award};
    }).sort((a,b) => b.totalCents - a.totalCents || a.name.localeCompare(b.name,"pt-BR"));
  }
  function validateBackup(raw) {
    if (!raw || raw.version !== 1 || !Array.isArray(raw.records) ||
        !Array.isArray(raw.gifts) || !Array.isArray(raw.awards)) throw new Error("Arquivo de backup incompatível.");
    if (raw.records.length > 100000 || raw.gifts.length > 1000 || raw.awards.length > 100000)
      throw new Error("Arquivo muito grande.");
    const ids = new Set();
    const records = raw.records.map(r => {
      if (!r || typeof r.id !== "string" || !r.id || ids.has(r.id) ||
          !normalizeName(r.name) || normalizeName(r.name).length > 80 ||
          !validDate(r.date) || !validNotes(r.amountCents,r.noteType)) throw new Error("Há registros inválidos no backup.");
      ids.add(r.id);
      return {id:r.id, name:normalizeName(r.name),date:r.date,amountCents:r.amountCents,noteType:r.noteType};
    });
    const giftIds = new Set();
    const gifts = raw.gifts.map(g => {
      if (!g || typeof g.id !== "string" || !g.id || giftIds.has(g.id) ||
          typeof g.name !== "string" || !g.name.trim() || g.name.length > 80)
        throw new Error("Há presentes inválidos no backup.");
      giftIds.add(g.id);
      return {id:g.id,name:g.name.trim()};
    });
    const awardKeys = new Set();
    const awards = raw.awards.map(a => {
      const key = a?.month + "|" + a?.personKey;
      if (!a || !validMonth(a.month) || typeof a.personKey !== "string" || !a.personKey ||
          typeof a.name !== "string" || !normalizeName(a.name) ||
          typeof a.giftName !== "string" || !a.giftName.trim() ||
          a.giftName.length > 80 || typeof a.giftId !== "string" ||
          typeof a.at !== "string" || !Number.isFinite(Date.parse(a.at)) ||
          typeof a.delivered !== "boolean" || awardKeys.has(key)) throw new Error("Há sorteios inválidos no backup.");
      awardKeys.add(key);
      return {month:a.month,personKey:a.personKey,name:normalizeName(a.name),
        giftId:a.giftId,giftName:a.giftName.trim(),at:a.at,delivered:a.delivered};
    });
    return {version:1,records,gifts,awards};
  }
  return {LIMIT_CENTS,NOTE_TYPES,normalizeName,personKey,validDate,validMonth,
    parseCents,validNotes,validateEntry,ranking,validateBackup};
});
