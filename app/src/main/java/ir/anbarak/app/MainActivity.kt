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
import androidx.compose.animation.fadeOut
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
