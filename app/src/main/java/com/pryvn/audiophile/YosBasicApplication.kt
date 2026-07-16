package com.pryvn.audiophile

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.funny.data_saver.core.DataSaverConverter.registerTypeConverters
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.pryvn.audiophile.code.MediaController.mediaControl
import com.pryvn.audiophile.code.MediaController.playingMusicList
import com.pryvn.audiophile.code.YosPlaybackService
import com.pryvn.audiophile.data.libraries.Folder
import com.pryvn.audiophile.data.libraries.MusicLibrary
import com.pryvn.audiophile.data.libraries.PlayList
import com.pryvn.audiophile.data.libraries.YosMediaItem
import com.pryvn.audiophile.data.libraries.YosStringWrapper
import kotlin.system.exitProcess
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
import com.pryvn.audiophile.archivetune.ArchiveTuneAdapter

class YosBasicApplication : Application() {
    override fun onCreate() {
        instance = this

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            runCatching {
                CrashActivity.startActivity(this, e.stackTraceToString())
            }
        }

        // 初始化 MMKV
        MMKV.initialize(this)

        // 初始化 ArchiveTune DataStore 内存缓存 (幂等，仅启动一次)
        PreferenceStore.start(applicationContext)

        // 启动 ArchiveTune 在线播放后端 (PoToken 生成 + visitorData 引导)
        runCatching { BotGuardTokenGenerator.initialize(applicationContext) }

        // 恢复持久化的认证状态 (visitorData, cookie, dataSyncId)
        ArchiveTuneAdapter.restorePersistedAuth()

        val atScope = CoroutineScope(Dispatchers.IO)

        // 预缓存 visitorData (如果未持久化则触发首次网络请求)
        atScope.launch { runCatching { ArchiveTuneAdapter.ensureVisitorData() } }

        // 预热 BotGuard Webview (避免首次播放时冷启动)
        atScope.launch {
            runCatching {
                val visitor = YouTube.currentPlaybackAuthState().visitorData
                if (!visitor.isNullOrBlank()) {
                    BotGuardTokenGenerator.preWarm(visitor)
                }
            }
        }

        val gson =
            GsonBuilder()
            //.registerTypeAdapter(Uri::class.java, UriSerializer())
            //registerTypeAdapter(Uri::class.java, UriDeserializer())
            .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
            .create()

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, Folder::class.java) }
        )

        /*registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, ImmutableList::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, ArrayList::class.java) }
        )*/

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, PlayList::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, IntArray::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, YosMediaItem::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, YosStringWrapper::class.java) }
        )

        // 初始化媒体控制器
        val sessionToken = SessionToken(this, ComponentName(this, YosPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                runCatching {
                    mediaControl = controllerFuture.get()
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val playListData = MusicLibrary.loadPlayList()
                        val playStatusData = MusicLibrary.loadPlayStatus()

                        println("prepare 读取历史")
                        if (playListData.mainMusicList != null) {
                            println("prepare 准备调用")
                            if (playStatusData.music != null) {
                                com.pryvn.audiophile.code.MediaController.prepare(
                                    playStatusData.music,
                                    playListData.playingMusicList!!,
                                    playStatusData.position,
                                    playStatusData.shuffleModeEnabled,
                                    playStatusData.repeatMode,
                                    false
                                )
                            }

                            if (playListData.playingMusicList != null) {
                                playingMusicList.value = playListData.playingMusicList
                            }
                        }
                    } catch (e:Exception) {
                        e.printStackTrace()
                    }
                }
            },
            MoreExecutors.directExecutor()
        )

        super.onCreate()
    }

    companion object {
        lateinit var instance: YosBasicApplication
            private set
    }
}


/*
class ImmutableListTypeAdapter<T> : JsonSerializer<ImmutableList<T>>,
    JsonDeserializer<ImmutableList<T>> {
    override fun serialize(src: ImmutableList<T>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return context?.serialize(src?.toList()) ?: JsonNull.INSTANCE
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ImmutableList<T> {
        val listType = object : TypeToken<List<T>>() {}.type
        val list = context?.deserialize<List<T>>(json, listType)
        return ImmutableList.copyOf(list)
    }
}*/

class UriTypeAdapter : TypeAdapter<Uri>() {
    override fun write(out: JsonWriter, value: Uri?) {
        out.value(value.toString())
    }

    override fun read(`in`: JsonReader): Uri {
        return Uri.parse(`in`.nextString())
    }
}

/*
class UriSerializer : JsonSerializer<Uri> {
    override fun serialize(src: Uri?, typeOfSrc: Type?, context: com.google.gson.JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

class UriDeserializer : JsonDeserializer<Uri> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: com.google.gson.JsonDeserializationContext?): Uri {
        return Uri.parse(json?.asString)
    }
}*/
