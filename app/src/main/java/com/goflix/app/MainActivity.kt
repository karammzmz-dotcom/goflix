package com.goflix.app

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch

val RED = Color(0xFFE50914)
val GRAY = Color(0xFF2A2A2A)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val st = Store(this)
        setContent { GoflixApp(st) }
    }
    override fun onPause() { super.onPause(); Sfx.pause() }
    override fun onResume() { super.onResume(); Sfx.resume() }
}

enum class Scr { INTRO, PROFILES, HOME, ADD, DETAIL, PLAYER, SETTINGS }

@Composable
fun GoflixApp(st: Store) {
    val ctx = LocalContext.current
    var scr by remember { mutableStateOf(if (st.flag("intro")) Scr.INTRO else Scr.PROFILES) }
    var cur by remember { mutableStateOf<Profile?>(null) }
    var sel by remember { mutableStateOf<Video?>(null) }
    var edit by remember { mutableStateOf<Video?>(null) }
    var tab by remember { mutableStateOf(0) }
    fun click() { if (st.flag("sound")) Sfx.play(ctx, R.raw.click) }

    LaunchedEffect(scr) {
        Sfx.ambient(ctx, st.flag("sound") && st.flag("ambient") && scr != Scr.INTRO && scr != Scr.PLAYER)
    }
    BackHandler(scr != Scr.HOME && scr != Scr.INTRO && scr != Scr.PROFILES) {
        scr = if (scr == Scr.PLAYER) Scr.DETAIL else Scr.HOME
    }
    if (st.profiles.isEmpty() && scr == Scr.PROFILES) {
        // first launch: nothing to do, Profiles screen shows the "add" tile
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = RED, background = Color.Black, surface = Color(0xFF141414))) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            when (scr) {
                Scr.INTRO -> Intro { scr = Scr.PROFILES }
                Scr.PROFILES -> Profiles(st) { cur = it; click(); scr = Scr.HOME }
                Scr.HOME -> cur?.let { p ->
                    Home(st, p, tab, { tab = it },
                        open = { click(); sel = it; scr = Scr.DETAIL },
                        play = { sel = it; scr = Scr.PLAYER },
                        add = { edit = null; scr = Scr.ADD },
                        settings = { scr = Scr.SETTINGS },
                        switch = { scr = Scr.PROFILES },
                        toggle = { v -> cur = st.toggleList(p, v.id) })
                }
                Scr.ADD -> AddEdit(st, edit) { scr = Scr.HOME }
                Scr.DETAIL -> sel?.let { v ->
                    cur?.let { p ->
                        Detail(st, p, v, back = { scr = Scr.HOME }, play = { scr = Scr.PLAYER },
                            edit = { edit = v; scr = Scr.ADD },
                            toggle = { cur = st.toggleList(p, v.id) })
                    }
                }
                Scr.PLAYER -> sel?.let { Player(it) { scr = Scr.DETAIL } }
                Scr.SETTINGS -> cur?.let { p -> SettingsScreen(st, p, { scr = Scr.HOME }, { scr = Scr.PROFILES }) }
            }
        }
    }
}

@Composable
fun Logo(size: Int = 34) {
    Text("GOFLIX", color = RED, fontSize = size.sp, fontWeight = FontWeight.Black, letterSpacing = (size / 8).sp)
}

@Composable
fun Intro(done: () -> Unit) {
    val ctx = LocalContext.current
    val sc = remember { Animatable(0.7f) }
    val al = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val st = Store(ctx)
        if (st.flag("sound")) Sfx.play(ctx, R.raw.intro)
        launch { al.animateTo(1f, tween(900)) }
        sc.animateTo(1.5f, tween(3200, easing = LinearEasing))
        al.animateTo(0f, tween(500))
        done()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        Text("GOFLIX", color = RED, fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
            modifier = Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = al.value })
    }
}

@Composable
fun Avatar(a: String, size: Dp) {
    val colors = listOf(Color(0xFFE50914), Color(0xFF1E88E5), Color(0xFFFBC02D), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFFFB8C00))
    val faces = listOf(":)", "^_^", "B)", ":D", "o_o", "UwU")
    val m = Modifier.size(size).clip(RoundedCornerShape(6.dp))
    if (a.startsWith("b:")) {
        val i = a.substring(2).toIntOrNull() ?: 0
        Box(m.background(colors[i % 6]), Alignment.Center) {
            Text(faces[i % 6], fontSize = (size.value * 0.4f).sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    } else AsyncImage(File(a), null, m.background(GRAY), contentScale = ContentScale.Crop)
}

@Composable
fun Profiles(st: Store, onPick: (Profile) -> Unit) {
    var manage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp)); Logo()
        Spacer(Modifier.height(32.dp))
        Text(if (manage) "Manage Profiles" else "Who's watching?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(st.profiles.toList()) { p ->
                Column(Modifier.clickable { if (manage) editing = p else onPick(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        Avatar(p.avatar, 110.dp)
                        if (manage) Box(Modifier.size(110.dp).background(Color(0x99000000)), Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(36.dp)) }
                    }
                    Text(p.name, Modifier.padding(top = 8.dp), color = Color.LightGray)
                    if (p.kids) Text("KIDS", color = Color.Gray, fontSize = 11.sp)
                }
            }
            if (st.profiles.size < 5) item {
                Column(Modifier.clickable { editing = Profile(name = "") }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(110.dp).border(2.dp, Color.Gray, RoundedCornerShape(6.dp)), Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(44.dp), tint = Color.Gray) }
                    Text("Add Profile", Modifier.padding(top = 8.dp), color = Color.LightGray)
                }
            }
        }
        OutlinedButton(onClick = { manage = !manage }, shape = RoundedCornerShape(2.dp)) { Text(if (manage) "DONE" else "MANAGE PROFILES", color = Color.LightGray) }
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
    AlertDialog(onDismissRequest = close, containerColor = Color(0xFF181818),
        title = { Text(if (exists) "Edit Profile" else "Add Profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { Avatar(av, 90.dp) }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.padding(top = 12.dp))
                Text("Choose an icon", Modifier.padding(top = 12.dp), color = Color.Gray)
                LazyRow { items(6) { i -> Box(Modifier.padding(4.dp).clickable { av = "b:$i" }) { Avatar("b:$i", 48.dp) } } }
                TextButton({ pick.launch("image/*") }) { Text("PICK FROM GALLERY", color = RED) }
                OutlinedTextField(url, { url = it }, label = { Text("Or paste image link") }, singleLine = true,
                    trailingIcon = { TextButton({ st.downloadImage(url) { r -> if (r != null) av = r } }) { Text("GET") } })
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { Switch(kids, { kids = it }); Text("  Kids profile") }
            }
        },
        confirmButton = { TextButton({ if (name.isNotBlank()) { st.upsertProfile(p.copy(name = name.trim(), avatar = av, kids = kids)); close() } }) { Text("SAVE", color = RED) } },
        dismissButton = {
            Row {
                if (exists && st.profiles.size > 1) TextButton({ st.deleteProfile(p); close() }) { Text("DELETE", color = RED) }
                TextButton(close) { Text("CANCEL", color = Color.Gray) }
            }
        })
}

@Composable
fun Poster(v: Video, w: Dp = 112.dp, onClick: () -> Unit) {
    AsyncImage(File(v.cover), v.title, Modifier.width(w).height(w * 1.5f).clip(RoundedCornerShape(4.dp)).background(GRAY).clickable(onClick = onClick), contentScale = ContentScale.Crop)
}

@Composable
fun Home(st: Store, p: Profile, tab: Int, onTab: (Int) -> Unit, open: (Video) -> Unit, play: (Video) -> Unit,
         add: () -> Unit, settings: () -> Unit, switch: () -> Unit, toggle: (Video) -> Unit) {
    val recent = st.videos.sortedByDescending { it.added }
    Scaffold(containerColor = Color.Black, bottomBar = {
        NavigationBar(containerColor = Color(0xFF0A0A0A)) {
            val c = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF333333))
            NavigationBarItem(tab == 0, { onTab(0) }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") }, colors = c)
            NavigationBarItem(tab == 1, { onTab(1) }, { Icon(Icons.Default.Check, null) }, label = { Text("My List") }, colors = c)
            NavigationBarItem(false, add, { Icon(Icons.Default.Add, null) }, label = { Text("Upload") }, colors = c)
            NavigationBarItem(false, settings, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, colors = c)
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (recent.isEmpty()) item {
                Column(Modifier.fillParentMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Logo(); Spacer(Modifier.height(24.dp))
                    Text("Your library is empty", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Upload your own videos with a title, description and cover.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                    Button(add, colors = ButtonDefaults.buttonColors(containerColor = RED), shape = RoundedCornerShape(4.dp)) { Text("UPLOAD A VIDEO") }
                }
            } else if (tab == 0) {
                val f = recent.first()
                item {
                    Box(Modifier.fillMaxWidth().height(480.dp)) {
                        AsyncImage(File(f.cover), null, Modifier.fillMaxSize().background(GRAY), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent, Color.Black))))
                        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Logo(26); Spacer(Modifier.weight(1f)); Box(Modifier.clickable(onClick = switch)) { Avatar(p.avatar, 32.dp) }
                        }
                        Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(f.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(f.category, color = Color.LightGray, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button({ play(f) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(4.dp)) {
                                    Icon(Icons.Default.PlayArrow, null); Text("Play", fontWeight = FontWeight.Bold)
                                }
                                Button({ toggle(f) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0x99333333)), shape = RoundedCornerShape(4.dp)) {
                                    Icon(if (f.id in p.list) Icons.Default.Check else Icons.Default.Add, null); Text(" My List")
                                }
                            }
                        }
                    }
                }
                val mine = recent.filter { it.id in p.list }
                if (mine.isNotEmpty()) item { Row2("My List", mine, open) }
                item { Row2("Recently Added", recent, open) }
                st.videos.groupBy { it.category }.forEach { (c, l) -> item { Row2(c, l, open) } }
            } else {
                item { Text("My List", Modifier.statusBarsPadding().padding(16.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                val mine = recent.filter { it.id in p.list }
                if (mine.isEmpty()) item { Text("Nothing here yet. Add videos from their detail page.", Modifier.padding(16.dp), color = Color.Gray) }
                items(mine.chunked(3)) { r ->
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { Poster(it, 110.dp) { open(it) } } }
                }
            }
        }
    }
}

@Composable
fun Row2(title: String, l: List<Video>, open: (Video) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(title, Modifier.padding(start = 16.dp, bottom = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(l) { Poster(it) { open(it) } } }
    }
}

@Composable
fun Detail(st: Store, p: Profile, v: Video, back: () -> Unit, play: () -> Unit, edit: () -> Unit, toggle: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            AsyncImage(File(v.cover), null, Modifier.fillMaxWidth().height(340.dp).background(GRAY), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color.Black))))
            IconButton(back, Modifier.statusBarsPadding()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }
        Column(Modifier.padding(16.dp)) {
            Text(v.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(v.category, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
            Button(play, Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(4.dp)) {
                Icon(Icons.Default.PlayArrow, null); Text("Play", fontWeight = FontWeight.Bold)
            }
            Text(v.desc, Modifier.padding(top = 16.dp), lineHeight = 22.sp)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(toggle) { Icon(if (v.id in p.list) Icons.Default.Check else Icons.Default.Add, null, tint = Color.White); Text(" My List", color = Color.White) }
                TextButton(edit) { Icon(Icons.Default.Edit, null, tint = Color.White); Text(" Edit", color = Color.White) }
                TextButton({ confirm = true }) { Icon(Icons.Default.Delete, null, tint = RED); Text(" Delete", color = RED) }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color(0xFF181818),
        title = { Text("Delete \"${v.title}\"?") }, text = { Text("This removes it from Goflix. Your original file is not deleted.") },
        confirmButton = { TextButton({ st.deleteVideo(v); confirm = false; back() }) { Text("DELETE", color = RED) } },
        dismissButton = { TextButton({ confirm = false }) { Text("CANCEL") } })
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
        }
    }
    val cp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { cover = st.copyImage(it) } }
    BackHandler { done() }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(done) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(if (old == null) "Upload to Goflix" else "Edit video", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally).width(150.dp).height(225.dp).clip(RoundedCornerShape(6.dp)).background(GRAY).clickable { cp.launch("image/*") }, Alignment.Center) {
            if (cover.isNotEmpty()) AsyncImage(File(cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("Tap to add\ncover", textAlign = TextAlign.Center, color = Color.Gray)
        }
        OutlinedButton({ vp.launch(arrayOf("video/*")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
            Text(if (uri.isEmpty()) "SELECT VIDEO (MP4)" else "VIDEO SELECTED  ✓  (tap to change)", color = Color.White)
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Title") }, singleLine = true)
        OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Description") }, minLines = 3)
        OutlinedTextField(cat, { cat = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Row / category (e.g. Movies)") }, singleLine = true)
        Button({
            st.upsertVideo((old ?: Video(title = "", desc = "", uri = "", cover = "")).copy(title = title.trim(), desc = desc.trim(), uri = uri, cover = cover, category = cat.trim().ifEmpty { "My Videos" }))
            done()
        }, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = title.isNotBlank() && uri.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = RED), shape = RoundedCornerShape(4.dp)) {
            Text("SAVE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Player(v: Video, close: () -> Unit) {
    val ctx = LocalContext.current
    val ex = remember { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(Uri.parse(v.uri))); prepare(); playWhenReady = true } }
    DisposableEffect(Unit) {
        val a = ctx as Activity
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { ex.release(); a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ PlayerView(it).apply { player = ex; setShowNextButton(false); setShowPreviousButton(false) } }, Modifier.fillMaxSize())
        IconButton(close, Modifier.statusBarsPadding()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
    }
}

@Composable
fun Toggle(st: Store, key: String, title: String, sub: String) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(st.flag(key)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(sub, color = Color.Gray, fontSize = 12.sp) }
        Switch(on, { on = it; st.setFlag(key, it); Sfx.ambient(ctx, st.flag("sound") && st.flag("ambient")) }, colors = SwitchDefaults.colors(checkedTrackColor = RED))
    }
}

@Composable
fun SettingsScreen(st: Store, p: Profile, back: () -> Unit, switch: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatar, 56.dp); Column(Modifier.padding(start = 12.dp)) { Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(if (p.kids) "Kids profile" else "Goflix member", color = Color.Gray) }
        }
        HorizontalDivider(color = GRAY)
        Toggle(st, "intro", "Intro animation", "Play the Goflix intro when the app opens")
        Toggle(st, "sound", "Sound effects", "Intro and click sounds")
        Toggle(st, "ambient", "Ambient background", "Soft looping ambience while browsing")
        HorizontalDivider(color = GRAY)
        TextButton(switch, Modifier.fillMaxWidth()) { Text("Switch / manage profiles", Modifier.fillMaxWidth(), color = Color.White) }
        TextButton({ confirm = true }, Modifier.fillMaxWidth()) { Text("Remove all videos from library", Modifier.fillMaxWidth(), color = RED) }
        TextButton(switch, Modifier.fillMaxWidth()) { Text("Sign out", Modifier.fillMaxWidth(), color = Color.White) }
        Text("Goflix 1.0", Modifier.padding(top = 24.dp), color = Color.Gray, fontSize = 12.sp)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color(0xFF181818),
        title = { Text("Remove all videos?") }, text = { Text("Your original files are not deleted.") },
        confirmButton = { TextButton({ st.clearLibrary(); confirm = false }) { Text("REMOVE", color = RED) } },
        dismissButton = { TextButton({ confirm = false }) { Text("CANCEL") } })
}
