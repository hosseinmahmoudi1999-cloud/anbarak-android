        Row(verticalAlignment = Alignment.CenterVertically) { Text("کالاها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Button(onClick = onAdd) { Text("کالای جدید +") } }
        OutlinedTextField(search, { search = it }, label = { Text("جست‌وجوی نام یا کد کالا") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (lines.isEmpty()) Text("کالایی پیدا نشد. از «کالای جدید» شروع کن.", modifier = Modifier.padding(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(lines, key = { it.productId }) { line ->
                val p = products.firstOrNull { it.id == line.productId }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(line.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("کد: ${line.code.ifBlank { "—" }} • واحد: ${line.unit}")
                    Text("موجودی این انبار: ${line.quantity} • حداقل: ${line.minimum}")
                    if (line.quantity <= line.minimum) Text("کم‌موجود", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { p?.let(onEdit) }, modifier = Modifier.weight(1f)) { Text("ویرایش") }
                        Button(onClick = { p?.let { onMove(it,"IN") } }, modifier = Modifier.weight(1f)) { Text("ورود") }
                        OutlinedButton(onClick = { p?.let { onMove(it,"OUT") } }, modifier = Modifier.weight(1f)) { Text("خروج") }
                        OutlinedButton(onClick = { p?.let { onMove(it,"TRANSFER") } }, modifier = Modifier.weight(1f)) { Text("انتقال") }
                    }
                } }
            }
        }
    }
}

@Composable
fun MovementsPage(db: InventoryDb, refresh: Int) {
    val list = remember(refresh) { db.movements() }
    Column(Modifier.fillMaxSize()) {
        Text("اسناد گردش کالا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("آخرین ۳۰۰ سند ثبت‌شده", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("هنوز سندی ثبت نشده است.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(list, key = { it.id }) { m ->
                val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(m.time))
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${m.doc} • ${when(m.type) { "IN" -> "ورود"; "OUT" -> "خروج"; else -> "انتقال" }}", fontWeight = FontWeight.Bold)
                    Text("${m.product} — تعداد: ${m.amount}")
                    if (m.from.isNotBlank()) Text("از: ${m.from}")
                    if (m.to.isNotBlank()) Text("به: ${m.to}")
                    if (m.note.isNotBlank()) Text("توضیحات: ${m.note}")
                    Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
}

@Composable
fun WarehousesPage(warehouses: List<Warehouse>, activeId: Long, onSelect: (Long) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("مدیریت انبارها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Button(onClick = onAdd) { Text("انبار جدید +") } }
        Text("برای دیدن موجودی یک انبار را انتخاب کن.")
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(warehouses, key = { it.id }) { w ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(w.name, fontWeight = FontWeight.Bold); if (w.note.isNotBlank()) Text(w.note, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (w.id == activeId) Text("فعال", color = MaterialTheme.colorScheme.primary) else OutlinedButton(onClick = { onSelect(w.id) }) { Text("انتخاب") }
            } }
        } }
    }
}

@Composable
fun Metric(title: String, value: Int, symbol: String, tint: Color, modifier: Modifier = Modifier, accent: Color = Color(0xFF176B67)) {
    val animatedValue by animateIntAsState(targetValue = value, animationSpec = tween(650), label = "metric-count")
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(tint, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(symbol, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(animatedValue.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B35))
        }
    }
}

@Composable
fun ProductDialog(existing: Product?, onDismiss: () -> Unit, onSave: (String,String,String,Int) -> Unit) {
    var code by remember(existing) { mutableStateOf(existing?.code ?: "") }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var unit by remember(existing) { mutableStateOf(existing?.unit ?: "عدد") }
    var min by remember(existing) { mutableStateOf((existing?.minimum ?: 0).toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "ثبت کالای جدید" else "ویرایش کالا") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(code, { code = it }, label = { Text("کد کالا") }, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("نام کالا *") }, singleLine = true)
            OutlinedTextField(unit, { unit = it }, label = { Text("واحد (عدد، کیلو، متر...) ") }, singleLine = true)
            OutlinedTextField(min, { min = it.filter(Char::isDigit) }, label = { Text("حداقل موجودی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { onSave(code,name,unit,min.toIntOrNull() ?: 0) }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
fun WarehouseDialog(onDismiss: () -> Unit, onSave: (String,String) -> Unit) {
    var name by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("افزودن انبار") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("نام انبار *") }, singleLine = true)
        OutlinedTextField(note, { note = it }, label = { Text("توضیحات") }, singleLine = true)
    } }, confirmButton = { TextButton(onClick = { onSave(name,note) }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
fun MovementDialog(product: Product, warehouses: List<Warehouse>, initialId: Long, initialType: String, onDismiss: () -> Unit, onSave: (String,Int,Long?,Long?,String) -> Unit) {
    var type by remember { mutableStateOf(initialType) }; var amount by remember { mutableStateOf("1") }; var note by remember { mutableStateOf("") }
    var from by remember { mutableLongStateOf(initialId) }; var to by remember { mutableLongStateOf(warehouses.firstOrNull { it.id != initialId }?.id ?: initialId) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("سند کالا: ${product.name}") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(type == "IN", { type = "IN" }, label = { Text("ورود") })
                FilterChip(type == "OUT", { type = "OUT" }, label = { Text("خروج") })
                FilterChip(type == "TRANSFER", { type = "TRANSFER" }, label = { Text("انتقال") })
            }
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("تعداد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            if (type == "OUT" || type == "TRANSFER") { Text("انبار مبدأ"); WarehousePicker(warehouses, from) { from = it } }
            if (type == "IN" || type == "TRANSFER") { Text("انبار مقصد"); WarehousePicker(warehouses, to) { to = it } }
            OutlinedTextField(note, { note = it }, label = { Text("توضیحات سند") })
        }
    }, confirmButton = { TextButton(onClick = { onSave(type, amount.toIntOrNull() ?: 0, if (type == "OUT" || type == "TRANSFER") from else null, if (type == "IN" || type == "TRANSFER") to else null, note) }) { Text("ثبت سند") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
fun WarehousePicker(warehouses: List<Warehouse>, selectedId: Long, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(warehouses.firstOrNull { it.id == selectedId }?.name ?: "انتخاب انبار") }
        DropdownMenu(expanded, { expanded = false }) { warehouses.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { onSelect(w.id); expanded = false }) } }
    }
}
