package com.goflix.app

import android.content.Context
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// ---- Live theme state (changed from Settings, applies instantly) ----
var ACC by mutableStateOf(Color(0xFFE50914))
var APP_NAME by mutableStateOf("GOFLIX")
var LOGO_IMG by mutableStateOf("")
var BG by mutableStateOf(Color.Black)
var CARD by mutableStateOf(Color(0xFF1C1C1C))
var RADIUS by mutableStateOf(8)
var POSTER by mutableStateOf(120)
var FONT by mutableStateOf<FontFamily>(FontFamily.Default)

var ANIM_ON by mutableStateOf(true)
var ANIM_SPEED by mutableStateOf(1f)
var ANIM_STYLE by mutableStateOf("slide")
var ANIM_POP by mutableStateOf(true)
var ANIM_STAGGER by mutableStateOf(true)
var ANIM_HERO by mutableStateOf(true)
var SHOW_UPLOAD by mutableStateOf(true)
var IS_TV by mutableStateOf(false)
fun hashPin(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(("goflix" + s).toByteArray()).joinToString("") { "%02x".format(it) }
var SEARCH_GRID by mutableStateOf(true)
fun ms(x: Int): Int = if (ANIM_ON) (x / ANIM_SPEED).toInt().coerceAtLeast(1) else 0

val LBL = mutableStateMapOf<String, String>()
// key, title in settings, default text
val LABELS = listOf(
    Triple("homeName", "Home header name (blank = app name)", ""),
    Triple("searchTitle", "Search page title", "Search"),
    Triple("searchph", "Search box hint", "Search titles, categories…"),
    Triple("home", "Home tab", "Home"),
    Triple("search", "Search tab", "Search"),
    Triple("upload", "Upload tab", "Upload"),
    Triple("settings", "Settings tab / title", "Settings"),
    Triple("mylist", "My List", "My List"),
    Triple("cont", "Continue Watching row", "Continue Watching"),
    Triple("recent", "Recently Added row", "Recently Added"),
    Triple("who", "Profile screen title", "Who's watching?"),
    Triple("manage", "Manage profiles button", "MANAGE PROFILES"),
    Triple("addprofile", "Add profile label", "Add Profile"),
    Triple("play", "Play button", "Play"),
    Triple("moreinfo", "More Info button", "More Info"),
    Triple("menuManage", "Profile menu: manage", "Manage Profiles"),
    Triple("menuSwitch", "Profile menu: switch", "Switch Profile"),
    Triple("menuSettings", "Profile menu: settings", "Account & Settings"),
    Triple("menuOut", "Profile menu: sign out", "Sign out")
)
fun L(k: String, d: String): String = LBL[k]?.takeIf { it.isNotBlank() } ?: d

data class Profile(val id: String = UUID.randomUUID().toString(), val name: String, val avatar: String = "b:0", val kids: Boolean = false, val list: List<String> = emptyList(), val pin: String = "")
data class Video(val id: String = UUID.randomUUID().toString(), val title: String, val desc: String, val uri: String, val cover: String, val category: String = "My Videos", val added: Long = System.currentTimeMillis())

class Store(val ctx: Context) {
    val profiles = mutableStateListOf<Profile>()
    val videos = mutableStateListOf<Video>()
    private val file = File(ctx.filesDir, "goflix.json")
    private val sp = ctx.getSharedPreferences("goflix", 0)
    fun flag(k: String, d: Boolean = true) = sp.getBoolean(k, d)
    fun setFlag(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply(); applyAll() }
    fun str(k: String, d: String = "") = sp.getString(k, d) ?: d
    fun put(k: String, v: String, persist: Boolean = true) { if (persist) sp.edit().putString(k, v).apply(); applyAll() }
    fun pos(id: String) = sp.getLong("pos_$id", 0L)
    fun dur(id: String) = sp.getLong("dur_$id", 0L)
    fun savePos(id: String, p: Long, d: Long) = sp.edit().putLong("pos_$id", p).putLong("dur_$id", d).apply()
    fun resetLook() {
        val e = sp.edit(); (listOf("name", "accent", "bg", "font", "radius", "poster", "logo") + LABELS.map { "l_" + it.first }).forEach { e.remove(it) }; e.apply(); applyAll()
    }

    fun applyAll() {
        ACC = try { Color(android.graphics.Color.parseColor(str("accent", "#E50914"))) } catch (e: Exception) { Color(0xFFE50914) }
        APP_NAME = str("name", "GOFLIX").ifBlank { "GOFLIX" }
        LOGO_IMG = str("logo")
        BG = when (str("bg", "black")) { "charcoal" -> Color(0xFF141414); "midnight" -> Color(0xFF0A0F1E); "wine" -> Color(0xFF14080B); else -> Color.Black }
        CARD = lerp(BG, Color.White, 0.11f)
        RADIUS = str("radius", "8").toIntOrNull() ?: 8
        POSTER = str("poster", "120").toIntOrNull() ?: 120
        ANIM_ON = flag("anim"); ANIM_SPEED = (str("animSpeed", "1").toFloatOrNull() ?: 1f).coerceIn(0.4f, 3f)
        ANIM_STYLE = str("animStyle", "slide"); ANIM_POP = flag("animPop"); ANIM_STAGGER = flag("animStagger"); ANIM_HERO = flag("animHero")
        SHOW_UPLOAD = flag("showUpload"); SEARCH_GRID = flag("searchGrid")
        LABELS.forEach { (k, _, _) -> LBL[k] = str("l_$k") }
        FONT = when (val f = str("font", "default")) {
            "default" -> FontFamily.Default; "sans" -> FontFamily.SansSerif; "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace; "cursive" -> FontFamily.Cursive
            else -> try { FontFamily(Typeface.createFromFile(File(f))) } catch (e: Exception) { FontFamily.Default }
        }
    }

    init {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                val pa = o.getJSONArray("p")
                for (i in 0 until pa.length()) {
                    val x = pa.getJSONObject(i); val l = x.getJSONArray("list")
                    profiles.add(Profile(x.getString("id"), x.getString("name"), x.getString("avatar"), x.getBoolean("kids"), (0 until l.length()).map { l.getString(it) }, x.optString("pin", "")))
                }
                val va = o.getJSONArray("v")
                for (i in 0 until va.length()) {
                    val x = va.getJSONObject(i)
                    videos.add(Video(x.getString("id"), x.getString("title"), x.getString("desc"), x.getString("uri"), x.getString("cover"), x.getString("cat"), x.getLong("added")))
                }
            }
        } catch (e: Exception) { }
        IS_TV = ctx.packageManager.hasSystemFeature("android.software.leanback") ||
            (ctx.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        applyAll()
    }

    fun save() {
        val o = JSONObject()
        val pa = JSONArray(); profiles.forEach { p -> pa.put(JSONObject().put("id", p.id).put("name", p.name).put("avatar", p.avatar).put("kids", p.kids).put("list", JSONArray(p.list)).put("pin", p.pin)) }
        val va = JSONArray(); videos.forEach { v -> va.put(JSONObject().put("id", v.id).put("title", v.title).put("desc", v.desc).put("uri", v.uri).put("cover", v.cover).put("cat", v.category).put("added", v.added)) }
        o.put("p", pa); o.put("v", va); file.writeText(o.toString())
    }

    fun upsertProfile(p: Profile) { val i = profiles.indexOfFirst { it.id == p.id }; if (i >= 0) profiles[i] = p else profiles.add(p); save() }
    fun deleteProfile(p: Profile) { profiles.removeAll { it.id == p.id }; save() }
    fun upsertVideo(v: Video) { val i = videos.indexOfFirst { it.id == v.id }; if (i >= 0) videos[i] = v else videos.add(v); save() }
    fun deleteVideo(v: Video) { videos.removeAll { it.id == v.id }; save() }
    fun clearLibrary() { videos.clear(); save() }
    fun toggleList(p: Profile, vid: String): Profile {
        val n = p.copy(list = if (vid in p.list) p.list - vid else p.list + vid); upsertProfile(n); return n
    }
    fun copyImage(uri: Uri): String = copyFile(uri, "img")
    fun copyFile(uri: Uri, prefix: String): String {
        val ext = displayName(uri.toString()).substringAfterLast('.', "bin")
        val f = File(ctx.filesDir, "${prefix}_${UUID.randomUUID()}.$ext")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f.path
    }
    fun displayName(uri: String): String {
        var n = "file"
        try { ctx.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) n = c.getString(i) } } catch (e: Exception) { }
        return n
    }
    fun downloadImage(url: String, done: (String?) -> Unit) {
        Thread {
            try {
                val f = File(ctx.filesDir, "img_${UUID.randomUUID()}.jpg")
                java.net.URL(url.trim()).openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                done(f.path)
            } catch (e: Exception) { done(null) }
        }.start()
    }
}

object Sfx {
    private var amb: MediaPlayer? = null
    private fun mk(c: Context, res: Int, path: String): MediaPlayer? = try {
        if (path.isNotEmpty() && File(path).exists()) MediaPlayer().apply { setDataSource(path); prepare() } else MediaPlayer.create(c, res)
    } catch (e: Exception) { MediaPlayer.create(c, res) }
    fun play(c: Context, res: Int, path: String = "", vol: Float = 1f) {
        mk(c, res, path)?.apply { setVolume(vol, vol); setOnCompletionListener { it.release() }; start() }
    }
    fun ambient(c: Context, on: Boolean, path: String = "") {
        amb?.release(); amb = null
        if (on) amb = mk(c, R.raw.ambient, path)?.apply { isLooping = true; setVolume(.3f, .3f); start() }
    }
    fun pause() { try { amb?.pause() } catch (e: Exception) { } }
    fun resume() { try { amb?.start() } catch (e: Exception) { } }
}
