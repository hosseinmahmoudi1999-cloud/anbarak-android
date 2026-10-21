package ir.anbarak.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Warehouse(val id: Long, val name: String, val note: String)
data class Product(val id: Long, val code: String, val name: String, val unit: String, val minimum: Int)
data class StockLine(val productId: Long, val code: String, val name: String, val unit: String, val quantity: Int, val minimum: Int)
data class Movement(val id: Long, val doc: String, val type: String, val product: String, val amount: Int, val from: String, val to: String, val note: String, val time: Long)

class InventoryDb(context: Context) : SQLiteOpenHelper(context, "anbarak.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE warehouses(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, unit TEXT NOT NULL DEFAULT 'عدد', min_quantity INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE stock(warehouse_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(warehouse_id,product_id))")
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY AUTOINCREMENT, document_no TEXT NOT NULL, type TEXT NOT NULL, product_id INTEGER NOT NULL, amount INTEGER NOT NULL, from_warehouse_id INTEGER, to_warehouse_id INTEGER, note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)")
        db.execSQL("INSERT INTO warehouses(name,note) VALUES('انبار اصلی','انبار پیش‌فرض')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE warehouses(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, note TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE stock(warehouse_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(warehouse_id,product_id))")
            db.execSQL("INSERT INTO warehouses(name,note) VALUES('انبار اصلی','انبار پیش‌فرض')")
            val wid = db.rawQuery("SELECT id FROM warehouses LIMIT 1", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 1L }
            db.execSQL("ALTER TABLE products ADD COLUMN code TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE products ADD COLUMN unit TEXT NOT NULL DEFAULT 'عدد'")
            db.execSQL("ALTER TABLE products ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
            db.execSQL("INSERT INTO stock(warehouse_id,product_id,quantity) SELECT ?,id,quantity FROM products", arrayOf<Any>(wid))
            db.execSQL("CREATE TABLE movements_new(id INTEGER PRIMARY KEY AUTOINCREMENT, document_no TEXT NOT NULL DEFAULT '', type TEXT NOT NULL, product_id INTEGER NOT NULL, amount INTEGER NOT NULL, from_warehouse_id INTEGER, to_warehouse_id INTEGER, note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)")
            db.execSQL("INSERT INTO movements_new(id,document_no,type,product_id,amount,to_warehouse_id,note,created_at) SELECT id,'قدیمی-'||id,type,product_id,amount,?,'',created_at FROM movements", arrayOf<Any>(wid))
            db.execSQL("DROP TABLE movements")
            db.execSQL("ALTER TABLE movements_new RENAME TO movements")
        }
    }
    fun warehouses(): List<Warehouse> = buildList {
        readableDatabase.rawQuery("SELECT id,name,note FROM warehouses ORDER BY id", null).use { c -> while (c.moveToNext()) add(Warehouse(c.getLong(0), c.getString(1), c.getString(2))) }
    }
    fun addWarehouse(name: String, note: String): Boolean {
        if (name.isBlank()) return false
        return writableDatabase.insert("warehouses", null, ContentValues().apply { put("name", name.trim()); put("note", note.trim()) }) != -1L
    }
    fun products(): List<Product> = buildList {
        readableDatabase.rawQuery("SELECT id,code,name,unit,min_quantity FROM products WHERE active=1 ORDER BY name COLLATE NOCASE", null).use { c -> while (c.moveToNext()) add(Product(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4))) }
    }
    fun addProduct(code: String, name: String, unit: String, min: Int): Boolean {
        if (name.isBlank() || min < 0) return false
        return writableDatabase.insert("products", null, ContentValues().apply { put("code", code.trim()); put("name", name.trim()); put("unit", unit.trim().ifBlank { "عدد" }); put("min_quantity", min) }) != -1L
    }
    fun updateProduct(p: Product, code: String, name: String, unit: String, min: Int): Boolean {
        if (name.isBlank() || min < 0) return false
        return writableDatabase.update("products", ContentValues().apply { put("code", code.trim()); put("name", name.trim()); put("unit", unit.trim().ifBlank { "عدد" }); put("min_quantity", min) }, "id=?", arrayOf(p.id.toString())) > 0
    }
    fun stockFor(wid: Long, search: String = ""): List<StockLine> = buildList {
        val q = "%${search.trim()}%"
        readableDatabase.rawQuery("SELECT p.id,p.code,p.name,p.unit,COALESCE(s.quantity,0),p.min_quantity FROM products p LEFT JOIN stock s ON s.product_id=p.id AND s.warehouse_id=? WHERE p.active=1 AND (p.name LIKE ? OR p.code LIKE ?) ORDER BY p.name COLLATE NOCASE", arrayOf(wid.toString(), q, q)).use { c ->
            while (c.moveToNext()) add(StockLine(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getInt(5)))
        }
    }
    fun quantity(wid: Long, pid: Long): Int = readableDatabase.rawQuery("SELECT quantity FROM stock WHERE warehouse_id=? AND product_id=?", arrayOf(wid.toString(), pid.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    fun saveMovement(type: String, pid: Long, amount: Int, fromId: Long?, toId: Long?, note: String): String? {
        if (amount <= 0) return "تعداد باید بیشتر از صفر باشد."
        if ((type == "OUT" || type == "TRANSFER") && (fromId == null || quantity(fromId, pid) < amount)) return "موجودی انبار مبدأ کافی نیست."
        if (type == "IN" && toId == null) return "انبار مقصد را انتخاب کن."
        if (type == "TRANSFER" && (fromId == null || toId == null || fromId == toId)) return "انبار مبدأ و مقصد باید متفاوت باشند."
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (type == "IN") changeStock(db, toId!!, pid, amount)
            if (type == "OUT") changeStock(db, fromId!!, pid, -amount)
            if (type == "TRANSFER") { changeStock(db, fromId!!, pid, -amount); changeStock(db, toId!!, pid, amount) }
            val next = db.rawQuery("SELECT COALESCE(MAX(id),0)+1 FROM movements", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 1L }
            val doc = "AN-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}-%04d".format(next)
            db.insertOrThrow("movements", null, ContentValues().apply {
                put("document_no", doc); put("type", type); put("product_id", pid); put("amount", amount)
                if (fromId == null) putNull("from_warehouse_id") else put("from_warehouse_id", fromId)
                if (toId == null) putNull("to_warehouse_id") else put("to_warehouse_id", toId)
                put("note", note.trim()); put("created_at", System.currentTimeMillis())
            })
            db.setTransactionSuccessful(); return null
        } catch (e: Exception) { return "ثبت سند ناموفق بود: ${e.message ?: "خطای پایگاه داده"}" }
        finally { db.endTransaction() }
    }
    private fun changeStock(db: SQLiteDatabase, wid: Long, pid: Long, delta: Int) {
        val current = db.rawQuery("SELECT quantity FROM stock WHERE warehouse_id=? AND product_id=?", arrayOf(wid.toString(), pid.toString())).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        db.insertWithOnConflict("stock", null, ContentValues().apply { put("warehouse_id", wid); put("product_id", pid); put("quantity", current + delta) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun movements(): List<Movement> = buildList {
        readableDatabase.rawQuery("SELECT m.id,m.document_no,m.type,p.name,m.amount,COALESCE(wf.name,''),COALESCE(wt.name,''),m.note,m.created_at FROM movements m JOIN products p ON p.id=m.product_id LEFT JOIN warehouses wf ON wf.id=m.from_warehouse_id LEFT JOIN warehouses wt ON wt.id=m.to_warehouse_id ORDER BY m.id DESC LIMIT 300", null).use { c ->
            while (c.moveToNext()) add(Movement(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getString(5), c.getString(6), c.getString(7), c.getLong(8)))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = InventoryDb(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF176B67), onPrimary = Color.White,
                    secondary = Color(0xFF2D8C83), tertiary = Color(0xFFF2B84B),
                    background = Color(0xFFF4F7F8), surface = Color.White,
                    surfaceVariant = Color(0xFFE8F0F0),
                    onSurface = Color(0xFF172B35), onSurfaceVariant = Color(0xFF657781),
                    error = Color(0xFFCE4E52)
                ),
                typography = Typography(
                    headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
                    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AnbarakApp(db) }
            }
        }
    }
}

@Composable
fun AnbarakApp(db: InventoryDb) {
    var page by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var activeId by remember { mutableLongStateOf(db.warehouses().firstOrNull()?.id ?: 1L) }
    var addProduct by remember { mutableStateOf(false) }
    var addWarehouse by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var moving by remember { mutableStateOf<Product?>(null) }
    var moveType by remember { mutableStateOf("IN") }
    var message by remember { mutableStateOf("") }
    val warehouses = remember(refresh) { db.warehouses() }
    val products = remember(refresh) { db.products() }
    LaunchedEffect(warehouses) { if (warehouses.isNotEmpty() && warehouses.none { it.id == activeId }) activeId = warehouses.first().id }
    Scaffold(
        containerColor = Color(0xFFF4F7F8),
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("انبارک", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF176B67))
                    Text("مدیریت هوشمند موجودی", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(48.dp).background(Color(0xFFE4F3F0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text("▦", color = Color(0xFF176B67), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 10.dp) {
                NavigationBarItem(page == 0, { page = 0 }, icon = { Text("⌂", fontSize = 22.sp) }, label = { Text("خانه") }, colors = NavigationBarItemDefaults.colors(selectedIndicatorColor = Color(0xFFDDF2EE)))
                NavigationBarItem(page == 1, { page = 1 }, icon = { Text("▤", fontSize = 20.sp) }, label = { Text("کالاها") }, colors = NavigationBarItemDefaults.colors(selectedIndicatorColor = Color(0xFFDDF2EE)))
                NavigationBarItem(page == 2, { page = 2 }, icon = { Text("⇄", fontSize = 22.sp) }, label = { Text("گردش اسناد") }, colors = NavigationBarItemDefaults.colors(selectedIndicatorColor = Color(0xFFDDF2EE)))
                NavigationBarItem(page == 3, { page = 3 }, icon = { Text("▥", fontSize = 20.sp) }, label = { Text("انبارها") }, colors = NavigationBarItemDefaults.colors(selectedIndicatorColor = Color(0xFFDDF2EE)))
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            AnimatedVisibility(visible = message.isNotBlank(), enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 3 }) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE4F3F0)), shape = RoundedCornerShape(14.dp)) {
                    Text(message, color = Color(0xFF176B67), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                }
            }
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 } togetherWith fadeIn(tween(120)) },
                label = "page-transition"
            ) { shownPage ->
                when (shownPage) {
                    0 -> Dashboard(db, warehouses, activeId, { activeId = it }, refresh)
                    1 -> ProductsPage(db, activeId, products, refresh, { addProduct = true }, { p -> editing = p }, { p, t -> moving = p; moveType = t })
                    2 -> MovementsPage(db, refresh)
                    else -> WarehousesPage(warehouses, activeId, { activeId = it }, { addWarehouse = true })
                }
            }
        }
    }
    if (addProduct) ProductDialog(null, { addProduct = false }) { c,n,u,m -> message = if (db.addProduct(c,n,u,m)) "کالا ثبت شد." else "ثبت کالا ناموفق بود؛ نام را بررسی کن."; addProduct = false; refresh++ }
    if (editing != null) ProductDialog(editing, { editing = null }) { c,n,u,m -> message = if (db.updateProduct(editing!!,c,n,u,m)) "کالا ویرایش شد." else "ویرایش ناموفق بود."; editing = null; refresh++ }
    if (addWarehouse) WarehouseDialog({ addWarehouse = false }) { n,note -> message = if (db.addWarehouse(n,note)) "انبار ثبت شد." else "ثبت انبار ناموفق بود؛ نام را بررسی کن."; addWarehouse = false; refresh++ }
    moving?.let { p -> MovementDialog(p, warehouses, activeId, moveType, { moving = null }) { t,a,f,to,n -> message = db.saveMovement(t,p.id,a,f,to,n) ?: "سند با موفقیت ثبت شد."; moving = null; refresh++ } }
}

@Composable
fun Dashboard(db: InventoryDb, warehouses: List<Warehouse>, activeId: Long, onSelect: (Long) -> Unit, refresh: Int) {
    val lines = remember(activeId, refresh) { db.stockFor(activeId) }
    val allMoves = remember(refresh) { db.movements() }
    val productCount = db.products().size
    val totalStock = lines.sumOf { it.quantity }
    val lowCount = lines.count { it.quantity <= it.minimum }
    val incoming = allMoves.count { it.type == "IN" }.coerceAtLeast(0)
    val outgoing = allMoves.count { it.type == "OUT" }.coerceAtLeast(0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(2.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF176B67))
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سلام! خوش آمدی", color = Color(0xFFD7F0EB), style = MaterialTheme.typography.bodyMedium)
                        Text("وضعیت انبار شما", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Box(Modifier.size(48.dp).background(Color(0x33FFFFFF), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Text("✦", color = Color(0xFFFFD16A), fontSize = 27.sp)
                    }
                }
                WarehousePicker(warehouses, activeId, onSelect)
                Text("اطلاعات این صفحه فقط روی همین دستگاه ذخیره می‌شود.", color = Color(0xFFD7F0EB), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("خلاصه موجودی", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("تعداد کالاها", productCount, "▤", Color(0xFFE6F3F1), Modifier.weight(1f))
            Metric("مجموع موجودی", totalStock, "▦", Color(0xFFFFF2D9), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Metric("نیازمند بررسی", lowCount, "!", Color(0xFFFFE8E7), Modifier.weight(1f), Color(0xFFC94F51))
            Metric("تعداد انبارها", warehouses.size, "⌂", Color(0xFFE9EDFF), Modifier.weight(1f), Color(0xFF5868B7))
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("نمای کلی گردش اسناد", style = MaterialTheme.typography.titleMedium)
                Text("بر اساس ۳۰۰ سند اخیر", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                AnimatedMovementBar("ورود کالا", incoming, (incoming + outgoing).coerceAtLeast(1), Color(0xFF2D9B85))
                AnimatedMovementBar("خروج کالا", outgoing, (incoming + outgoing).coerceAtLeast(1), Color(0xFFE6A43A))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("کالاهای نیازمند بررسی", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (lowCount > 0) Surface(color = Color(0xFFFFE8E7), shape = RoundedCornerShape(50)) { Text("$lowCount مورد", color = Color(0xFFC94F51), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
        }
        val low = lines.filter { it.quantity <= it.minimum }
        if (low.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F3F1))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("✓", color = Color(0xFF176B67), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Column { Text("همه‌چیز مرتب است", fontWeight = FontWeight.Bold, color = Color(0xFF176B67)); Text("در این انبار کالای کم‌موجودی ثبت نشده.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        low.take(20).forEach { item ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFFFFE8E7), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("!", color = Color(0xFFC94F51), fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text("حداقل موجودی: ${item.minimum} ${item.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${item.quantity}", color = Color(0xFFC94F51), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun AnimatedMovementBar(title: String, count: Int, total: Int, color: Color) {
    val fraction by animateFloatAsState(targetValue = count.toFloat() / total.toFloat(), animationSpec = tween(850), label = "movement-bar")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.width(84.dp), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f).height(10.dp).background(Color(0xFFF0F3F4), RoundedCornerShape(20.dp))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(20.dp)))
        }
        Text(count.toString(), modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ProductsPage(db: InventoryDb, wid: Long, products: List<Product>, refresh: Int, onAdd: () -> Unit, onEdit: (Product) -> Unit, onMove: (Product, String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val lines = remember(wid, search, refresh) { db.stockFor(wid, search) }
    Column(Modifier.fillMaxSize()) {
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
