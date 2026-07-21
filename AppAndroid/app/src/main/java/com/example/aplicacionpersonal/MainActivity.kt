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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
data class AnimeItem(val apiId: String, val title: String, val image: String, val episodes: Int?, val rating: Double?, val synopsis: String, val watchStatus: String = "Quiero verlo", val genres: List<String> = emptyList())
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
    fun loadAnime(): List<AnimeItem> = parse("anime", prefs) { item -> AnimeItem(item.getString("apiId"), item.getString("title"), item.optString("image"), item.optIntOrNull("episodes"), item.optDoubleOrNull("rating"), item.optString("synopsis"), item.optString("watchStatus", "Quiero verlo"), item.optStringList("genres")) }
    fun saveAnime(items: List<AnimeItem>) = save("anime", prefs, items) { anime -> JSONObject().put("apiId", anime.apiId).put("title", anime.title).put("image", anime.image).put("episodes", anime.episodes ?: JSONObject.NULL).put("rating", anime.rating ?: JSONObject.NULL).put("synopsis", anime.synopsis).put("watchStatus", anime.watchStatus).put("genres", JSONArray(anime.genres)) }

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

private object AnimeApi {
    private const val ENDPOINT = "https://kitsu.io/api/edge/anime"

    suspend fun search(query: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = (URL("$ENDPOINT?filter%5Btext%5D=$encoded&page%5Blimit%5D=20&include=categories").openConnection() as HttpURLConnection).apply {
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
        val root = JSONObject(body)
        val array = root.optJSONArray("data") ?: JSONArray()
        val included = root.optJSONArray("included") ?: JSONArray()
        val categoryNames = buildMap {
            repeat(included.length()) { index ->
                val category = included.optJSONObject(index) ?: return@repeat
                if (category.optString("type") == "categories") {
                    val name = category.optJSONObject("attributes")?.optString("title").orEmpty()
                    if (name.isNotBlank()) put(category.optString("id"), name)
                }
            }
        }
        return List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull().mapIndexed { index, item -> parseAnimeItem(item, index, categoryNames) }
    }

    private fun parseAnimeItem(index: Int, item: JSONObject): AnimeItem = parseAnimeItem(item, index)

    private fun parseAnimeItem(item: JSONObject, index: Int, categoryNames: Map<String, String> = emptyMap()): AnimeItem {
        val attributes = item.optJSONObject("attributes") ?: JSONObject()
        val titles = attributes.optJSONObject("titles")
        val title = sequenceOf(attributes.optString("canonicalTitle"), titles?.optString("en").orEmpty(), titles?.optString("en_jp").orEmpty(), titles?.optString("ja_jp").orEmpty()).firstOrNull { it.isNotBlank() } ?: "Anime"
        val poster = attributes.optJSONObject("posterImage")
        val image = sequenceOf("large", "medium", "small", "original", "tiny").map { poster?.optString(it).orEmpty() }.firstOrNull { it.startsWith("http") }.orEmpty()
        val categoryData = item.optJSONObject("relationships")?.optJSONObject("categories")?.optJSONArray("data") ?: JSONArray()
        val genres = List(categoryData.length()) { categoryData.optJSONObject(it)?.optString("id").orEmpty() }.mapNotNull(categoryNames::get).distinct()
        return AnimeItem(item.optString("id").ifBlank { "$title-$index" }, title, image, attributes.optIntOrNull("episodeCount"), attributes.optString("averageRating").toDoubleOrNull()?.div(10.0), attributes.optString("synopsis", attributes.optString("description")), genres = genres)
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
    var libraryOpen by rememberSaveable { mutableStateOf(false) }
    var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
    val scope = rememberCoroutineScope()

    if (libraryOpen) BackHandler { libraryOpen = false }

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

    if (libraryOpen) {
        AnimeLibraryOverview(items, padding, onBack = { libraryOpen = false }, onDetails = { selectedAnime = it })
        return
    }

    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 112.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TU MUNDO ANIME", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Explorar anime", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                    FilledTonalButton({ libraryOpen = true }, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)) { Text("Mi lista  ${items.size}", fontWeight = FontWeight.Bold) }
                }
                Text("Busca títulos y agrégalos a tu colección personal.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Buscar anime") }, leadingIcon = { Text("⌕", fontSize = 23.sp) }, singleLine = true, shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { runSearch() }))
                    FilledIconButton({ runSearch() }, Modifier.padding(start = 9.dp).size(56.dp), enabled = query.trim().length >= 2 && !searching, shape = RoundedCornerShape(18.dp)) { if (searching) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("⌕", fontSize = 24.sp) }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 9.dp))
            }
        }
        if (results.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Resultados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            gridItems(results.take(20), key = { "result-${it.apiId}" }) { anime ->
                AnimeResultCard(anime, alreadyAdded = items.any { it.apiId == anime.apiId }, onDetails = { selectedAnime = anime }) { onAdd(anime); results = results.filterNot { it.apiId == anime.apiId } }
            }
        } else if (query.isBlank()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("⌕", "Empieza a explorar", "Escribe al menos dos letras para descubrir anime.") }
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
    Card(Modifier.fillMaxWidth().clickable(onClick = onDetails), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Box {
                AnimeArtwork(anime, Modifier.fillMaxWidth().aspectRatio(0.72f))
                Surface(Modifier.align(Alignment.TopEnd).padding(9.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                    Text(if (alreadyAdded) "✓" else "+", Modifier.clickable(enabled = !alreadyAdded, onClick = onAdd).padding(horizontal = 11.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(anime.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                if (anime.genres.isNotEmpty()) Row(Modifier.padding(top = 9.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    anime.genres.forEach { genre -> AnimeGenrePill(genre) }
                } else Text(anime.rating?.let { "★ ${"%.1f".format(it)}" } ?: "Ver detalles", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            }
        }
    }
}

@Composable
private fun AnimeLibraryOverview(items: List<AnimeItem>, padding: PaddingValues, onBack: () -> Unit, onDetails: (AnimeItem) -> Unit) {
    val sections = listOf("Ya lo vi" to "Animes vistos", "Quiero verlo" to "Animes que quiero ver", "Viendo" to "Animes viendo")
    var openStatus by rememberSaveable { mutableStateOf<String?>(null) }
    openStatus?.let { status ->
        val title = sections.firstOrNull { it.first == status }?.second ?: "Mi lista"
        BackHandler { openStatus = null }
        AnimeLibraryGrid(title, items.filter { it.watchStatus == status }, padding, onBack = { openStatus = null }, onDetails = onDetails)
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onBack, Modifier.size(52.dp), shape = CircleShape) { Text("‹", fontSize = 32.sp) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("MI COLECCIÓN", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("Mi lista de anime", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text(items.size.toString(), Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
            }
        }
        if (items.isEmpty()) item { EmptyState("▷", "Tu lista está vacía", "Busca un anime y agrégalo para comenzar tu colección.") }
        sections.forEach { (status, title) ->
            val sectionItems = items.filter { it.watchStatus == status }
            item(key = "section-$status") { AnimeLibraryShelf(title, sectionItems, onOpen = { openStatus = status }, onDetails = onDetails) }
        }
    }
}

@Composable
private fun AnimeLibraryShelf(title: String, items: List<AnimeItem>, onOpen: () -> Unit, onDetails: (AnimeItem) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(vertical = 17.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Text(items.size.toString(), Modifier.padding(horizontal = 11.dp, vertical = 5.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            if (items.isEmpty()) Text("Aún no hay títulos en esta sección.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp))
            else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.padding(top = 14.dp)) {
                items(items, key = { "shelf-${it.apiId}" }) { anime ->
                    Column(Modifier.width(104.dp).clickable { onDetails(anime) }) {
                        AnimeArtwork(anime, Modifier.fillMaxWidth().height(142.dp))
                        Text(anime.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                    }
                }
            }
            if (items.isNotEmpty()) TextButton(onOpen, Modifier.align(Alignment.End).padding(end = 10.dp, top = 5.dp)) { Text("Ver todos  →", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AnimeLibraryGrid(title: String, items: List<AnimeItem>, padding: PaddingValues, onBack: () -> Unit, onDetails: (AnimeItem) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 112.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onBack, Modifier.size(52.dp), shape = CircleShape) { Text("‹", fontSize = 32.sp) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("MI LISTA DE ANIME", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text(items.size.toString(), Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold) }
            }
        }
        gridItems(items, key = { "library-grid-${it.apiId}" }) { anime -> AnimeSavedGridCard(anime, { onDetails(anime) }) }
    }
}

@Composable
private fun AnimeSavedGridCard(anime: AnimeItem, onDetails: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onDetails), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            AnimeArtwork(anime, Modifier.fillMaxWidth().aspectRatio(0.72f))
            Column(Modifier.padding(12.dp)) {
                Text(anime.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                Spacer(Modifier.height(8.dp)); AnimeStatusPill(anime.watchStatus)
                if (anime.genres.isNotEmpty()) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { anime.genres.forEach { AnimeGenrePill(it) } }
            }
        }
    }
}

@Composable
private fun AnimeGenrePill(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(text, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
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
                    if (anime.genres.isNotEmpty()) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { anime.genres.forEach { AnimeGenrePill(it) } }
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
private fun JSONObject.optStringList(key: String): List<String> = optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it) }.filter(String::isNotBlank) } ?: emptyList()
