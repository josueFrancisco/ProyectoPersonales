package com.example.aplicacionpersonal

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplicacionpersonal.ui.theme.AplicacionPersonalTheme
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

data class WishItem(val id: String = UUID.randomUUID().toString(), val name: String, val description: String, val price: Double?, val store: String, val purchased: Boolean = false)
data class TaskItem(val id: String = UUID.randomUUID().toString(), val title: String, val details: String, val due: String, val priority: String, val completed: Boolean = false)
data class FavoriteItem(val id: String = UUID.randomUUID().toString(), val name: String, val category: String, val notes: String, val link: String)

private class PersonalRepository(context: Context) {
    private val wishesPrefs = context.getSharedPreferences("wish_list", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("personal_hub", Context.MODE_PRIVATE)

    fun loadWishes(): List<WishItem> = parse("items", wishesPrefs) { item -> WishItem(item.getString("id"), item.getString("name"), item.optString("description"), if (item.isNull("price")) null else item.getDouble("price"), item.optString("store"), item.optBoolean("purchased")) }
    fun saveWishes(items: List<WishItem>) = save("items", wishesPrefs, items) { wish -> JSONObject().put("id", wish.id).put("name", wish.name).put("description", wish.description).put("price", wish.price ?: JSONObject.NULL).put("store", wish.store).put("purchased", wish.purchased) }
    fun loadTasks(): List<TaskItem> = parse("tasks", prefs) { item -> TaskItem(item.getString("id"), item.getString("title"), item.optString("details"), item.optString("due"), item.optString("priority", "Media"), item.optBoolean("completed")) }
    fun saveTasks(items: List<TaskItem>) = save("tasks", prefs, items) { task -> JSONObject().put("id", task.id).put("title", task.title).put("details", task.details).put("due", task.due).put("priority", task.priority).put("completed", task.completed) }
    fun loadFavorites(): List<FavoriteItem> = parse("favorites", prefs) { item -> FavoriteItem(item.getString("id"), item.getString("name"), item.optString("category"), item.optString("notes"), item.optString("link")) }
    fun saveFavorites(items: List<FavoriteItem>) = save("favorites", prefs, items) { favorite -> JSONObject().put("id", favorite.id).put("name", favorite.name).put("category", favorite.category).put("notes", favorite.notes).put("link", favorite.link) }

    private fun <T> parse(key: String, storage: android.content.SharedPreferences, map: (JSONObject) -> T): List<T> = runCatching {
        val array = JSONArray(storage.getString(key, "[]")); List(array.length()) { map(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    private fun <T> save(key: String, storage: android.content.SharedPreferences, items: List<T>, map: (T) -> JSONObject) {
        val array = JSONArray(); items.forEach { array.put(map(it)) }; storage.edit().putString(key, array.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val repository = PersonalRepository(applicationContext)
        setContent { AplicacionPersonalTheme { PersonalHub(repository) } }
    }
}

private enum class Section(val title: String, val short: String) { WISHES("Compras", "◇"), TASKS("Tareas", "✓"), FAVORITES("Favoritos", "♡") }

@Composable
private fun PersonalHub(repository: PersonalRepository) {
    var section by rememberSaveable { mutableStateOf(Section.WISHES) }
    var wishes by remember { mutableStateOf(repository.loadWishes()) }
    var tasks by remember { mutableStateOf(repository.loadTasks()) }
    var favorites by remember { mutableStateOf(repository.loadFavorites()) }
    var editingWish by remember { mutableStateOf<WishItem?>(null) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var editingFavorite by remember { mutableStateOf<FavoriteItem?>(null) }
    var formOpen by rememberSaveable { mutableStateOf(false) }

    if (formOpen) {
        BackHandler { formOpen = false }
        when (section) {
            Section.WISHES -> WishForm(editingWish, { formOpen = false }) { value -> wishes = if (editingWish == null) wishes + value else wishes.map { if (it.id == value.id) value else it }; repository.saveWishes(wishes); formOpen = false }
            Section.TASKS -> TaskForm(editingTask, { formOpen = false }) { value -> tasks = if (editingTask == null) tasks + value else tasks.map { if (it.id == value.id) value else it }; repository.saveTasks(tasks); formOpen = false }
            Section.FAVORITES -> FavoriteForm(editingFavorite, { formOpen = false }) { value -> favorites = if (editingFavorite == null) favorites + value else favorites.map { if (it.id == value.id) value else it }; repository.saveFavorites(favorites); formOpen = false }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppNavigation(section) { section = it } },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingWish = null; editingTask = null; editingFavorite = null; formOpen = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onPrimary) }
        }
    ) { padding ->
        AnimatedContent(targetState = section, label = "section") { current ->
            when (current) {
                Section.WISHES -> WishesScreen(wishes, padding, onToggle = { target -> wishes = wishes.map { if (it.id == target.id) it.copy(purchased = !it.purchased) else it }; repository.saveWishes(wishes) }, onEdit = { editingWish = it; formOpen = true }, onDelete = { target -> wishes = wishes.filterNot { it.id == target.id }; repository.saveWishes(wishes) })
                Section.TASKS -> TasksScreen(tasks, padding, onToggle = { target -> tasks = tasks.map { if (it.id == target.id) it.copy(completed = !it.completed) else it }; repository.saveTasks(tasks) }, onEdit = { editingTask = it; formOpen = true }, onDelete = { target -> tasks = tasks.filterNot { it.id == target.id }; repository.saveTasks(tasks) })
                Section.FAVORITES -> FavoritesScreen(favorites, padding, onEdit = { editingFavorite = it; formOpen = true }, onDelete = { target -> favorites = favorites.filterNot { it.id == target.id }; repository.saveFavorites(favorites) })
            }
        }
    }
}

@Composable
private fun AppNavigation(selected: Section, onSelect: (Section) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Section.entries.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Text(item.short, fontSize = 23.sp, fontWeight = if (selected == item) FontWeight.Bold else FontWeight.Normal) },
                label = { Text(item.title, fontWeight = if (selected == item) FontWeight.SemiBold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

@Composable
private fun ScreenHeader(eyebrow: String, title: String, subtitle: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(count.toString().padStart(2, '0'), modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), placeholder = { Text(hint) }, leadingIcon = { Text("⌕", fontSize = 23.sp) }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface))
}

@Composable
private fun WishesScreen(items: List<WishItem>, padding: PaddingValues, onToggle: (WishItem) -> Unit, onEdit: (WishItem) -> Unit, onDelete: (WishItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; var purchased by rememberSaveable { mutableStateOf(false) }
    val visible = items.filter { it.purchased == purchased && (query.isBlank() || it.name.contains(query, true) || it.store.contains(query, true)) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Colección personal", "Próximas compras", "Guarda hoy lo que quieres mañana.", items.count { !it.purchased }); Spacer(Modifier.height(22.dp)); SearchField(query, { query = it }, "Buscar compra o tienda"); FilterRow(if (purchased) 1 else 0, listOf("Pendientes" to items.count { !it.purchased }, "Comprados" to items.count { it.purchased })) { purchased = it == 1 } }
        if (visible.isEmpty()) item { EmptyState("◇", if (query.isBlank()) "Nada por aquí todavía" else "Sin resultados", if (purchased) "Cuando completes una compra aparecerá aquí." else "Agrega algo que te gustaría comprar.") }
        items(visible, key = { it.id }) { item -> ElegantCard(onClick = { onEdit(item) }) { Row(verticalAlignment = Alignment.Top) { CheckControl(item.purchased) { onToggle(item) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (item.description.isNotBlank()) Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)); Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) { LabelPill(formatPrice(item.price)); if (item.store.isNotBlank()) Text(item.store, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) } }; ItemMenu({ onEdit(item) }, { onDelete(item) }) } } }
    }
}

@Composable
private fun TasksScreen(items: List<TaskItem>, padding: PaddingValues, onToggle: (TaskItem) -> Unit, onEdit: (TaskItem) -> Unit, onDelete: (TaskItem) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }; val visible = items.filter { if (mode == 0) !it.completed else it.completed }.sortedBy { priorityOrder(it.priority) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Enfoque diario", "Mis tareas", "Pequeños pasos, progreso real.", items.count { !it.completed }); FilterRow(mode, listOf("Por hacer" to items.count { !it.completed }, "Completadas" to items.count { it.completed })) { mode = it } }
        if (visible.isEmpty()) item { EmptyState("✓", if (mode == 0) "Todo está en orden" else "Aún no hay completadas", if (mode == 0) "Agrega una tarea cuando tengas algo pendiente." else "Las tareas terminadas aparecerán aquí.") }
        items(visible, key = { it.id }) { item -> ElegantCard(onClick = { onEdit(item) }) { Row(verticalAlignment = Alignment.Top) { CheckControl(item.completed) { onToggle(item) }; Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); PriorityDot(item.priority) }; if (item.details.isNotBlank()) Text(item.details, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp)); if (item.due.isNotBlank()) Text("○  ${item.due}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 13.dp)) }; ItemMenu({ onEdit(item) }, { onDelete(item) }) } } }
    }
}

@Composable
private fun FavoritesScreen(items: List<FavoriteItem>, padding: PaddingValues, onEdit: (FavoriteItem) -> Unit, onDelete: (FavoriteItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; val visible = items.filter { query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Archivo de inspiración", "Mis favoritos", "Lugares, ideas y cosas que hablan de ti.", items.size); Spacer(Modifier.height(22.dp)); SearchField(query, { query = it }, "Buscar favorito o categoría"); Spacer(Modifier.height(8.dp)) }
        if (visible.isEmpty()) item { EmptyState("♡", if (query.isBlank()) "Empieza tu colección" else "Sin resultados", "Guarda películas, lugares, música, ideas o cualquier cosa que te guste.") }
        items(visible, key = { it.id }) { item -> ElegantCard(onClick = { onEdit(item) }) { Row(verticalAlignment = Alignment.Top) { Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(categorySymbol(item.category), fontSize = 21.sp, color = MaterialTheme.colorScheme.primary) }; Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (item.category.isNotBlank()) Text(item.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)); if (item.notes.isNotBlank()) Text(item.notes, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp)); if (item.link.isNotBlank()) Text(item.link, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }; ItemMenu({ onEdit(item) }, { onDelete(item) }) } } }
    }
}

@Composable private fun FilterRow(selected: Int, options: List<Pair<String, Int>>, onSelect: (Int) -> Unit) { Row(Modifier.padding(top = 18.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { options.forEachIndexed { index, option -> FilterChip(selected == index, { onSelect(index) }, { Text("${option.first}  ${option.second}") }, shape = RoundedCornerShape(14.dp)) } } }
@Composable private fun ElegantCard(onClick: () -> Unit, content: @Composable () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Box(Modifier.padding(18.dp)) { content() } } }
@Composable private fun CheckControl(checked: Boolean, onClick: () -> Unit) { Surface(Modifier.size(28.dp).clickable(onClick = onClick), shape = CircleShape, color = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, border = if (checked) null else androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) { if (checked) Box(contentAlignment = Alignment.Center) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun LabelPill(text: String) { Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }
@Composable private fun PriorityDot(priority: String) { val color = when (priority) { "Alta" -> MaterialTheme.colorScheme.error; "Baja" -> Color(0xFF4E9B75); else -> Color(0xFFE6A23C) }; Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Text(priority, Modifier.padding(start = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } }
@Composable private fun ItemMenu(onEdit: () -> Unit, onDelete: () -> Unit) { var open by remember { mutableStateOf(false) }; Box { TextButton({ open = true }, contentPadding = PaddingValues(2.dp), modifier = Modifier.size(34.dp)) { Text("⋮", fontSize = 23.sp) }; DropdownMenu(open, { open = false }) { DropdownMenuItem({ Text("Editar") }, { open = false; onEdit() }); DropdownMenuItem({ Text("Eliminar", color = MaterialTheme.colorScheme.error) }, { open = false; onDelete() }) } } }
@Composable private fun EmptyState(symbol: String, title: String, text: String) { Column(Modifier.fillMaxWidth().padding(top = 58.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(Modifier.size(84.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Text(symbol, fontSize = 37.sp, color = MaterialTheme.colorScheme.primary) } }; Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp), lineHeight = 21.sp) } }

@Composable
private fun FormScaffold(title: String, action: String, onBack: () -> Unit, onAction: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { Surface(color = MaterialTheme.colorScheme.background) { Row(Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onBack, modifier = Modifier.size(45.dp), contentPadding = PaddingValues(0.dp)) { Text("‹", fontSize = 34.sp) }; Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)) } } }, bottomBar = { Surface(shadowElevation = 10.dp) { Button(onAction, Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp), shape = RoundedCornerShape(18.dp)) { Text(action, fontWeight = FontWeight.Bold) } } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { item { Column(content = content) } } }
}

@Composable
private fun WishForm(existing: WishItem?, onBack: () -> Unit, onSave: (WishItem) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }; var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }; var price by rememberSaveable { mutableStateOf(existing?.price?.toString().orEmpty()) }; var store by rememberSaveable { mutableStateOf(existing?.store.orEmpty()) }; var error by rememberSaveable { mutableStateOf(false) }
    FormScaffold(if (existing == null) "Nueva compra" else "Editar compra", "Guardar compra", onBack, { if (name.isBlank()) error = true else onSave(WishItem(existing?.id ?: UUID.randomUUID().toString(), name.trim(), description.trim(), price.replace(',', '.').toDoubleOrNull(), store.trim(), existing?.purchased ?: false)) }) { FormIntro("Un deseo para después", "Guarda los detalles que te ayudarán a decidir."); AppField(name, { name = it; error = false }, "Nombre *", error = error); AppField(description, { description = it }, "Descripción", lines = 3); AppField(price, { price = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, "Precio aproximado", keyboardType = KeyboardType.Decimal); AppField(store, { store = it }, "Tienda o lugar") }
}

@Composable
private fun TaskForm(existing: TaskItem?, onBack: () -> Unit, onSave: (TaskItem) -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }; var details by rememberSaveable { mutableStateOf(existing?.details.orEmpty()) }; var due by rememberSaveable { mutableStateOf(existing?.due.orEmpty()) }; var priority by rememberSaveable { mutableStateOf(existing?.priority ?: "Media") }; var error by rememberSaveable { mutableStateOf(false) }
    FormScaffold(if (existing == null) "Nueva tarea" else "Editar tarea", "Guardar tarea", onBack, { if (title.isBlank()) error = true else onSave(TaskItem(existing?.id ?: UUID.randomUUID().toString(), title.trim(), details.trim(), due.trim(), priority, existing?.completed ?: false)) }) { FormIntro("Aclara tu próximo paso", "Una tarea concreta siempre es más fácil de empezar."); AppField(title, { title = it; error = false }, "¿Qué tienes que hacer? *", error = error); AppField(details, { details = it }, "Notas", lines = 3); AppField(due, { due = it }, "Fecha o momento", hint = "Ej. Mañana, viernes, 20 de julio"); Text("Prioridad", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Baja", "Media", "Alta").forEach { option -> FilterChip(priority == option, { priority = option }, { Text(option) }, shape = RoundedCornerShape(14.dp)) } } }
}

@Composable
private fun FavoriteForm(existing: FavoriteItem?, onBack: () -> Unit, onSave: (FavoriteItem) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }; var category by rememberSaveable { mutableStateOf(existing?.category.orEmpty()) }; var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }; var link by rememberSaveable { mutableStateOf(existing?.link.orEmpty()) }; var error by rememberSaveable { mutableStateOf(false) }
    FormScaffold(if (existing == null) "Nuevo favorito" else "Editar favorito", "Guardar favorito", onBack, { if (name.isBlank()) error = true else onSave(FavoriteItem(existing?.id ?: UUID.randomUUID().toString(), name.trim(), category.trim(), notes.trim(), link.trim())) }) { FormIntro("Algo que te representa", "Guarda una referencia para volver a ella cuando quieras."); AppField(name, { name = it; error = false }, "Nombre *", error = error); AppField(category, { category = it }, "Categoría", hint = "Música, película, lugar, idea..."); AppField(notes, { notes = it }, "Por qué te gusta", lines = 3); AppField(link, { link = it }, "Enlace o referencia", keyboardType = KeyboardType.Uri) }
}

@Composable private fun ColumnScope.FormIntro(title: String, text: String) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)) }
@Composable private fun ColumnScope.AppField(value: String, onChange: (String) -> Unit, label: String, hint: String = "", lines: Int = 1, error: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) { OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(bottom = 18.dp), label = { Text(label) }, placeholder = { if (hint.isNotBlank()) Text(hint) }, singleLine = lines == 1, minLines = lines, maxLines = if (lines == 1) 1 else 5, isError = error, supportingText = if (error) ({ Text("Este campo es obligatorio") }) else null, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(17.dp)) }

private fun formatPrice(price: Double?) = price?.let { NumberFormat.getCurrencyInstance(Locale.US).format(it) } ?: "Sin precio"
private fun priorityOrder(priority: String) = when (priority) { "Alta" -> 0; "Media" -> 1; else -> 2 }
private fun categorySymbol(category: String) = when { category.contains("música", true) || category.contains("musica", true) -> "♪"; category.contains("película", true) || category.contains("serie", true) -> "▶"; category.contains("lugar", true) || category.contains("viaje", true) -> "⌖"; category.contains("libro", true) -> "▤"; else -> "♡" }
