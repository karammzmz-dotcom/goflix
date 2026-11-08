package com.goflix.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val st = Store(this)
        setContent { GoflixApp(st) }
    }
    override fun onPause() { super.onPause(); Sfx.pause() }
    override fun onResume() { super.onResume(); Sfx.resume() }
}

enum class Scr { INTRO, PROFILES, PINTRO, HOME, SEARCH, ADD, DETAIL, PLAYER, SETTINGS }

fun TextStyle.f(ff: FontFamily) = copy(fontFamily = ff)
fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.f(f), displayMedium = displayMedium.f(f), displaySmall = displaySmall.f(f),
    headlineLarge = headlineLarge.f(f), headlineMedium = headlineMedium.f(f), headlineSmall = headlineSmall.f(f),
    titleLarge = titleLarge.f(f), titleMedium = titleMedium.f(f), titleSmall = titleSmall.f(f),
    bodyLarge = bodyLarge.f(f), bodyMedium = bodyMedium.f(f), bodySmall = bodySmall.f(f),
    labelLarge = labelLarge.f(f), labelMedium = labelMedium.f(f), labelSmall = labelSmall.f(f))

@Composable
fun GoflixApp(st: Store) {
    val ctx = LocalContext.current
    var scr by remember { mutableStateOf(if (st.flag("intro")) Scr.INTRO else Scr.PROFILES) }
    var cur by remember { mutableStateOf<Profile?>(null) }
    var sel by remember { mutableStateOf<Video?>(null) }
    var edit by remember { mutableStateOf<Video?>(null) }
    fun click() { if (st.flag("sound")) Sfx.play(ctx, R.raw.click, st.str("clickPath")) }

    LaunchedEffect(scr) {
        val quiet = scr == Scr.INTRO || scr == Scr.PINTRO || scr == Scr.PLAYER || scr == Scr.PROFILES
        Sfx.ambient(ctx, st.flag("sound") && st.flag("ambient") && !quiet, st.str("ambientPath"))
    }
    BackHandler(scr != Scr.HOME && scr != Scr.INTRO && scr != Scr.PROFILES && scr != Scr.PINTRO) {
        scr = if (scr == Scr.PLAYER) Scr.DETAIL else Scr.HOME
    }

    MaterialTheme(
        colorScheme = darkColorScheme(primary = ACC, background = BG, surface = BG, surfaceVariant = CARD, onSurface = Color.White, onBackground = Color.White),
        typography = Typography().withFont(FONT)
    ) {
        val showBar = cur != null && (scr == Scr.HOME || scr == Scr.SEARCH || scr == Scr.SETTINGS)
        Scaffold(containerColor = BG, bottomBar = {
            if (showBar) NavBar(scr, { scr = it }) { edit = null; scr = Scr.ADD }
        }) { pad ->
            Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
                when (scr) {
                    Scr.INTRO -> Splash(st, st.str("introPath"), true) { scr = Scr.PROFILES }
                    Scr.PROFILES -> Profiles(st) { cur = it; click(); scr = Scr.PINTRO }
                    Scr.PINTRO -> Splash(st, st.str("pIntroPath"), false) { scr = Scr.HOME }
                    Scr.HOME -> cur?.let { p ->
                        Home(st, p, open = { click(); sel = it; scr = Scr.DETAIL }, play = { sel = it; scr = Scr.PLAYER },
                            add = { edit = null; scr = Scr.ADD }, switch = { scr = Scr.PROFILES }, search = { scr = Scr.SEARCH },
                            toggle = { v -> cur = st.toggleList(p, v.id) })
                    }
                    Scr.SEARCH -> Search(st) { click(); sel = it; scr = Scr.DETAIL }
                    Scr.ADD -> AddEdit(st, edit) { scr = Scr.HOME }
                    Scr.DETAIL -> sel?.let { v -> cur?.let { p ->
                        Detail(st, p, v, back = { scr = Scr.HOME }, play = { scr = Scr.PLAYER }, edit = { edit = v; scr = Scr.ADD },
                            toggle = { cur = st.toggleList(p, v.id) }, open = { sel = it })
                    } }
                    Scr.PLAYER -> sel?.let { Player(st, it) { scr = Scr.DETAIL } }
                    Scr.SETTINGS -> cur?.let { p -> SettingsScreen(st, p) { scr = Scr.PROFILES } }
                }
            }
        }
    }
}

@Composable
fun NavBar(scr: Scr, go: (Scr) -> Unit, add: () -> Unit) {
    NavigationBar(containerColor = BG, tonalElevation = 0.dp) {
        val c = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = ACC,
            unselectedIconColor = Color(0xFF8A8A8A), unselectedTextColor = Color(0xFF8A8A8A))
        NavigationBarItem(scr == Scr.HOME, { go(Scr.HOME) }, { Icon(Icons.Default.Home, null) }, label = { Text("Home", fontSize = 11.sp) }, colors = c)
        NavigationBarItem(scr == Scr.SEARCH, { go(Scr.SEARCH) }, { Icon(Icons.Default.Search, null) }, label = { Text("Search", fontSize = 11.sp) }, colors = c)
        NavigationBarItem(false, add, { Icon(Icons.Default.Add, null) }, label = { Text("Upload", fontSize = 11.sp) }, colors = c)
        NavigationBarItem(scr == Scr.SETTINGS, { go(Scr.SETTINGS) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings", fontSize = 11.sp) }, colors = c)
    }
}

@Composable
fun Logo(size: Int = 34) {
    if (LOGO_IMG.isNotEmpty() && File(LOGO_IMG).exists())
        AsyncImage(File(LOGO_IMG), null, Modifier.height((size * 1.3f).dp), contentScale = ContentScale.Fit)
    else Text(APP_NAME, color = ACC, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (size / 10f).sp, maxLines = 1)
}

/** Full-screen intro: plays the user's own video/audio if provided, otherwise an animated logo. */
@Composable
fun Splash(st: Store, path: String, defaultSound: Boolean, done: () -> Unit) {
    val ctx = LocalContext.current
    var fin by remember { mutableStateOf(false) }
    val finish = { if (!fin) { fin = true; done() } }
    val isVid = path.substringAfterLast('.').lowercase() in listOf("mp4", "mkv", "webm", "mov", "3gp", "m4v")
    val has = path.isNotEmpty() && File(path).exists()
    if (!defaultSound && !has) { LaunchedEffect(Unit) { finish() }; return }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable { finish() }, Alignment.Center) {
        if (has && isVid) {
            val ex = remember {
                ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path)))); prepare(); playWhenReady = true
                    volume = if (st.flag("sound")) 1f else 0f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_ENDED) finish() }
                        override fun onPlayerError(e: PlaybackException) { finish() }
                    })
                }
            }
            DisposableEffect(Unit) { onDispose { ex.release() } }
            AndroidView({ PlayerView(it).apply { player = ex; useController = false } }, Modifier.fillMaxSize())
        } else {
            val sc = remember { Animatable(0.8f) }
            val al = remember { Animatable(0f) }
            var ms = 3400L
            LaunchedEffect(Unit) {
                var mp: MediaPlayer? = null
                if (st.flag("sound")) {
                    mp = try { if (has) MediaPlayer().apply { setDataSource(path); prepare() } else MediaPlayer.create(ctx, R.raw.intro) } catch (e: Exception) { null }
                    mp?.start(); if (mp != null && has) ms = mp.duration.toLong().coerceIn(2500L, 9000L)
                }
                val t = ms
                coroutineScope {
                    launch { al.animateTo(1f, tween(800)) }
                    launch { sc.animateTo(1.35f, tween(t.toInt(), easing = LinearEasing)) }
                    delay(t - 400); al.animateTo(0f, tween(400))
                }
                mp?.release(); finish()
            }
            Box(Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = al.value }) { Logo(54) }
        }
    }
}

@Composable
fun Avatar(a: String, size: Dp) {
    val colors = listOf(Color(0xFFE50914), Color(0xFF1E88E5), Color(0xFFFBC02D), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFFFB8C00))
    val faces = listOf(":)", "^_^", "B)", ":D", "o_o", "UwU")
    val m = Modifier.size(size).clip(RoundedCornerShape((size.value * 0.12f).dp))
    if (a.startsWith("b:")) {
        val i = a.substring(2).toIntOrNull() ?: 0
        Box(m.background(Brush.linearGradient(listOf(colors[i % 6], colors[i % 6].copy(alpha = .65f)))), Alignment.Center) {
            Text(faces[i % 6], fontSize = (size.value * 0.38f).sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    } else AsyncImage(File(a), null, m.background(CARD), contentScale = ContentScale.Crop)
}

@Composable
fun Profiles(st: Store, onPick: (Profile) -> Unit) {
    var manage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ACC.copy(alpha = .22f), BG, BG))).statusBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp)); Logo(30)
        Spacer(Modifier.height(36.dp))
        Text(if (manage) "Manage Profiles" else "Who's watching?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(if (manage) "Tap a profile to edit it" else "Pick a profile to continue", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
        LazyColumn(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val tiles: List<Profile?> = st.profiles.toList() + if (st.profiles.size < 5) listOf(null) else emptyList()
            items(tiles.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    row.forEach { p ->
                        Column(Modifier.width(130.dp).clip(RoundedCornerShape(12.dp)).clickable { if (p == null) editing = Profile(name = "") else if (manage) editing = p else onPick(p) },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (p == null) Box(Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(CARD), Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(44.dp), tint = Color.Gray) }
                            else Box(contentAlignment = Alignment.Center) {
                                Avatar(p.avatar, 120.dp)
                                if (manage) Box(Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xAA000000)), Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(34.dp)) }
                            }
                            Text(p?.name ?: "Add profile", Modifier.padding(top = 10.dp), fontSize = 15.sp, color = Color.LightGray, maxLines = 1)
                            if (p?.kids == true) Text("KIDS", color = ACC, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        OutlinedButton({ manage = !manage }, shape = RoundedCornerShape(50), modifier = Modifier.padding(top = 12.dp)) {
            Text(if (manage) "DONE" else "MANAGE PROFILES", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
    editing?.let { ProfileEditor(st, it) { editing = null } }
}

@Composable
fun ProfileEditor(st: Store, p: Profile, close: () -> Unit) {
    val exists = st.profiles.any { it.id == p.id }
    var name by remember { mutableStateOf(p.name) }
    var av by remember { mutableStateOf(p.avatar) }
    var kids by remember { mutableStateOf(p.kids) }
    var url by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { av = st.copyImage(it) } }
    AlertDialog(onDismissRequest = close, containerColor = Color(0xFF1A1A1A),
        title = { Text(if (exists) "Edit profile" else "New profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { Avatar(av, 96.dp) }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 14.dp), label = { Text("Name") }, singleLine = true)
                Text("Icon", Modifier.padding(top = 14.dp), color = Color.Gray, fontSize = 13.sp)
                LazyRow { items(6) { i -> Box(Modifier.padding(4.dp).clickable { av = "b:$i" }) { Avatar("b:$i", 48.dp) } } }
                OutlinedButton({ pick.launch("image/*") }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Choose from gallery", color = Color.White) }
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("…or paste an image link") }, singleLine = true,
                    trailingIcon = { TextButton({ st.downloadImage(url) { r -> if (r != null) av = r } }) { Text("GET", color = ACC) } })
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(kids, { kids = it }, colors = SwitchDefaults.colors(checkedTrackColor = ACC)); Text("   Kids profile")
                }
            }
        },
        confirmButton = { Button({ if (name.isNotBlank()) { st.upsertProfile(p.copy(name = name.trim(), avatar = av, kids = kids)); close() } }, colors = ButtonDefaults.buttonColors(containerColor = ACC)) { Text("Save") } },
        dismissButton = {
            Row {
                if (exists && st.profiles.size > 1) TextButton({ st.deleteProfile(p); close() }) { Text("Delete", color = ACC) }
                TextButton(close) { Text("Cancel", color = Color.Gray) }
            }
        })
}

@Composable
fun Poster(v: Video, modifier: Modifier = Modifier, progress: Float = 0f, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(RADIUS.dp)).background(CARD), Alignment.Center) {
            if (v.cover.isNotEmpty()) AsyncImage(File(v.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(v.title.take(1).uppercase(), fontSize = 40.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            if (progress > 0f) LinearProgressIndicator({ progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = ACC, trackColor = Color(0x99000000))
        }
        Text(v.title, Modifier.padding(top = 5.dp), fontSize = 12.sp, color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ShelfRow(title: String, l: List<Video>, st: Store? = null, open: (Video) -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(title, Modifier.padding(start = 16.dp, bottom = 10.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(l) { v ->
                val pr = if (st != null && st.dur(v.id) > 0) st.pos(v.id).toFloat() / st.dur(v.id) else 0f
                Poster(v, Modifier.width(POSTER.dp), pr) { open(v) }
            }
        }
    }
}

@Composable
fun Grid3(l: List<Video>, open: (Video) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        l.chunked(3).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.forEach { Poster(it, Modifier.weight(1f)) { open(it) } }
                repeat(3 - r.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Home(st: Store, p: Profile, open: (Video) -> Unit, play: (Video) -> Unit, add: () -> Unit, switch: () -> Unit, search: () -> Unit, toggle: (Video) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    val all = st.videos.sortedByDescending { it.added }
    val cats = all.map { it.category }.distinct()
    if (filter != "All" && filter != "My List" && filter !in cats) filter = "All"
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (all.isEmpty()) item {
                Column(Modifier.fillParentMaxSize().padding(36.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Logo(40); Spacer(Modifier.height(28.dp))
                    Text("Nothing to watch yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Add your own videos with a title, description and cover.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                    Button(add, colors = ButtonDefaults.buttonColors(containerColor = ACC), shape = RoundedCornerShape(RADIUS.dp)) { Text("Upload your first video", Modifier.padding(6.dp)) }
                }
            } else if (filter == "All") {
                val f = all.first()
                item {
                    Box(Modifier.fillMaxWidth().height(540.dp)) {
                        if (f.cover.isNotEmpty()) AsyncImage(File(f.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color.Transparent, BG))))
                        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
                            Text(f.title, fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp)
                            if (f.desc.isNotBlank()) Text(f.desc, Modifier.padding(top = 6.dp), color = Color(0xFFDDDDDD), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button({ play(f) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(RADIUS.dp)) {
                                    Icon(Icons.Default.PlayArrow, null); Text(" Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Button({ toggle(f) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x88555555), contentColor = Color.White), shape = RoundedCornerShape(RADIUS.dp)) {
                                    Icon(if (f.id in p.list) Icons.Default.Check else Icons.Default.Add, null); Text(" My List", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                val cont = all.filter { val po = st.pos(it.id); val d = st.dur(it.id); po > 5000 && d > 0 && po < d - 5000 }
                if (cont.isNotEmpty()) item { ShelfRow("Continue Watching", cont, st, open) }
                val mine = all.filter { it.id in p.list }
                if (mine.isNotEmpty()) item { ShelfRow("My List", mine, null, open) }
                item { ShelfRow("Recently Added", all, null, open) }
                cats.forEach { c -> item { ShelfRow(c, all.filter { it.category == c }, null, open) } }
            } else {
                item { Spacer(Modifier.height(130.dp)) }
                val l = if (filter == "My List") all.filter { it.id in p.list } else all.filter { it.category == filter }
                if (l.isEmpty()) item { Text("Nothing here yet.", Modifier.padding(20.dp), color = Color.Gray) }
                item { Grid3(l, open) }
            }
        }
        // top overlay
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(BG.copy(alpha = .92f), Color.Transparent))).statusBarsPadding().padding(top = 6.dp, bottom = 18.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(24); Spacer(Modifier.weight(1f))
                IconButton(search) { Icon(Icons.Default.Search, null, Modifier.size(26.dp)) }
                Box(Modifier.padding(start = 6.dp).clickable(onClick = switch)) { Avatar(p.avatar, 30.dp) }
            }
            if (all.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = listOf("All", "My List") + cats
                items(chips) { c ->
                    val on = c == filter
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) ACC else Color(0x66222222)).border(1.dp, if (on) ACC else Color(0x88FFFFFF), RoundedCornerShape(50))
                        .clickable { filter = c }.padding(horizontal = 14.dp, vertical = 6.dp)) { Text(c, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
    }
}

@Composable
fun Search(st: Store, open: (Video) -> Unit) {
    var q by remember { mutableStateOf("") }
    val r = st.videos.filter { q.isBlank() || it.title.contains(q, true) || it.category.contains(q, true) || it.desc.contains(q, true) }.sortedBy { it.title.lowercase() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Search titles, categories…") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(50),
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Close, null) } })
        }
        item { Text(if (q.isBlank()) "All videos" else "Results", Modifier.padding(start = 16.dp, bottom = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (r.isEmpty()) item { Text("No matches.", Modifier.padding(16.dp), color = Color.Gray) }
        item { Grid3(r, open) }
    }
}

@Composable
fun Detail(st: Store, p: Profile, v: Video, back: () -> Unit, play: () -> Unit, edit: () -> Unit, toggle: () -> Unit, open: (Video) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val resume = st.pos(v.id) > 5000 && st.dur(v.id) > 0 && st.pos(v.id) < st.dur(v.id) - 5000
    val more = st.videos.filter { it.category == v.category && it.id != v.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box {
                Box(Modifier.fillMaxWidth().height(380.dp).background(CARD)) { if (v.cover.isNotEmpty()) AsyncImage(File(v.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, BG))))
                IconButton(back, Modifier.statusBarsPadding().padding(6.dp).clip(CircleShape).background(Color(0x66000000))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(v.title, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(CARD).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(v.category, fontSize = 12.sp, color = Color.LightGray) }
                    Text(java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(v.added)), fontSize = 12.sp, color = Color.Gray)
                }
                Button(play, Modifier.fillMaxWidth().height(50.dp).padding(top = 0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(RADIUS.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Text(if (resume) " Resume" else " Play", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (resume) LinearProgressIndicator({ st.pos(v.id).toFloat() / st.dur(v.id) }, Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp), color = ACC, trackColor = CARD)
                if (v.desc.isNotBlank()) Text(v.desc, Modifier.padding(top = 16.dp), lineHeight = 22.sp, color = Color(0xFFDDDDDD))
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Action(if (v.id in p.list) Icons.Default.Check else Icons.Default.Add, "My List", toggle)
                    Action(Icons.Default.Edit, "Edit", edit)
                    Action(Icons.Default.Delete, "Delete") { confirm = true }
                }
            }
        }
        if (more.isNotEmpty()) item { ShelfRow("More in ${v.category}", more, null, open) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color(0xFF1A1A1A),
        title = { Text("Delete \"${v.title}\"?") }, text = { Text("It will be removed from the app. Your original file stays on your phone.") },
        confirmButton = { TextButton({ st.deleteVideo(v); confirm = false; back() }) { Text("Delete", color = ACC) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = Color.Gray) } })
}

@Composable
fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(26.dp)); Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun AddEdit(st: Store, old: Video?, done: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(old?.title ?: "") }
    var desc by remember { mutableStateOf(old?.desc ?: "") }
    var cat by remember { mutableStateOf(old?.category ?: "") }
    var uri by remember { mutableStateOf(old?.uri ?: "") }
    var cover by remember { mutableStateOf(old?.cover ?: "") }
    val vp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let {
            try { ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            uri = it.toString()
            if (title.isBlank()) title = st.displayName(uri).substringBeforeLast('.')
        }
    }
    val cp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { cover = st.copyImage(it) } }
    BackHandler { done() }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(if (old == null) "Add a video" else "Edit video", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(140.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(RADIUS.dp)).background(CARD).clickable { cp.launch("image/*") }, Alignment.Center) {
                if (cover.isNotEmpty()) AsyncImage(File(cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Add, null, tint = Color.Gray); Text("Cover", color = Color.Gray, fontSize = 13.sp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. Pick your video", fontWeight = FontWeight.Bold)
                OutlinedButton({ vp.launch(arrayOf("video/*")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(RADIUS.dp)) {
                    Text(if (uri.isEmpty()) "Choose file" else "Change", color = Color.White)
                }
                if (uri.isNotEmpty()) Text("✓ " + st.displayName(uri), color = ACC, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("2. Tap the box to add a cover", color = Color.Gray, fontSize = 13.sp)
            }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 20.dp), label = { Text("Title") }, singleLine = true)
        OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Description") }, minLines = 3)
        OutlinedTextField(cat, { cat = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Row name (e.g. Movies, Shows)") }, singleLine = true)
        val cats = st.videos.map { it.category }.distinct()
        if (cats.isNotEmpty()) LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cats) { c -> Box(Modifier.clip(RoundedCornerShape(50)).background(CARD).clickable { cat = c }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text(c, fontSize = 12.sp) } }
        }
        Button({
            st.upsertVideo((old ?: Video(title = "", desc = "", uri = "", cover = "")).copy(title = title.trim(), desc = desc.trim(), uri = uri, cover = cover, category = cat.trim().ifEmpty { "My Videos" }))
            done()
        }, Modifier.fillMaxWidth().padding(vertical = 20.dp).height(50.dp), enabled = title.isNotBlank() && uri.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = ACC), shape = RoundedCornerShape(RADIUS.dp)) {
            Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Player(st: Store, v: Video, close: () -> Unit) {
    val ctx = LocalContext.current
    val ex = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(v.uri))); prepare()
            val p = st.pos(v.id); val d = st.dur(v.id)
            if (p > 5000 && (d == 0L || p < d - 5000)) seekTo(p)
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        val a = ctx as Activity
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            val d = ex.duration
            if (d > 0) st.savePos(v.id, if (ex.currentPosition > d - 5000) 0 else ex.currentPosition, d)
            ex.release(); a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ PlayerView(it).apply { player = ex; setShowNextButton(false); setShowPreviousButton(false) } }, Modifier.fillMaxSize())
        IconButton(close, Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x66000000))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
    }
}
