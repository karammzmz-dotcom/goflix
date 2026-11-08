package com.goflix.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun Section(t: String) {
    Text(t.uppercase(), Modifier.padding(top = 26.dp, bottom = 6.dp), color = ACC, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
fun Toggle(st: Store, key: String, title: String, sub: String) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(st.flag(key)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp); Text(sub, color = Color.Gray, fontSize = 12.sp) }
        Switch(on, { on = it; st.setFlag(key, it); Sfx.ambient(ctx, st.flag("sound") && st.flag("ambient"), st.str("ambientPath")) }, colors = SwitchDefaults.colors(checkedTrackColor = ACC))
    }
}

@Composable
fun FileRow(st: Store, key: String, title: String, sub: String, mimes: Array<String>) {
    val ctx = LocalContext.current
    var path by remember { mutableStateOf(st.str(key)) }
    fun refresh() { if (key == "ambientPath") Sfx.ambient(ctx, st.flag("sound") && st.flag("ambient"), st.str("ambientPath")) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { path = st.copyFile(it, key); st.put(key, path); refresh() }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(if (path.isEmpty()) sub else "Custom file added", color = if (path.isEmpty()) Color.Gray else ACC, fontSize = 12.sp)
        }
        if (path.isNotEmpty()) TextButton({ File(path).delete(); path = ""; st.put(key, ""); refresh() }) { Text("Reset", color = Color.Gray) }
        OutlinedButton({ pick.launch(mimes) }, shape = RoundedCornerShape(RADIUS.dp)) { Text(if (path.isEmpty()) "Add" else "Change", color = Color.White) }
    }
}

@Composable
fun Swatch(c: Color, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(c).border(2.dp, if (on) Color.White else Color.Transparent, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        if (on) Icon(Icons.Default.Check, null, tint = Color.White)
    }
}

@Composable
fun Chip(label: String, on: Boolean, ff: FontFamily = FONT, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) ACC else CARD).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(label, fontFamily = ff, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun SettingsScreen(st: Store, p: Profile, switch: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(st.str("name", "GOFLIX")) }
    var hex by remember { mutableStateOf(st.str("accent", "#E50914")) }
    var radius by remember { mutableStateOf(RADIUS.toFloat()) }
    var poster by remember { mutableStateOf(POSTER.toFloat()) }
    val logoPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { st.put("logo", st.copyImage(it)) } }
    val fontPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { st.put("font", st.copyFile(it, "font")) } }
    val font = st.str("font", "default")

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(RADIUS.dp)).background(CARD).clickable(onClick = switch).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.avatar, 52.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Switch or manage profiles", color = Color.Gray, fontSize = 13.sp) }
        }

        Section("Appearance")
        OutlinedTextField(name, { name = it; st.put("name", it) }, Modifier.fillMaxWidth(), label = { Text("App name (shown in the app)") }, singleLine = true)
        FileRow2(st, "Logo image", "Use an image instead of the name", logoPick = { logoPick.launch("image/*") }, clear = { st.put("logo", "") })

        Text("Accent color", Modifier.padding(top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 13.sp)
        val presets = listOf("#E50914", "#FF6A00", "#F5C518", "#2ECC71", "#00BCD4", "#2979FF", "#8E44AD", "#FF4081", "#FFFFFF")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(presets) { h -> Swatch(Color(android.graphics.Color.parseColor(h)), hex.equals(h, true)) { hex = h; st.put("accent", h) } }
        }
        OutlinedTextField(hex, { hex = it; if (Regex("#[0-9a-fA-F]{6}").matches(it)) st.put("accent", it) }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Custom color (#RRGGBB)") }, singleLine = true)

        Text("Font", Modifier.padding(top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("default" to FontFamily.Default, "sans" to FontFamily.SansSerif, "serif" to FontFamily.Serif, "mono" to FontFamily.Monospace, "cursive" to FontFamily.Cursive)) { (k, f) ->
                Chip(k.replaceFirstChar { it.uppercase() }, font == k, f) { st.put("font", k) }
            }
            item { Chip(if (font.contains("/")) "Custom ✓" else "Import .ttf / .otf", font.contains("/")) { fontPick.launch("*/*") } }
        }

        Text("Background", Modifier.padding(top = 16.dp, bottom = 8.dp), color = Color.Gray, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("black" to "Black", "charcoal" to "Charcoal", "midnight" to "Midnight", "wine" to "Wine")) { (k, l) -> Chip(l, st.str("bg", "black") == k) { st.put("bg", k) } }
        }

        Text("Corner roundness: ${radius.toInt()}", Modifier.padding(top = 16.dp), color = Color.Gray, fontSize = 13.sp)
        Slider(radius, { radius = it; RADIUS = it.toInt() }, valueRange = 0f..24f, onValueChangeFinished = { st.put("radius", radius.toInt().toString()) }, colors = SliderDefaults.colors(thumbColor = ACC, activeTrackColor = ACC))
        Text("Poster size: ${poster.toInt()}", color = Color.Gray, fontSize = 13.sp)
        Slider(poster, { poster = it; POSTER = it.toInt() }, valueRange = 90f..170f, onValueChangeFinished = { st.put("poster", poster.toInt().toString()) }, colors = SliderDefaults.colors(thumbColor = ACC, activeTrackColor = ACC))

        Section("Intro & sounds")
        Toggle(st, "intro", "Play intro on launch", "Shown every time the app opens")
        Toggle(st, "sound", "Sound effects", "Master switch for all sounds")
        Toggle(st, "ambient", "Ambient background", "Loops softly while you browse")
        FileRow(st, "introPath", "Launch intro", "Default: animated logo", arrayOf("video/*", "audio/*"))
        FileRow(st, "pIntroPath", "Profile-open intro", "Default: none", arrayOf("video/*", "audio/*"))
        FileRow(st, "clickPath", "Click sound", "Default: built-in", arrayOf("audio/*"))
        FileRow(st, "ambientPath", "Ambient sound", "Default: built-in", arrayOf("audio/*"))

        Section("Library")
        OutlinedButton({ confirm = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(RADIUS.dp)) { Text("Remove all videos from app", color = ACC) }
        OutlinedButton({ st.resetLook(); name = "GOFLIX"; hex = "#E50914"; radius = 8f; poster = 120f }, Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(RADIUS.dp)) { Text("Reset appearance to default", color = Color.White) }
        Text("$APP_NAME  ·  v1.0", Modifier.padding(top = 24.dp, bottom = 12.dp), color = Color.Gray, fontSize = 12.sp)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, containerColor = Color(0xFF1A1A1A),
        title = { Text("Remove all videos?") }, text = { Text("Your original files stay on your phone.") },
        confirmButton = { TextButton({ st.clearLibrary(); confirm = false }) { Text("Remove", color = ACC) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = Color.Gray) } })
}

@Composable
fun FileRow2(st: Store, title: String, sub: String, logoPick: () -> Unit, clear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(if (LOGO_IMG.isEmpty()) sub else "Custom logo set", color = if (LOGO_IMG.isEmpty()) Color.Gray else ACC, fontSize = 12.sp)
        }
        if (LOGO_IMG.isNotEmpty()) TextButton(clear) { Text("Reset", color = Color.Gray) }
        OutlinedButton(logoPick, shape = RoundedCornerShape(RADIUS.dp)) { Text(if (LOGO_IMG.isEmpty()) "Add" else "Change", color = Color.White) }
    }
}
