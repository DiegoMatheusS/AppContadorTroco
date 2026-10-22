package com.diegomatheus.contadortroco

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF0A6754)
private val DarkGreen = Color(0xFF064D40)
private val Mint = Color(0xFFE4F4EA)
private val PageBg = Color(0xFFF5F8F6)
private val BodyGray = Color(0xFF6D8177)
private val Gold = Color(0xFFAD7625)
private val Brazil = Locale("pt", "BR")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Brazil)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Brazil)
private fun reais(valueInCents: Long): String =
    NumberFormat.getCurrencyInstance(Brazil).format(valueInCents / 100.0)

class MainActivity : ComponentActivity() {
    private lateinit var store: TrocoStore
    private var data by mutableStateOf(TrocoData())
    private var storageError by mutableStateOf<String?>(null)
    private var locked = false
    private var runningMonth by mutableStateOf(YearMonth.now())

    override fun onResume() {
        super.onResume()
        runningMonth = YearMonth.now()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TrocoStore(this)
        runCatching { store.load() }.onSuccess { data = it }.onFailure {
            locked = true
            storageError = "Não foi possível ler os dados locais. Seus registros não foram sobrescritos. Restaure um backup válido."
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Green, onPrimary = Color.White, secondary = Green,
                background = PageBg, surface = Color.White, onSurface = DarkGreen
            )) {
                // Mantém o mês vigente correto mesmo se o app permanecer aberto na virada.
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(60_000L)
                        runningMonth = YearMonth.now()
                    }
                }
                var pendingBackup by remember { mutableStateOf<TrocoData?>(null) }
                val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) runCatching {
                        contentResolver.openOutputStream(uri)?.use {
                            it.write(store.encode(data).toByteArray(Charsets.UTF_8))
                        } ?: error("Não foi possível abrir o arquivo.")
                    }.onSuccess { message("Backup exportado.") }
                     .onFailure { message("Erro no backup: ${it.message}") }
                }
                val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) runCatching {
                        val json = contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(8192)
                            val output = ByteArrayOutputStream()
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                require(output.size() <= 15_000_000) { "Arquivo grande demais." }
                            }
                            output.toString(Charsets.UTF_8.name())
                        } ?: error("Não foi possível ler o arquivo.")
                        store.decode(json)
                    }.onSuccess { pendingBackup = it }
                     .onFailure { message("Backup inválido: ${it.message}") }
                }
                pendingBackup?.let { next ->
                    AlertDialog(
                        onDismissRequest = { pendingBackup = null },
                        title = { Text("Restaurar backup?") },
                        text = { Text("Esta operação substituirá todas as trocas, presentes e sorteios deste aparelho.") },
                        confirmButton = { TextButton(onClick = {
                            pendingBackup = null
                            if (save(next, true)) message("Backup restaurado.")
                        }) { Text("Restaurar") } },
                        dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text("Cancelar") } }
                    )
                }
                TrocoScreen(
                    data = data, storageError = storageError, activeMonth = runningMonth,
                    onRegister = { name, date, five, ten ->
                        if (!TrocoRules.isValidExchange(name, date.toString(), five, ten)) false
                        else save(data.copy(exchanges = data.exchanges + Exchange(
                            UUID.randomUUID().toString(),
                            name.trim().replace(Regex("\\s+"), " "), date.toString(), five, ten
                        ))).also { if (it) message("Troca salva.") }
                    },
                    onDelete = { id -> if (save(data.copy(exchanges = data.exchanges.filterNot { it.id == id }))) message("Troca excluída.") },
                    onAddGift = { name ->
                        val clean = name.trim().replace(Regex("\\s+"), " ")
                        if (clean.isNotBlank() && clean.length <= 80 &&
                            data.gifts.none { it.name.equals(clean, true) })
                            save(data.copy(gifts = data.gifts + Gift(UUID.randomUUID().toString(), clean)))
                        else { message("Nome de presente inválido ou repetido."); false }
                    },
                    onEditGift = { id, name ->
                        val clean = name.trim().replace(Regex("\\s+"), " ")
                        if (clean.isNotBlank() && clean.length <= 80 &&
                            data.gifts.none { it.id != id && it.name.equals(clean, true) })
                            save(data.copy(gifts = data.gifts.map { if (it.id == id) it.copy(name = clean) else it }))
                        else { message("Nome inválido ou repetido."); false }
                    },
                    onDeleteGift = { id -> save(data.copy(gifts = data.gifts.filterNot { it.id == id })) },
                    onDraw = { month, key ->
                        if (month != YearMonth.now() || !TrocoRules.canDraw(data, month, key)) false
                        else {
                            val person = TrocoRules.ranks(data, month).first { it.key == key }
                            val gift = data.gifts[SecureRandom().nextInt(data.gifts.size)]
                            save(data.copy(awards = data.awards + Award(
                                month.toString(), key, person.name, gift.id, gift.name, System.currentTimeMillis()
                            )))
                        }
                    },
                    onDelivery = { month, key -> save(data.copy(awards = data.awards.map {
                        if (it.month == month && it.personKey == key) it.copy(delivered = !it.delivered) else it
                    })) },
                    onExport = { export.launch("trocas-${LocalDate.now()}.json") },
                    onImport = { import.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
            }
        }
    }

    private fun save(next: TrocoData, restoring: Boolean = false): Boolean {
        if (locked && !restoring) { message("Restaure um backup para liberar alterações."); return false }
        return runCatching { store.save(next) }.onSuccess {
            data = next; locked = false; storageError = null
        }.onFailure { message("Não foi possível salvar: ${it.message}") }.isSuccess
    }

    private fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun TrocoScreen(
    data: TrocoData,
    storageError: String?,
    activeMonth: YearMonth,
    onRegister: (String, LocalDate, Int, Int) -> Boolean,
    onDelete: (String) -> Unit,
    onAddGift: (String) -> Boolean,
    onEditGift: (String, String) -> Boolean,
    onDeleteGift: (String) -> Boolean,
    onDraw: (YearMonth, String) -> Boolean,
    onDelivery: (String, String) -> Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("inicio") }

    var historyMonthText by rememberSaveable { mutableStateOf(YearMonth.now().minusMonths(1).toString()) }
    var selectedPerson by rememberSaveable { mutableStateOf("") }
    // Tela principal sempre representa SOMENTE o mês vigente, sem carregar períodos anteriores.
    val month = activeMonth
    val currentMonth = month.toString()
    val ranks = remember(data, currentMonth) { TrocoRules.ranks(data, month) }
    val exchanges = remember(data, currentMonth) {
        data.exchanges.filter { it.date.startsWith("$currentMonth-") }.sortedByDescending { it.date }
    }
    val awards = data.awards.filter { it.month == currentMonth }.sortedByDescending { it.at }
    val pastMonths = remember(data, activeMonth) { TrocoRules.historicalMonths(data, activeMonth) }
    val historyMonth = YearMonth.parse(historyMonthText)
    val oldRanks = remember(data, historyMonthText) { TrocoRules.ranks(data, historyMonth) }
    val oldExchanges = remember(data, historyMonthText) {
        data.exchanges.filter { it.date.startsWith("$historyMonthText-") }.sortedByDescending { it.date }
    }
    val oldAwards = data.awards.filter { it.month == historyMonthText }.sortedByDescending { it.at }
    val openHistory: () -> Unit = {
        historyMonthText = pastMonths.firstOrNull()?.toString() ?: activeMonth.minusMonths(1).toString()
        tab = "historico"
    }
    Scaffold(
        containerColor = PageBg,
        topBar = {
            Column(Modifier.fillMaxWidth().background(Green).padding(horizontal = 20.dp, vertical = 19.dp)) {
                Text("CONTROLE MENSAL", color = Color(0xFFB2E4CF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Contador de Trocas", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Troque, acompanhe e presenteie", color = Color(0xFFCCEBDF), fontSize = 12.sp)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    Triple("inicio","Início","⌂"), Triple("registro","Registrar","+"),
                    Triple("ranking","Ranking","▤"), Triple("presentes","Presentes","♢"),
                    Triple("ajustes","Ajustes","⚙")
                ).forEach { (id, label, icon) ->
                    NavigationBarItem(
                        selected = tab == id || ((tab == "sorteio" || tab == "historico") && id == "ranking"),
                        onClick = { tab = id }, icon = { Text(icon, fontSize = 22.sp) },
                        label = { Text(label, fontSize = 10.sp) }, alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Mint,
                            unselectedIconColor = BodyGray
                        )
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (tab == "historico") "MÊS ARQUIVADO" else "MÊS ATUAL",
                                color = BodyGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text((if (tab == "historico") historyMonth else month).atDay(1)
                                    .format(monthFormat).replaceFirstChar { it.uppercase() },
                                fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        if (tab == "historico") {
                            TextButton(onClick = {
                                historyMonthText = historyMonth.minusMonths(1).toString()
                            }) { Text("‹", fontSize = 29.sp) }
                            TextButton(
                                enabled = historyMonth.isBefore(activeMonth.minusMonths(1)),
                                onClick = { historyMonthText = historyMonth.plusMonths(1).toString() }
                            ) { Text("›", fontSize = 29.sp) }
                        } else {
                            Text("Reinicia a cada mês", color = Green, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (storageError != null) item { Banner(storageError, true) }
            when (tab) {
                "inicio" -> {
                    item { HomeSummary(ranks, onRanking = { tab = "ranking" }, onRegister = { tab = "registro" }, onHistory = openHistory) }
                    item { Section("Últimas trocas", "LANÇAMENTOS DO MÊS") }
                    if (exchanges.isEmpty()) item { Empty("Nenhuma troca registrada neste mês.") }
                    items(exchanges.take(5), key = { it.id }) { EntryCard(it) }
                }
                "registro" -> {
                    item { Section("Registrar troca", "NOVO LANÇAMENTO") }
                    item { RegisterForm(data.exchanges, month, onRegister) }
                    item { Section("Trocas cadastradas", "NO MÊS SELECIONADO") }
                    if (exchanges.isEmpty()) item { Empty("Nenhuma troca neste mês.") }
                    items(exchanges, key = { it.id }) { entry -> EntryCard(entry, { onDelete(entry.id) }) }
                }
                "ranking" -> {
                    item { Section("Ranking mensal", "QUEM TROCOU MAIS") }
                    item { Banner("Somente as trocas do mês atual contam. Com R$ 150,00 ou mais, a pessoa tem direito a um sorteio.") }
                    item { TextButton(onClick = openHistory) { Text("Consultar histórico dos meses anteriores →") } }
                    if (ranks.isEmpty()) item { Empty("Nenhum participante neste mês.") }
                    itemsIndexed(ranks, key = { _, person -> person.key }) { index, person ->
                        RankingCard(index + 1, person, onOpen = {
                            selectedPerson = person.key
                            tab = "sorteio"
                        })
                    }
                    item { Section("Presentes sorteados", "HISTÓRICO DO MÊS") }
                    if (awards.isEmpty()) item { Empty("Nenhum presente sorteado ainda.") }
                    items(awards, key = { it.month + it.personKey }) { award ->
                        AwardCard(award, onDelivery = { onDelivery(award.month, award.personKey) })
                    }
                }
                "sorteio" -> {
                    item { TextButton(onClick = { tab = "ranking" }) { Text("← Voltar ao ranking") } }
                    item {
                        val person = ranks.firstOrNull { it.key == selectedPerson }
                        val award = awards.firstOrNull { it.personKey == selectedPerson }
                        if (person == null && award == null) Empty("Participante não encontrado.")
                        else DrawCard(person, award, data.gifts, onDraw = { onDraw(month, selectedPerson) },
                            onDelivery = { onDelivery(currentMonth, selectedPerson) })
                    }
                }
                "presentes" -> {
                    item { Section("Presentes disponíveis", "CATÁLOGO DO SORTEIO") }
                    item { GiftForm(onAddGift) }
                    item { Banner("Todos os presentes têm a mesma chance de serem sorteados.") }
                    if (data.gifts.isEmpty()) item { Empty("Cadastre um presente antes de sortear.") }
                    items(data.gifts, key = { it.id }) { gift -> GiftCard(gift, onEditGift, onDeleteGift) }
                }
                "historico" -> {
                    item { TextButton(onClick = { tab = "ranking" }) { Text("← Voltar ao mês atual") } }
                    item { Section("Histórico de trocas", "MESES ENCERRADOS") }
                    item { Banner("Ao começar um novo mês, o total e o ranking voltam a zero. Os registros antigos permanecem disponíveis apenas para consulta.") }
                    item {
                        AppCard {
                            Text("TOTAL HISTÓRICO (MESES ENCERRADOS)", color = BodyGray,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(reais(TrocoRules.historicalTotalCents(data, activeMonth)),
                                color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                            Subtext("${pastMonths.size} meses arquivados. Este total não conta para presentes do mês atual.")
                        }
                    }
                    item { Section("Resumo do mês", "HISTÓRICO SELECIONADO") }
                    item {
                        AppCard {
                            Text(reais(oldRanks.sumOf { it.totalCents }), color = Green,
                                fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                            Subtext("${oldRanks.size} participantes • ${oldRanks.count { it.eligible }} atingiram a meta")
                        }
                    }
                    item { Section("Ranking arquivado", "SOMENTE CONSULTA") }
                    if (oldRanks.isEmpty()) item { Empty("Nenhuma troca registrada nesse mês.") }
                    itemsIndexed(oldRanks, key = { _, person -> person.key }) { index, person ->
                        RankingCard(index + 1, person, onOpen = null)
                    }
                    item { Section("Trocas do mês", "REGISTROS ARQUIVADOS") }
                    if (oldExchanges.isEmpty()) item { Empty("Nenhuma troca nesse mês.") }
                    items(oldExchanges, key = { it.id }) { EntryCard(it) }
                    item { Section("Presentes registrados", "RESULTADOS ARQUIVADOS") }
                    if (oldAwards.isEmpty()) item { Empty("Nenhum sorteio registrado nesse mês.") }
                    items(oldAwards, key = { it.month + it.personKey }) { award ->
                        AwardCard(award, onDelivery = { onDelivery(award.month, award.personKey) })
                    }
                }
                "ajustes" -> {
                    item { Section("Backup e dados", "CONFIGURAÇÕES") }
                    item {
                        AppCard {
                            Text("Exportar backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Subtext("Guarde uma cópia das trocas, presentes e sorteios fora do celular.")
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onExport) { Text("Salvar arquivo JSON") }
                        }
                    }
                    item {
                        AppCard {
                            Text("Restaurar backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Subtext("Importar substitui os dados atuais somente após confirmação.")
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onImport) { Text("Selecionar arquivo JSON") }
                        }
                    }
                    item { Banner("Seus dados ficam apenas neste aparelho, sem login, internet ou sincronização. Faça backups regularmente.") }
                }
            }
        }
    }
}

@Composable
private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { Column(Modifier.fillMaxWidth().padding(19.dp), content = content) }
}

@Composable
private fun Section(title: String, caption: String) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(caption, color = BodyGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}
@Composable
private fun Subtext(text: String) {
    Text(text, color = BodyGray, fontSize = 13.sp, lineHeight = 20.sp)
}
@Composable
private fun Banner(text: String, warning: Boolean = false) {
    Surface(color = if (warning) Color(0xFFFFECEA) else Mint, shape = RoundedCornerShape(14.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(15.dp),
            color = if (warning) Color(0xFF983529) else DarkGreen, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
@Composable
private fun Empty(text: String) {
    AppCard {
        Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            fontSize = 13.sp, color = BodyGray, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeSummary(ranks: List<PersonRank>, onRanking: () -> Unit, onRegister: () -> Unit, onHistory: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Green, shape = RoundedCornerShape(19.dp)) {
            Column(Modifier.fillMaxWidth().padding(23.dp)) {
                Text("TOTAL TROCADO NO MÊS", fontSize = 11.sp, color = Color(0xFFC8E9DC), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text(reais(ranks.sumOf { it.totalCents }), fontSize = 33.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("Notas de R$ 5 e R$ 10", fontSize = 12.sp, color = Color(0xFFC8E9DC))
            }
        }
        AppCard {
            Text("${ranks.size} participantes  •  ${ranks.count { it.eligible }} elegíveis",
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Meta: R$ 150,00 ou mais por pessoa no mês", color = Gold, fontSize = 13.sp)
            Spacer(Modifier.height(13.dp))
            Button(onClick = onRanking) { Text("Ver ranking e sorteios →") }
            TextButton(onClick = onHistory) { Text("Consultar histórico dos meses anteriores →") }
        }
        AppCard {
            Text("QUEM LIDERA?", color = BodyGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            if (ranks.isNotEmpty()) {
                Text(ranks.first().name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(reais(ranks.first().totalCents), color = Green, fontWeight = FontWeight.Bold)
            } else Subtext("Nenhuma troca neste mês.")
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRegister) { Text("+ Registrar troca") }
        }
    }
}

@Composable
private fun RegisterForm(existing: List<Exchange>, month: YearMonth,
                         onRegister: (String, LocalDate, Int, Int) -> Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    val suggestedDate = if (YearMonth.now() == month) LocalDate.now() else month.atDay(1)
    var dateText by rememberSaveable(month.toString()) { mutableStateOf(suggestedDate.toString()) }
    var notes5 by rememberSaveable { mutableStateOf("") }
    var notes10 by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val date = LocalDate.parse(dateText)
    val context = LocalContext.current
    val five = notes5.toIntOrNull() ?: 0
    val ten = notes10.toIntOrNull() ?: 0
    val total = five.toLong() * 500L + ten.toLong() * 1000L
    AppCard {
        Subtext("Informe quantas notas de R$ 5 e R$ 10 foram trocadas. Pode usar apenas um dos valores; não há quantidade mínima de cada nota. O total é automático.")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= 80) name = it },
            label = { Text("Nome da pessoa") }, modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        val suggestions = remember(existing) {
            existing.map { it.name }.distinctBy { TrocoRules.personKey(it) }.takeLast(5)
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Nomes recentes:", color = BodyGray, fontSize = 11.sp)
            suggestions.forEach { previous ->
                TextButton(onClick = { name = previous }, contentPadding = PaddingValues(3.dp)) {
                    Text(previous, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            DatePickerDialog(context, { _, year, m, day ->
                dateText = LocalDate.of(year, m + 1, day).toString()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }) { Text("Data: ${date.format(dateFormat)} ▾") }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = notes5, onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) notes5 = it },
                label = { Text("Notas de R$ 5") }, placeholder = { Text("0") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = notes10, onValueChange = { if (it.length <= 7 && it.all(Char::isDigit)) notes10 = it },
                label = { Text("Notas de R$ 10") }, placeholder = { Text("0") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(color = Mint, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("VALOR CALCULADO", fontSize = 11.sp, color = Green, fontWeight = FontWeight.Bold)
                Text(reais(total), fontSize = 25.sp, color = Green, fontWeight = FontWeight.ExtraBold)
                Text("$five × R$ 5 + $ten × R$ 10", color = BodyGray, fontSize = 12.sp)
            }
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Text(error, color = Color(0xFFA3312B), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            error = if (!TrocoRules.isValidExchange(name, dateText, five, ten) ||
                    (notes5.isNotEmpty() && notes5.toIntOrNull() == null) ||
                    (notes10.isNotEmpty() && notes10.toIntOrNull() == null))
                "Informe nome e quantidades válidas. Pelo menos uma nota deve ser registrada."
            else ""
            if (error.isEmpty() && onRegister(name, date, five, ten)) {
                name = ""; notes5 = ""; notes10 = ""; dateText = suggestedDate.toString()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("+ Salvar troca") }
    }
}

@Composable
private fun EntryCard(exchange: Exchange, onDelete: (() -> Unit)? = null) {
    var confirming by remember { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exchange.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${LocalDate.parse(exchange.date).format(dateFormat)} · ${exchange.notes5} notas de 5 · ${exchange.notes10} notas de 10",
                    color = BodyGray, fontSize = 11.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(reais(exchange.totalCents), fontWeight = FontWeight.ExtraBold, color = Green)
        }
        if (onDelete != null) {
            TextButton(onClick = { confirming = true }, modifier = Modifier.align(Alignment.End)) {
                Text("Excluir lançamento")
            }
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Excluir troca?") },
        text = { Text("Esta troca será excluída. Sorteios já realizados serão preservados.") },
        confirmButton = { TextButton(onClick = { confirming = false; onDelete?.invoke() }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun RankingCard(position: Int, person: PersonRank, onOpen: (() -> Unit)?) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (position == 1) Color(0xFFFFEEC9) else Mint,
                shape = RoundedCornerShape(13.dp)) {
                Text("#$position", modifier = Modifier.padding(11.dp), fontWeight = FontWeight.Bold,
                    color = if (position == 1) Gold else Green)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${person.exchangeCount} trocas", color = BodyGray, fontSize = 12.sp)
            }
            Text(reais(person.totalCents), fontWeight = FontWeight.Bold, color = Green)
        }
        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = PageBg)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val status = when {
                person.award != null -> "Presente sorteado"
                person.eligible -> if (onOpen == null) "Meta atingida neste mês" else "Elegível ao presente"
                else -> "Faltam ${reais(REWARD_THRESHOLD_CENTS - person.totalCents)}"
            }
            Text(status, modifier = Modifier.weight(1f), fontSize = 12.sp,
                color = if (person.eligible) Green else BodyGray)
            if (onOpen != null && (person.eligible || person.award != null)) TextButton(onClick = onOpen) {
                Text(if (person.award != null) "Ver presente →" else "Sortear →")
            }
        }
    }
}

@Composable
private fun AwardCard(award: Award, onDelivery: () -> Boolean) {
    AppCard {
        Text(award.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("🎁 ${award.giftName}", color = Green, fontSize = 15.sp)
        Subtext(if (award.delivered) "Presente entregue" else "Aguardando entrega")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { onDelivery() }) {
            Text(if (award.delivered) "Desfazer entrega" else "Marcar como entregue")
        }
    }
}

@Composable
private fun DrawCard(person: PersonRank?, award: Award?, gifts: List<Gift>,
                     onDraw: () -> Boolean, onDelivery: () -> Boolean) {
    var concealed by remember(person?.key, award?.month) { mutableStateOf(false) }
    LaunchedEffect(concealed) {
        if (concealed) { delay(1400); concealed = false }
    }
    AppCard {
        Text("SORTEIO DE PRESENTE", modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Text("🎁", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 62.sp)
        Text(person?.name ?: award?.name.orEmpty(), modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text(person?.let { reais(it.totalCents) } ?: "Sorteio salvo",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Green)
        Spacer(Modifier.height(18.dp))
        Surface(color = Mint, shape = RoundedCornerShape(14.dp)) {
            Text(
                when {
                    concealed -> "Sorteando…"
                    award != null -> "🎁 ${award.giftName}"
                    gifts.isEmpty() -> "Cadastre pelo menos um presente."
                    else -> "Qual presente vai sair?"
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 35.dp, horizontal = 15.dp),
                textAlign = TextAlign.Center, color = Green, fontWeight = FontWeight.Bold, fontSize = 20.sp
            )
        }
        Spacer(Modifier.height(15.dp))
        if (award != null && !concealed) {
            Subtext(if (award.delivered) "Presente entregue ✓" else "Aguardando entrega")
            OutlinedButton(onClick = { onDelivery() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (award.delivered) "Desfazer entrega" else "Marcar entregue")
            }
        } else if (award == null) {
            Button(
                onClick = { if (onDraw()) concealed = true },
                enabled = !concealed && person?.eligible == true && gifts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sortear presente") }
        }
    }
    Spacer(Modifier.height(7.dp))
    Subtext("Um único sorteio por pessoa em cada mês. O resultado fica salvo.")
}

@Composable
private fun GiftForm(onAdd: (String) -> Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    AppCard {
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Nome do presente") },
            placeholder = { Text("Ex.: Caixa de bombom") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (onAdd(name)) name = "" }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Adicionar presente")
        }
    }
}

@Composable
private fun GiftCard(gift: Gift, onEdit: (String, String) -> Boolean, onDelete: (String) -> Boolean) {
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var draft by remember(gift.name) { mutableStateOf(gift.name) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", fontSize = 24.sp)
            Spacer(Modifier.width(9.dp))
            Text(gift.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { draft = gift.name; editing = true }) { Text("Editar") }
            TextButton(onClick = { deleting = true }) { Text("Excluir") }
        }
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("Editar presente") },
        text = { OutlinedTextField(value = draft, onValueChange = {
            if (it.length <= 80) draft = it
        }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (onEdit(gift.id, draft)) editing = false }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } }
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Excluir presente?") },
        text = { Text("Não será sorteado novamente, mas os sorteios anteriores serão mantidos.") },
        confirmButton = { TextButton(onClick = { if (onDelete(gift.id)) deleting = false }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } }
    )
}
