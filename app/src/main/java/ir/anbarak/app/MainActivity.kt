package ir.anbarak.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class Product(val id: Long, val name: String, val quantity: Int, val minQuantity: Int)

class InventoryDb(context: Context) : SQLiteOpenHelper(context, "anbarak.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 0, min_quantity INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE movements (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun allProducts(): List<Product> {
        val result = mutableListOf<Product>()
        readableDatabase.rawQuery("SELECT id, name, quantity, min_quantity FROM products ORDER BY name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) result.add(Product(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3)))
        }
        return result
    }

    fun addProduct(name: String, minimum: Int): Boolean {
        if (name.isBlank() || minimum < 0) return false
        val values = ContentValues().apply {
            put("name", name.trim())
            put("quantity", 0)
            put("min_quantity", minimum)
        }
        return writableDatabase.insert("products", null, values) != -1L
    }

    fun moveStock(product: Product, amount: Int, isEntry: Boolean): Boolean {
        if (amount <= 0 || (!isEntry && amount > product.quantity)) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            val newQuantity = if (isEntry) product.quantity + amount else product.quantity - amount
            val values = ContentValues().apply { put("quantity", newQuantity) }
            db.update("products", values, "id=?", arrayOf(product.id.toString()))
            val movement = ContentValues().apply {
                put("product_id", product.id)
                put("type", if (isEntry) "IN" else "OUT")
                put("amount", amount)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("movements", null, movement)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = InventoryDb(this)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    InventoryScreen(db)
                }
            }
        }
    }
}

@Composable
fun InventoryScreen(db: InventoryDb) {
    var products by remember { mutableStateOf(db.allProducts()) }
    var productName by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("2") }
    var message by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Product?>(null) }
    var amount by remember { mutableStateOf("1") }
    var isEntry by remember { mutableStateOf(true) }

    fun refresh() { products = db.allProducts() }

    Scaffold(
        topBar = {
            TopAppBarCompat(
                title = "انبارک",
                subtitle = "مدیریت آفلاین موجودی کالا"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("تعداد کالاها", products.size.toString(), Modifier.weight(1f))
                SummaryCard("کم‌موجود", products.count { it.quantity <= it.minQuantity }.toString(), Modifier.weight(1f))
            }

            Text("افزودن کالای جدید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = productName, onValueChange = { productName = it },
                label = { Text("نام کالا") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = minimum, onValueChange = { minimum = it.filter(Char::isDigit) },
                label = { Text("حداقل موجودی برای هشدار") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Button(
                onClick = {
                    val ok = db.addProduct(productName, minimum.toIntOrNull() ?: 0)
                    message = if (ok) "کالا اضافه شد." else "نام کالا را وارد کن."
                    if (ok) { productName = ""; refresh() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ثبت کالا") }

            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)

            Divider()
            Text("فهرست کالاها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (products.isEmpty()) {
                Text("هنوز کالایی ثبت نشده است. از فرم بالا شروع کن.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(products, key = { it.id }) { product ->
                        ProductCard(product = product, onMove = {
                            selected = product
                            amount = "1"
                            isEntry = true
                        })
                    }
                }
            }
        }
    }

    selected?.let { product ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("ثبت گردش کالا: ${product.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("موجودی فعلی: ${product.quantity}")
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter(Char::isDigit) },
                        label = { Text("تعداد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isEntry, onClick = { isEntry = true })
                        Text("ورود")
                        Spacer(Modifier.width(12.dp))
                        RadioButton(selected = !isEntry, onClick = { isEntry = false })
                        Text("خروج")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = db.moveStock(product, amount.toIntOrNull() ?: 0, isEntry)
                    message = if (ok) "موجودی به‌روزرسانی شد." else "تعداد نامعتبر است یا موجودی کافی نیست."
                    selected = null
                    refresh()
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("انصراف") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarCompat(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProductCard(product: Product, onMove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("موجودی: ${product.quantity}  |  حداقل: ${product.minQuantity}")
            if (product.quantity <= product.minQuantity) {
                Text("نیاز به بررسی موجودی", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onMove, modifier = Modifier.fillMaxWidth()) { Text("ثبت ورود / خروج") }
        }
    }
}
