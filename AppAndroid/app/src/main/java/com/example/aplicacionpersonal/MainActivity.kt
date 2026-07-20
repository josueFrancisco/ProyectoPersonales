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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.aplicacionpersonal.ui.theme.AplicacionPersonalTheme
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

data class WishItem(val id: String = UUID.randomUUID().toString(), val name: String, val description: String, val price: Double?, val store: String, val purchased: Boolean = false)
data class TaskItem(val id: String = UUID.randomUUID().toString(), val title: String, val details: String, val due: String, val priority: String, val completed: Boolean = false)
data class FavoriteItem(val id: String = UUID.randomUUID().toString(), val name: String, val category: String, val notes: String, val link: String)
data class AnimeItem(val apiId: String, val title: String, val image: String, val episodes: Int?, val rating: Double?, val synopsis: String, val watchStatus: String = "Quiero verlo")
data class RelatedAnime(val role: String, val anime: AnimeItem)

private class PersonalRepository(context: Context) {
    private val wishesPrefs = context.getSharedPreferences("wish_list", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("personal_hub", Context.MODE_PRIVATE)

    fun loadWishes(): List<WishItem> = parse("items", wishesPrefs) { item -> WishItem(item.getString("id"), item.getString("name"), item.optString("description"), if (item.isNull("price")) null else item.getDouble("price"), item.optString("store"), item.optBoolean("purchased")) }
    fun saveWishes(items: List<WishItem>) = save("items", wishesPrefs, items) { wish -> JSONObject().put("id", wish.id).put("name", wish.name).put("description", wish.description).put("price", wish.price ?: JSONObject.NULL).put("store", wish.store).put("purchased", wish.purchased) }
    fun loadTasks(): List<TaskItem> = parse("tasks", prefs) { item -> TaskItem(item.getString("id"), item.getString("title"), item.optString("details"), item.optString("due"), item.optString("priority", "Media"), item.optBoolean("completed")) }
    fun saveTasks(items: List<TaskItem>) = save("tasks", prefs, items) { task -> JSONObject().put("id", task.id).put("title", task.title).put("details", task.details).put("due", task.due).put("priority", task.priority).put("completed", task.completed) }
    fun loadFavorites(): List<FavoriteItem> = parse("favorites", prefs) { item -> FavoriteItem(item.getString("id"), item.getString("name"), item.optString("category"), item.optString("notes"), item.optString("link")) }
    fun saveFavorites(items: List<FavoriteItem>) = save("favorites", prefs, items) { favorite -> JSONObject().put("id", favorite.id).put("name", favorite.name).put("category", favorite.category).put("notes", favorite.notes).put("link", favorite.link) }
    fun loadAnime(): List<AnimeItem> = parse("anime", prefs) { item -> AnimeItem(item.getString("apiId"), item.getString("title"), item.optString("image"), item.optIntOrNull("episodes"), item.optDoubleOrNull("rating"), item.optString("synopsis"), item.optString("watchStatus", "Quiero verlo")) }
    fun saveAnime(items: List<AnimeItem>) = save("anime", prefs, items) { anime -> JSONObject().put("apiId", anime.apiId).put("title", anime.title).put("image", anime.image).put("episodes", anime.episodes ?: JSONObject.NULL).put("rating", anime.rating ?: JSONObject.NULL).put("synopsis", anime.synopsis).put("watchStatus", anime.watchStatus) }

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

private enum class Section(val title: String, val short: String) { WISHES("Compras", "◇"), TASKS("Tareas", "✓"), FAVORITES("Favoritos", "♡"), ANIME("Anime", "▷") }

@Composable
private fun PersonalHub(repository: PersonalRepository) {
    var section by rememberSaveable { mutableStateOf(Section.WISHES) }
    var wishes by remember { mutableStateOf(repository.loadWishes()) }
    var tasks by remember { mutableStateOf(repository.loadTasks()) }
    var favorites by remember { mutableStateOf(repository.loadFavorites()) }
    var anime by remember { mutableStateOf(repository.loadAnime()) }
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
            Section.ANIME -> Unit
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppNavigation(section) { section = it } },
        floatingActionButton = {
            if (section in setOf(Section.WISHES, Section.TASKS, Section.FAVORITES))
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
                Section.ANIME -> AnimeScreen(anime, padding, onAdd = { result -> if (anime.none { it.apiId == result.apiId }) { anime = anime + result; repository.saveAnime(anime) } }, onStatus = { target, status -> anime = anime.map { if (it.apiId == target.apiId) it.copy(watchStatus = status) else it }; repository.saveAnime(anime) }, onDelete = { target -> anime = anime.filterNot { it.apiId == target.apiId }; repository.saveAnime(anime) })
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

/* Removed experimental video tools.
@Composable
private fun VideosScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tiktokUrl by rememberSaveable { mutableStateOf("") }
    var directUrl by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("mi-video") }
    var confirmsRights by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingDownload
        if (granted && pending != null) message = enqueueVideoDownload(context, pending.first, pending.second)
        else if (!granted) message = "Se necesita permiso para guardar el video en Descargas."
        pendingDownload = null
    }

    fun startDownload() {
        val url = directUrl.trim()
        if (!isHttpUrl(url)) { message = "Ingresa una URL directa válida que comience con http o https."; return }
        if (!confirmsRights) { message = "Confirma que el video es tuyo o que tienes permiso para descargarlo."; return }
        val safeName = fileName.trim().ifBlank { "video-${System.currentTimeMillis()}" }
        message = "Comprobando que el enlace contiene un video…"
        scope.launch {
            val check = inspectDirectVideoUrl(url)
            if (!check.isVideo) {
                message = check.error ?: "El enlace devuelve una página web, no un archivo de video directo."
                return@launch
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = check.resolvedUrl to safeName
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else message = enqueueVideoDownload(context, check.resolvedUrl, safeName)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeader("Herramientas personales", "Videos", "Visualiza TikToks oficialmente y guarda archivos directos autorizados.", 2) }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Surface(Modifier.size(50.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Text("♪", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary) } }
                    Text("Abrir un video de TikTok", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
                    Text("El enlace se abrirá en TikTok o en el navegador mediante su reproductor oficial.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(tiktokUrl, { tiktokUrl = it; message = null }, Modifier.fillMaxWidth().padding(top = 16.dp), label = { Text("Enlace de TikTok") }, placeholder = { Text("https://www.tiktok.com/@usuario/video/…") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, shape = RoundedCornerShape(17.dp))
                    Button({
                        val url = tiktokUrl.trim()
                        val uri = runCatching { Uri.parse(url) }.getOrNull()
                        if (uri != null && isTikTokUrl(uri)) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { message = "No se pudo abrir el enlace." }
                        else message = "Ese enlace no parece pertenecer a TikTok."
                    }, Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp), shape = RoundedCornerShape(16.dp), enabled = tiktokUrl.isNotBlank()) { Text("Abrir oficialmente") }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Surface(Modifier.size(50.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFE2F4EB)) { Box(contentAlignment = Alignment.Center) { Text("↓", fontSize = 27.sp, color = Color(0xFF287A58)) } }
                    Text("Descargar un MP4 directo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
                    Text("Usa una dirección directa al archivo, no una página de TikTok. Android gestionará la descarga y mostrará su progreso.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(directUrl, { directUrl = it; message = null }, Modifier.fillMaxWidth().padding(top = 16.dp), label = { Text("URL directa del video") }, placeholder = { Text("https://servidor.com/video.mp4") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, shape = RoundedCornerShape(17.dp))
                    OutlinedTextField(fileName, { fileName = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Nombre del archivo") }, suffix = { Text(".mp4") }, singleLine = true, shape = RoundedCornerShape(17.dp))
                    Row(Modifier.fillMaxWidth().clickable { confirmsRights = !confirmsRights }.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmsRights, { confirmsRights = it })
                        Text("Confirmo que el video es mío o tengo autorización para descargarlo.", Modifier.padding(start = 5.dp).weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button({ startDownload() }, Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp), enabled = directUrl.isNotBlank() && confirmsRights, shape = RoundedCornerShape(16.dp)) { Text("Guardar en Descargas") }
                }
            }
        }
        message?.let { text -> item { Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(text, Modifier.fillMaxWidth().padding(15.dp), color = MaterialTheme.colorScheme.onSecondaryContainer) } } }
        item { Text("Esta herramienta no extrae variantes privadas ni elimina marcas de agua. Está diseñada para archivos propios o expresamente autorizados.", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 5.dp)) }
    }
}

private fun isHttpUrl(value: String): Boolean = runCatching { Uri.parse(value) }.getOrNull()?.let { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() } == true

private fun isTikTokUrl(uri: Uri): Boolean {
    val host = uri.host?.lowercase(Locale.US).orEmpty()
    return uri.scheme in listOf("http", "https") && (host == "tiktok.com" || host.endsWith(".tiktok.com"))
}

private data class VideoUrlCheck(val isVideo: Boolean, val resolvedUrl: String, val error: String? = null)

private suspend fun inspectDirectVideoUrl(value: String): VideoUrlCheck = withContext(Dispatchers.IO) {
    val connection = runCatching { URL(value).openConnection() as HttpURLConnection }.getOrElse { return@withContext VideoUrlCheck(false, value, "La dirección no se pudo abrir.") }
    try {
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=0-1")
        connection.setRequestProperty("Accept", "video/all,application/octet-stream")
        connection.setRequestProperty("User-Agent", VIDEO_USER_AGENT)
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        val status = connection.responseCode
        val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.US)
        val resolved = connection.url.toString()
        val looksLikeVideo = contentType.startsWith("video/") || (contentType == "application/octet-stream" && Uri.parse(resolved).path.orEmpty().lowercase(Locale.US).endsWith(".mp4"))
        when {
            status !in 200..299 -> VideoUrlCheck(false, resolved, "El servidor rechazó el enlace (error $status). Puede haber caducado o requerir una sesión.")
            contentType.contains("html") || contentType.startsWith("text/") -> VideoUrlCheck(false, resolved, "El enlace conduce a una página web ($contentType), no al archivo MP4.")
            !looksLikeVideo -> VideoUrlCheck(false, resolved, "El servidor respondió con ${contentType.ifBlank { "un tipo desconocido" }}; no se puede confirmar que sea un video.")
            else -> VideoUrlCheck(true, resolved)
        }
    } catch (error: Exception) {
        VideoUrlCheck(false, value, "No se pudo verificar el video: ${error.message ?: "conexión fallida"}")
    } finally { connection.disconnect() }
}

private const val VIDEO_USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

private fun enqueueVideoDownload(context: Context, url: String, requestedName: String): String = runCatching {
    val cleanName = requestedName.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().removeSuffix(".mp4").ifBlank { "video-${System.currentTimeMillis()}" }
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("$cleanName.mp4")
        .setDescription("Descargando video autorizado")
        .setMimeType("video/mp4")
        .addRequestHeader("Accept", "video/all,application/octet-stream")
        .addRequestHeader("User-Agent", VIDEO_USER_AGENT)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$cleanName.mp4")
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
    "Descarga iniciada. Puedes seguir el progreso desde las notificaciones."
}.getOrElse { "No se pudo iniciar la descarga: ${it.message ?: "URL no compatible"}" }
*/

private object AnimeApi {
    private const val ENDPOINT = "https://kitsu.io/api/edge/anime"

    suspend fun search(query: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = (URL("$ENDPOINT?filter%5Btext%5D=$encoded&page%5Blimit%5D=20").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.api+json")
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException(when (status) { 429 -> "Kitsu limitó temporalmente las consultas"; else -> "Kitsu respondió con error $status" })
            parseAnimeResponse(body)
        } finally { connection.disconnect() }
    }

    suspend fun related(animeId: String): List<RelatedAnime> = withContext(Dispatchers.IO) {
        val connection = (URL("$ENDPOINT/$animeId/media-relationships?include=destination").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.api+json")
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) return@withContext emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val relationships = root.optJSONArray("data") ?: JSONArray()
            val included = root.optJSONArray("included") ?: JSONArray()
            val rolesById = buildMap {
                repeat(relationships.length()) { index ->
                    val relation = relationships.optJSONObject(index) ?: return@repeat
                    val destination = relation.optJSONObject("relationships")?.optJSONObject("destination")?.optJSONObject("data")
                    if (destination?.optString("type") == "anime") put(destination.optString("id"), relation.optJSONObject("attributes")?.optString("role").orEmpty())
                }
            }
            List(included.length()) { index -> included.optJSONObject(index) }.filterNotNull()
                .filter { it.optString("type") == "anime" && rolesById.containsKey(it.optString("id")) }
                .mapIndexed { index, item -> RelatedAnime(rolesById[item.optString("id")].orEmpty(), parseAnimeItem(item, index)) }
                .sortedBy { relationshipOrder(it.role) }
        } finally { connection.disconnect() }
    }

    private fun parseAnimeResponse(body: String): List<AnimeItem> {
        val array = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull().mapIndexed(::parseAnimeItem)
    }

    private fun parseAnimeItem(index: Int, item: JSONObject): AnimeItem = parseAnimeItem(item, index)

    private fun parseAnimeItem(item: JSONObject, index: Int): AnimeItem {
        val attributes = item.optJSONObject("attributes") ?: JSONObject()
        val titles = attributes.optJSONObject("titles")
        val title = sequenceOf(attributes.optString("canonicalTitle"), titles?.optString("en").orEmpty(), titles?.optString("en_jp").orEmpty(), titles?.optString("ja_jp").orEmpty()).firstOrNull { it.isNotBlank() } ?: "Anime"
        val poster = attributes.optJSONObject("posterImage")
        val image = sequenceOf("large", "medium", "small", "original", "tiny").map { poster?.optString(it).orEmpty() }.firstOrNull { it.startsWith("http") }.orEmpty()
        return AnimeItem(item.optString("id").ifBlank { "$title-$index" }, title, image, attributes.optIntOrNull("episodeCount"), attributes.optString("averageRating").toDoubleOrNull()?.div(10.0), attributes.optString("synopsis", attributes.optString("description")))
    }

    private fun relationshipOrder(role: String) = when (role) { "prequel" -> 0; "sequel" -> 1; "spinoff" -> 2; "side_story" -> 3; else -> 4 }
}

private object AnimeTranslator {
    suspend fun toSpanish(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        val options = TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.SPANISH).build()
        val translator = Translation.getClient(options)
        try {
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()))
            Tasks.await(translator.translate(text))
        } finally { translator.close() }
    }
}

@Composable
private fun AnimeScreen(items: List<AnimeItem>, padding: PaddingValues, onAdd: (AnimeItem) -> Unit, onStatus: (AnimeItem, String) -> Unit, onDelete: (AnimeItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
    val scope = rememberCoroutineScope()
    val library = items.filter { filter == "Todos" || it.watchStatus == filter }

    selectedAnime?.let { selected ->
        val saved = items.firstOrNull { it.apiId == selected.apiId }
        AnimeDetailSheet(
            anime = saved ?: selected,
            isSaved = saved != null,
            onDismiss = { selectedAnime = null },
            onAdd = { onAdd(selected); selectedAnime = null },
            onStatus = { status -> onStatus(saved ?: selected, status) },
            onDelete = { onDelete(saved ?: selected); selectedAnime = null },
            onSelectRelated = { selectedAnime = it }
        )
    }

    fun runSearch(term: String = query) {
        val requestedQuery = term.trim()
        if (requestedQuery.length < 2) return
        searching = true; error = null
        scope.launch {
            runCatching { AnimeApi.search(requestedQuery) }
                .onSuccess { if (query.trim() == requestedQuery) { results = it; if (it.isEmpty()) error = "No se encontraron animes" } }
                .onFailure { if (query.trim() == requestedQuery) error = it.message ?: "No fue posible conectar con la API" }
            if (query.trim() == requestedQuery) searching = false
        }
    }

    LaunchedEffect(query) {
        val requestedQuery = query.trim()
        if (requestedQuery.length < 2) {
            results = emptyList(); error = null; searching = false
        } else {
            delay(450)
            runSearch(requestedQuery)
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader("Tu mundo anime", "Lista de anime", "Descubre títulos y lleva el control de lo que ves.", items.size)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Buscar anime") }, leadingIcon = { Text("⌕", fontSize = 23.sp) }, singleLine = true, shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }))
                Button({ runSearch() }, Modifier.padding(start = 9.dp).height(56.dp), enabled = query.trim().length >= 2 && !searching, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 17.dp)) { if (searching) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Buscar") }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 9.dp))
        }

        if (results.isNotEmpty()) {
            item { Text("Resultados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            items(results.take(12), key = { "result-${it.apiId}" }) { anime ->
                AnimeResultCard(anime, alreadyAdded = items.any { it.apiId == anime.apiId }, onDetails = { selectedAnime = anime }) { onAdd(anime); results = results.filterNot { it.apiId == anime.apiId } }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        }

        item {
            Text("Mi lista", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Todos", "Quiero verlo", "Viendo", "Ya lo vi").forEach { value -> FilterChip(filter == value, { filter = value }, { Text(value, fontSize = 12.sp) }, shape = RoundedCornerShape(13.dp)) }
            }
        }
        if (library.isEmpty()) item { EmptyState("▷", "Tu lista está vacía", "Busca un anime y agrégalo para comenzar tu colección.") }
        items(library, key = { "saved-${it.apiId}" }) { anime -> AnimeLibraryCard(anime, { selectedAnime = anime }, onStatus, onDelete) }
    }
}

@Composable
private fun AnimeArtwork(anime: AnimeItem, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        if (anime.image.isNotBlank()) AsyncImage(model = anime.image, contentDescription = "Portada de ${anime.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Text("▷", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun AnimeResultCard(anime: AnimeItem, alreadyAdded: Boolean, onDetails: () -> Unit, onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onDetails), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimeArtwork(anime, Modifier.size(width = 88.dp, height = 124.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(anime.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(anime.episodes?.let { "$it episodios" }, anime.rating?.let { "★ ${"%.1f".format(it)}" }).joinToString("  •  ").ifBlank { "Información disponible" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
            Text("Ver ficha completa  →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Button(onAdd, enabled = !alreadyAdded, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp), modifier = Modifier.padding(top = 10.dp)) { Text(if (alreadyAdded) "En mi lista" else "+ Mi lista", fontSize = 12.sp) }
        }
    } }
}

@Composable
private fun AnimeLibraryCard(anime: AnimeItem, onDetails: () -> Unit, onStatus: (AnimeItem, String) -> Unit, onDelete: (AnimeItem) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onDetails), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
        AnimeArtwork(anime, Modifier.size(width = 84.dp, height = 116.dp))
        Column(Modifier.weight(1f).padding(start = 15.dp, end = 6.dp)) {
            Text(anime.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp)); AnimeStatusPill(anime.watchStatus)
            val facts = listOfNotNull(anime.episodes?.let { "$it eps." }, anime.rating?.let { "★ ${"%.1f".format(it)}" })
            if (facts.isNotEmpty()) Text(facts.joinToString("   •   "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            if (anime.synopsis.isNotBlank()) Text(anime.synopsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
        }
        Box { TextButton({ menu = true }, contentPadding = PaddingValues(2.dp), modifier = Modifier.size(34.dp)) { Text("⋮", fontSize = 23.sp) }; DropdownMenu(menu, { menu = false }) { listOf("Quiero verlo", "Viendo", "Ya lo vi").forEach { status -> DropdownMenuItem({ Text(status) }, { menu = false; onStatus(anime, status) }) }; HorizontalDivider(); DropdownMenuItem({ Text("Eliminar", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete(anime) }) } }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeDetailSheet(anime: AnimeItem, isSaved: Boolean, onDismiss: () -> Unit, onAdd: () -> Unit, onStatus: (String) -> Unit, onDelete: () -> Unit, onSelectRelated: (AnimeItem) -> Unit) {
    var translatedSynopsis by remember(anime.apiId) { mutableStateOf<String?>(null) }
    var translationFailed by remember(anime.apiId) { mutableStateOf(false) }
    var related by remember(anime.apiId) { mutableStateOf<List<RelatedAnime>>(emptyList()) }
    var loadingRelated by remember(anime.apiId) { mutableStateOf(true) }
    LaunchedEffect(anime.apiId) {
        if (anime.synopsis.isNotBlank()) runCatching { AnimeTranslator.toSpanish(anime.synopsis) }.onSuccess { translatedSynopsis = it }.onFailure { translationFailed = true }
        related = runCatching { AnimeApi.related(anime.apiId) }.getOrDefault(emptyList())
        loadingRelated = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 34.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AnimeArtwork(anime, Modifier.size(width = 126.dp, height = 178.dp))
                Column(Modifier.weight(1f).padding(start = 18.dp)) {
                    Text(anime.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    if (isSaved) { Spacer(Modifier.height(12.dp)); AnimeStatusPill(anime.watchStatus) }
                    anime.rating?.let { Text("★  ${"%.1f".format(it)} / 10", color = Color(0xFFE59A22), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp)) }
                    anime.episodes?.let { Text("$it episodios", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 22.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Sinopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when {
                anime.synopsis.isBlank() -> Text("Kitsu no ofrece una sinopsis para este título.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
                translatedSynopsis != null -> {
                    Text(translatedSynopsis!!, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp, modifier = Modifier.padding(top = 9.dp))
                    Text("Traducido por Google", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                translationFailed -> {
                    Text(anime.synopsis, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp, modifier = Modifier.padding(top = 9.dp))
                    Text("No se pudo descargar el traductor. Conéctate a Wi-Fi para obtenerlo.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                else -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Traduciendo al español…", Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            }
            Text("Saga y temporadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 26.dp, bottom = 12.dp))
            when {
                loadingRelated -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Buscando títulos relacionados…", Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                related.isEmpty() -> Text("No se encontraron temporadas o títulos relacionados.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                else -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    related.forEach { relation -> RelatedAnimeCard(relation, { onSelectRelated(relation.anime) }) }
                }
            }
            if (isSaved) {
                Text("Estado en mi lista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Quiero verlo", "Viendo", "Ya lo vi").forEach { status -> FilterChip(anime.watchStatus == status, { onStatus(status) }, { Text(status) }) }
                }
                OutlinedButton(onDelete, Modifier.fillMaxWidth().padding(top = 22.dp).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Eliminar de mi lista") }
            } else {
                Button(onAdd, Modifier.fillMaxWidth().padding(top = 24.dp).height(54.dp), shape = RoundedCornerShape(17.dp)) { Text("+  Agregar a mi lista", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun RelatedAnimeCard(relation: RelatedAnime, onClick: () -> Unit) {
    Card(Modifier.width(142.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            AnimeArtwork(relation.anime, Modifier.fillMaxWidth().height(172.dp))
            Column(Modifier.padding(11.dp)) {
                Text(relationshipLabel(relation.role), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(relation.anime.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

private fun relationshipLabel(role: String) = when (role) {
    "prequel" -> "PRECUELA"
    "sequel" -> "CONTINUACIÓN"
    "spinoff" -> "SPIN-OFF"
    "side_story" -> "HISTORIA PARALELA"
    "alternative_version" -> "VERSIÓN ALTERNATIVA"
    "adaptation" -> "ADAPTACIÓN"
    else -> "RELACIONADO"
}

@Composable
private fun AnimeStatusPill(status: String) {
    val color = when (status) { "Viendo" -> Color(0xFF2878C8); "Ya lo vi" -> Color(0xFF3B8B68); else -> Color(0xFF7357B8) }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) { Text(status, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
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
private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
