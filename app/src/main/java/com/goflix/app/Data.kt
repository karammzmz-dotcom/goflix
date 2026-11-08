package com.goflix.app

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Profile(val id: String = UUID.randomUUID().toString(), val name: String, val avatar: String = "b:0", val kids: Boolean = false, val list: List<String> = emptyList())
data class Video(val id: String = UUID.randomUUID().toString(), val title: String, val desc: String, val uri: String, val cover: String, val category: String = "My Videos", val added: Long = System.currentTimeMillis())

class Store(val ctx: Context) {
    val profiles = mutableStateListOf<Profile>()
    val videos = mutableStateListOf<Video>()
    private val file = File(ctx.filesDir, "goflix.json")
    private val sp = ctx.getSharedPreferences("goflix", 0)
    fun flag(k: String, d: Boolean = true) = sp.getBoolean(k, d)
    fun setFlag(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    init {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                val pa = o.getJSONArray("p")
                for (i in 0 until pa.length()) {
                    val x = pa.getJSONObject(i); val l = x.getJSONArray("list")
                    profiles.add(Profile(x.getString("id"), x.getString("name"), x.getString("avatar"), x.getBoolean("kids"), (0 until l.length()).map { l.getString(it) }))
                }
                val va = o.getJSONArray("v")
                for (i in 0 until va.length()) {
                    val x = va.getJSONObject(i)
                    videos.add(Video(x.getString("id"), x.getString("title"), x.getString("desc"), x.getString("uri"), x.getString("cover"), x.getString("cat"), x.getLong("added")))
                }
            }
        } catch (e: Exception) { }
    }

    fun save() {
        val o = JSONObject()
        val pa = JSONArray(); profiles.forEach { p -> pa.put(JSONObject().put("id", p.id).put("name", p.name).put("avatar", p.avatar).put("kids", p.kids).put("list", JSONArray(p.list))) }
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
    fun copyImage(uri: Uri): String {
        val f = File(ctx.filesDir, "img_${UUID.randomUUID()}.jpg")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f.path
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
    fun play(c: Context, res: Int, vol: Float = 1f) {
        MediaPlayer.create(c, res)?.apply { setVolume(vol, vol); setOnCompletionListener { it.release() }; start() }
    }
    fun ambient(c: Context, on: Boolean) {
        amb?.release(); amb = null
        if (on) amb = MediaPlayer.create(c, R.raw.ambient)?.apply { isLooping = true; setVolume(.25f, .25f); start() }
    }
    fun pause() { try { amb?.pause() } catch (e: Exception) { } }
    fun resume() { try { amb?.start() } catch (e: Exception) { } }
}
