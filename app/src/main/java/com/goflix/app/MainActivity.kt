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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
        val tabScreens = setOf(Scr.HOME, Scr.SEARCH, Scr.SETTINGS)
        val showBar = cur != null && scr in tabScreens
        Scaffold(containerColor = BG, topBar = {
            if (showBar) TopTabs(scr, cur, go = { scr = it }, onAdd = { edit = null; scr = Scr.ADD }, onAvatar = { scr = Scr.PROFILES })
        }) { pad ->
            AnimatedContent(scr, Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()), label = "screen", transitionSpec = {
                if (initialState in tabScreens && targetState in tabScreens) {
                    // Quick cross-fade between tabs — a real but snappy animation, not a dead instant cut.
                    val d = ms(110)
                    (fadeIn(tween(d)) togetherWith fadeOut(tween(d))) using SizeTransform(clip = false) { _, _ -> tween(d) }
                } else {
                    val d = ms(120)
                    if (!ANIM_ON || ANIM_STYLE == "none") EnterTransition.None togetherWith ExitTransition.None
                    else when (ANIM_STYLE) {
                        "fade" -> fadeIn(tween(d)) togetherWith fadeOut(tween(d))
                        "scale" -> (scaleIn(tween(d), 0.92f) + fadeIn(tween(d))) togetherWith fadeOut(tween(d / 2))
                        else -> (slideInHorizontally(tween(d)) { it / 5 } + fadeIn(tween(d))) togetherWith (slideOutHorizontally(tween(d)) { -it / 6 } + fadeOut(tween(d / 2)))
                    }
                }
            }) { s ->
                Box(Modifier.fillMaxSize()) {
                    when (s) {
                        Scr.INTRO -> Splash(st, st.str("introPath"), true) { scr = Scr.PROFILES }
                        Scr.PROFILES -> Profiles(st) { cur = it; click(); scr = Scr.PINTRO }
                        Scr.PINTRO -> Splash(st, st.str("pIntroPath"), false) { scr = Scr.HOME }
                        Scr.HOME -> cur?.let { p ->
                            Home(st, p, open = { click(); sel = it; scr = Scr.DETAIL }, play = { sel = it; scr = Scr.PLAYER },
                                add = { edit = null; scr = Scr.ADD },
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
}

/** The ONE top bar for the app: logo + text tabs (red underline on the active one) on the left,
 *  bell + profile avatar on the right. Tapping a tab switches instantly — no animation, no delay. */
@Composable
fun TopTabs(scr: Scr, p: Profile?, go: (Scr) -> Unit, onAdd: () -> Unit, onAvatar: () -> Unit) {
    val tabs = buildList {
        this += Scr.HOME to L("home", "Home")
        this += Scr.SEARCH to L("search", "Search")
        if (SHOW_UPLOAD) this += Scr.ADD to L("upload", "Upload")
        this += Scr.SETTINGS to L("settings", "Settings")
    }
    Column(Modifier.fillMaxWidth().background(BG).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(22)
            Row(Modifier.weight(1f).padding(start = 20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { (s, label) ->
                    val on = scr == s
                    Column(Modifier.clickable(remember { MutableInteractionSource() }, null) { if (s == Scr.ADD) onAdd() else go(s) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color(0xFFB3B3B3), maxLines = 1)
                        Box(Modifier.padding(top = 4.dp).height(2.dp).width(if (on) 18.dp else 0.dp).background(ACC))
                    }
                }
            }
            if (p?.kids == true) Text("kids", color = Color(0xFFFFD400), fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(end = 4.dp))
            IconButton({}) { Icon(Icons.Default.Notifications, null, Modifier.size(22.dp)) }
            if (p != null) Box(Modifier.padding(start = 2.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onAvatar)) { Avatar(p.avatar, 28.dp) }
        }
    }
}

@Composable
fun Logo(size: Int = 34, text: String? = null) {
    if (text == null && LOGO_IMG.isNotEmpty() && File(LOGO_IMG).exists())
        AsyncImage(File(LOGO_IMG), null, Modifier.height((size * 1.3f).dp), contentScale = ContentScale.Fit)
    else Text(text ?: APP_NAME, color = ACC, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (size / 10f).sp, maxLines = 1)
}

@Composable
fun Reveal(i: Int, content: @Composable () -> Unit) {
    // Content renders immediately — no artificial per-row stagger delay.
    content()
}

@Composable
fun Stars(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "stars")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "a")
    val pts = remember { val r = java.util.Random(7); List(50) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(modifier) {
        pts.forEachIndexed { i, (x, y, s) ->
            val al = if (ANIM_ON) (if (i % 2 == 0) a else 1.2f - a) else 0.8f
            drawCircle(Color.White.copy(alpha = al.coerceIn(0.1f, 1f)), 1.5f + s * 3.5f, Offset(x * size.width, y * size.height))
        }
    }
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
            LaunchedEffect(Unit) {
                var mp: MediaPlayer? = null
                var t = 900L
                if (st.flag("sound")) {
                    mp = try { if (has) MediaPlayer().apply { setDataSource(path); prepare() } else MediaPlayer.create(ctx, R.raw.intro) } catch (e: Exception) { null }
                    mp?.start(); if (mp != null && has) t = mp.duration.toLong().coerceIn(900L, 3000L)
                }
                coroutineScope {
                    launch { al.animateTo(1f, tween(220)) }
                    launch { sc.animateTo(1.12f, tween(t.toInt(), easing = LinearEasing)) }
                    delay(t - 180); al.animateTo(0f, tween(180))
                }
                mp?.release(); finish()
            }
            Box(Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = al.value }) { Logo(54) }
        }
    }
}

@Composable
fun Avatar(a: String, size: Dp, round: Dp = (size.value * 0.06f).dp) {
    val colors = listOf(Color(0xFF2F7CF6), Color(0xFFE50914), Color(0xFFFBC02D), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFFFB8C00))
    val m = Modifier.size(size).clip(RoundedCornerShape(round))
    if (a.startsWith("b:")) {
        val i = a.substring(2).toIntOrNull() ?: 0
        Box(m.background(Brush.verticalGradient(listOf(colors[i % 6], colors[i % 6].copy(alpha = .7f)))), Alignment.Center) {
            // simple smiley face like the classic profile icon
            Canvas(Modifier.size(size * 0.6f)) {
                val w = this.size.width
                drawCircle(Color.White, w * 0.07f, Offset(w * 0.28f, w * 0.30f))
                drawCircle(Color.White, w * 0.07f, Offset(w * 0.72f, w * 0.30f))
                drawArc(Color.White, 20f, 140f, false, Offset(w * 0.12f, w * 0.18f), androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.62f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f, cap = StrokeCap.Round))
            }
        }
    } else AsyncImage(File(a), null, m.background(CARD), contentScale = ContentScale.Crop)
}

@Composable
fun Profiles(st: Store, onPick: (Profile) -> Unit) {
    var manage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.fillMaxSize().background(Color(0xFF141414)).statusBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp)); Logo(26)
        Spacer(Modifier.weight(0.5f))
        Text(if (manage) "Manage Profiles" else L("who", "Who's watching?"), fontSize = 30.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val n = (maxWidth / 124.dp).toInt().coerceIn(2, 5)
            val tiles: List<Profile?> = st.profiles.toList() + if (st.profiles.size < 5) listOf(null) else emptyList()
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                tiles.chunked(n).forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEachIndexed { ci, p ->
                            Reveal(ri * n + ci) {
                                Column(Modifier.width(106.dp).clickable(remember { MutableInteractionSource() }, null) { if (p == null) editing = Profile(name = "") else if (manage) editing = p else onPick(p) },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (p == null) Box(Modifier.size(100.dp), Alignment.Center) {
                                        Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0xFF808080)), Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(54.dp), tint = Color(0xFF141414)) }
                                    } else Box(contentAlignment = Alignment.Center) {
                                        Avatar(p.avatar, 100.dp)
                                        if (manage) Box(Modifier.size(100.dp).background(Color(0x99000000)), Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(32.dp)) }
                                    }
                                    Text(if (p == null) L("addprofile", "Add Profile") else p.name, Modifier.padding(top = 10.dp), fontSize = 14.sp, color = Color(0xFF9A9A9A), maxLines = 1)
                                    if (p?.kids == true) Text("KIDS", color = Color(0xFFFFD400), fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.border(1.dp, Color(0xFF8A8A8A)).clickable { manage = !manage }.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(if (manage) "DONE" else L("manage", "MANAGE PROFILES"), color = Color(0xFF9A9A9A), letterSpacing = 2.sp, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
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
                    Switch(kids, { kids = it }, colors = SwitchDefaults.colors(checkedTrackColor = ACC)); Text("   Kids profile (colorful kids home)")
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
fun Poster(v: Video, modifier: Modifier = Modifier, progress: Float = 0f, showTitle: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed && ANIM_ON && ANIM_POP) 0.93f else 1f, tween(ms(160)), label = "press")
    Column(modifier.graphicsLayer { scaleX = sc; scaleY = sc }.clickable(src, null, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(RADIUS.dp)).background(CARD), Alignment.Center) {
            if (v.cover.isNotEmpty()) AsyncImage(File(v.cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(v.title.take(1).uppercase(), fontSize = 40.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            if (progress > 0f) LinearProgressIndicator({ progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = ACC, trackColor = Color(0x99000000))
        }
        if (showTitle) Text(v.title, Modifier.padding(top = 5.dp), fontSize = 12.sp, color = Color(0xFFCCCCCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun Grid(l: List<Video>, cols: Int, open: (Video) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        l.chunked(cols).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.forEach { Poster(it, Modifier.weight(1f)) { open(it) } }
                repeat(cols - r.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ListRow(v: Video, open: (Video) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { open(v) }.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Poster(v, Modifier.width(84.dp), showTitle = false) { open(v) }
        Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
            Text(v.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(v.category, fontSize = 12.sp, color = ACC)
            if (v.desc.isNotBlank()) Text(v.desc, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Color(0xFFB3B3B3), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DropBox(label: String, items: List<String>, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.border(1.dp, Color(0xAAFFFFFF), RoundedCornerShape(3.dp)).clickable { open = true }.padding(horizontal = 12.dp, vertical = 7.dp)) { Text("$label ▾", fontSize = 13.sp) }
        DropdownMenu(open, { open = false }, Modifier.background(Color(0xFF1C1C1C))) {
            items.forEach { DropdownMenuItem({ Text(it) }, { open = false; pick(it) }) }
        }
    }
}

@Composable
fun Hero(f: Video, p: Profile, play: (Video) -> Unit, toggle: (Video) -> Unit) {
    val inf = rememberInfiniteTransition(label = "kb")
    val z by inf.animateFloat(1f, 1.1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "z")
    Box(Modifier.fillMaxWidth().height(540.dp).clipToBounds()) {
        if (f.cover.isNotEmpty()) AsyncImage(File(f.cover), null, Modifier.fillMaxSize().graphicsLayer { val s = if (ANIM_ON && ANIM_HERO) z else 1f; scaleX = s; scaleY = s }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x77000000), Color.Transparent, Color.Transparent, BG))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(f.title, fontSize = 32.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp)
            if (f.desc.isNotBlank()) Text(f.desc, Modifier.padding(top = 6.dp), color = Color(0xFFDDDDDD), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({ play(f) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(RADIUS.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Text(" Play", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Button({ toggle(f) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x88555555), contentColor = Color.White), shape = RoundedCornerShape(RADIUS.dp)) {
                    Icon(if (f.id in p.list) Icons.Default.Check else Icons.Default.Add, null); Text(" " + L("mylist", "My List"), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun KidsHeader(p: Profile, all: List<Video>, open: (Video) -> Unit) {
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        Stars(Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = 14.dp)) {
            Text("Hi ${p.name}!", Modifier.padding(horizontal = 20.dp), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Pick something fun to watch", Modifier.padding(start = 20.dp, bottom = 14.dp), color = Color(0xFFFFE9C7))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(all.take(10)) { v -> Poster(v, Modifier.width(112.dp).border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(RADIUS.dp)), showTitle = false) { open(v) } }
            }
        }
    }
}

@Composable
fun Home(st: Store, p: Profile, open: (Video) -> Unit, play: (Video) -> Unit, add: () -> Unit, toggle: (Video) -> Unit) {
    var filter by remember { mutableStateOf("All") }
    var grid by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf("Newest") }
    val all = st.videos.sortedByDescending { it.added }
    val cats = all.map { it.category }.distinct()
    if (filter != "All" && filter != "My List" && filter !in cats) filter = "All"
    val kids = p.kids
    val bg = if (kids) Brush.verticalGradient(listOf(Color(0xFF1B1464), Color(0xFF6A2BD9), Color(0xFFE8588F), Color(0xFFFFA24D), BG), 0f, 2000f) else SolidColor(BG)

    Box(Modifier.fillMaxSize().background(bg)) {
        Crossfade(filter, Modifier.fillMaxSize(), tween(ms(120)), label = "filter") { f ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (all.isEmpty()) item {
                    Column(Modifier.fillParentMaxSize().padding(36.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                        Logo(40); Spacer(Modifier.height(28.dp))
                        Text("Nothing to watch yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (SHOW_UPLOAD) {
                            Text("Add your own videos with a title, description and cover.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                            Button(add, colors = ButtonDefaults.buttonColors(containerColor = ACC), shape = RoundedCornerShape(RADIUS.dp)) { Text("Upload your first video", Modifier.padding(6.dp)) }
                        } else Text("Turn on Upload in Settings to add videos.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
                    }
                } else if (f == "All") {
                    item { if (kids) KidsHeader(p, all, open) else Hero(all.first(), p, play, toggle) }
                    val shelves = buildList<Triple<String, List<Video>, Boolean>> {
                        val cont = all.filter { val po = st.pos(it.id); val d = st.dur(it.id); po > 5000 && d > 0 && po < d - 5000 }
                        if (cont.isNotEmpty()) add(Triple(L("cont", "Continue Watching"), cont, true))
                        val mine = all.filter { it.id in p.list }
                        if (mine.isNotEmpty()) add(Triple(L("mylist", "My List"), mine, false))
                        add(Triple(L("recent", "Recently Added"), all, false))
                        cats.forEach { c -> add(Triple(c, all.filter { it.category == c }, false)) }
                    }
                    itemsIndexed(shelves) { i, (t, l, prog) -> Reveal(i) { ShelfRow(t, l, if (prog) st else null, open) } }
                } else {
                    item { Spacer(Modifier.height(140.dp)) }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(if (f == "My List") L("mylist", "My List") else f, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (f != "My List") DropBox("Genres", cats) { filter = it }
                                DropBox(sort, listOf("Newest", "Oldest", "A–Z")) { sort = it }
                                Spacer(Modifier.weight(1f))
                                Text("▦", Modifier.clickable { grid = true }.padding(6.dp), fontSize = 22.sp, color = if (grid) Color.White else Color.Gray)
                                Text("☰", Modifier.clickable { grid = false }.padding(6.dp), fontSize = 22.sp, color = if (!grid) Color.White else Color.Gray)
                            }
                        }
                    }
                    val base = if (f == "My List") all.filter { it.id in p.list } else all.filter { it.category == f }
                    val l = when (sort) { "A–Z" -> base.sortedBy { it.title.lowercase() }; "Oldest" -> base.sortedBy { it.added }; else -> base }
                    if (l.isEmpty()) item { Text("Nothing here yet.", Modifier.padding(20.dp), color = Color.Gray) }
                    else if (grid) item { Grid(l, 3, open) } else items(l) { ListRow(it, open) }
                }
            }
        }
        // Category filter strip only — logo/search/bell/avatar already live in the one global top bar.
        if (all.isNotEmpty()) Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(BG.copy(alpha = .92f), Color.Transparent))).padding(top = 10.dp, bottom = 22.dp)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                items(listOf("All") + cats + "My List") { c ->
                    val on = c == filter
                    val w by animateDpAsState(if (on) 24.dp else 0.dp, tween(ms(250)), label = "ul")
                    Column(Modifier.clickable(remember { MutableInteractionSource() }, null) { filter = c }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(when (c) { "All" -> L("home", "Home"); "My List" -> L("mylist", "My List"); else -> c }, fontSize = 15.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color(0xFFB3B3B3), maxLines = 1)
                        Box(Modifier.padding(top = 3.dp).height(2.dp).width(w).background(ACC))
                    }
                }
            }
        }
    }
}

@Composable
fun KeyGrid(q: String, set: (String) -> Unit) {
    val keys = "abcdefghijklmnopqrstuvwxyz0123456789".toList()
    Column(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF1A1A1A)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("␣", Modifier.weight(3f)) { set("$q ") }
            Key("⌫", Modifier.weight(3f)) { set(q.dropLast(1)) }
        }
        keys.chunked(6).forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { r.forEach { c -> Key(c.toString(), Modifier.weight(1f)) { set(q + c) } } }
        }
    }
}

@Composable
fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    Box(modifier.height(42.dp).clip(RoundedCornerShape(3.dp)).background(if (pr) ACC else Color(0xFF2C2C2C)).clickable(src, null, onClick = onClick), Alignment.Center) {
        Text(label, fontSize = 17.sp, color = Color(0xFFD0D0D0))
    }
}

@Composable
fun Search(st: Store, open: (Video) -> Unit) {
    var q by remember { mutableStateOf("") }
    var kb by remember { mutableStateOf(SEARCH_GRID) }
    val r = st.videos.filter { q.isBlank() || it.title.contains(q, true) || it.category.contains(q, true) || it.desc.contains(q, true) }.sortedBy { it.title.lowercase() }
    val sug = if (q.isBlank()) emptyList() else st.videos.map { it.title }.filter { it.contains(q.trim(), true) }.distinct().take(6)

    @Composable fun Field() {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text(L("searchph", "Search titles, categories…")) }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(RADIUS.dp),
            trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Default.Close, null) } })
    }
    @Composable fun Suggest() {
        sug.forEach { s -> Text(s, Modifier.fillMaxWidth().clickable { q = s }.padding(vertical = 9.dp, horizontal = 4.dp), color = Color(0xFFB3B3B3), fontSize = 15.sp, maxLines = 1) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(L("searchTitle", "Search"), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Field(); if (kb) KeyGrid(q) { q = it }; Suggest()
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Grid(r, 4, open) }
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L("searchTitle", "Search"), Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Black)
                        TextButton({ kb = !kb }) { Text(if (kb) "Hide letters" else "Letter grid", color = ACC) }
                    }
                    Field()
                    AnimatedVisibility(kb, enter = expandVertically(tween(ms(300))) + fadeIn(tween(ms(300))), exit = shrinkVertically(tween(ms(250))) + fadeOut(tween(ms(200)))) { KeyGrid(q) { q = it } }
                    Suggest()
                }
            }
            item { Text(if (q.isBlank()) "All videos" else "Results", Modifier.padding(start = 16.dp, bottom = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (r.isEmpty()) item { Text("No matches.", Modifier.padding(16.dp), color = Color.Gray) }
            item { Grid(r, 3, open) }
        }
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
                Button(play, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(RADIUS.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Text(if (resume) " Resume" else " Play", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (resume) LinearProgressIndicator({ st.pos(v.id).toFloat() / st.dur(v.id) }, Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp), color = ACC, trackColor = CARD)
                if (v.desc.isNotBlank()) Text(v.desc, Modifier.padding(top = 16.dp), lineHeight = 22.sp, color = Color(0xFFDDDDDD))
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Action(if (v.id in p.list) Icons.Default.Check else Icons.Default.Add, L("mylist", "My List"), toggle)
                    if (SHOW_UPLOAD) { Action(Icons.Default.Edit, "Edit", edit); Action(Icons.Default.Delete, "Delete") { confirm = true } }
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
        Icon(icon, null, Modifier.size(26.dp)); Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
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
