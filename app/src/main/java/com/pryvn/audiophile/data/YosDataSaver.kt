package com.pryvn.audiophile.data

import androidx.compose.runtime.Stable
import com.funny.data_saver.core.DataSaverInterface
import com.tencent.mmkv.MMKV

// Refer to https://github.com/FunnySaltyFish/ComposeDataSaver

@Stable
enum class DataType {
    NORMAL, SONG_LIST, SETTINGS, PLAY_LIST
}

val NormalSaver = YosDataSaver(DataType.NORMAL)
// General data storage, e.g. hidden folder list
val SongListSaver = YosDataSaver(DataType.SONG_LIST)
// Songs, hidden songs, all folders
val SettingsSaver = YosDataSaver(DataType.SETTINGS)
val PlayListSaver = YosDataSaver(DataType.PLAY_LIST)

@Stable
class YosDataSaver(dataType: DataType = DataType.NORMAL) : DataSaverInterface() {
    private val mmkv = MMKV.mmkvWithID(getKey(dataType))

    private fun notifyExternalDataChanged(key: String, value: Any?) {
        if (senseExternalDataChange) externalDataChangedFlow?.tryEmit(key to value)
    }

    private fun getKey(dataType: DataType): String {
        return when (dataType) {
            DataType.NORMAL -> "yos_data_normal"
            DataType.SONG_LIST -> "yos_data_song_list"
            DataType.SETTINGS -> "yos_data_settings"
            DataType.PLAY_LIST -> "yos_data_play_list"
        }
    }
    override fun <T> saveData(key: String, data: T) {
        if (data == null) {
            remove(key)
            notifyExternalDataChanged(key, null)
            return
        }
        with(mmkv) {
            when (data) {
                is Long -> encode(key, data)
                is Int -> encode(key, data)
                is String -> encode(key, data)
                is Boolean -> encode(key, data)
                is Float -> encode(key, data)
                is Double -> encode(key, data)
                else -> throw IllegalArgumentException("YosDataSaver: Cannot save $data (${data!!::class.java})")
            }
            notifyExternalDataChanged(key, data)
        }
    }

    override fun <T> readData(key: String, default: T): T = with(mmkv) {
        val res: Any = when (default) {
            is Long -> decodeLong(key, default)
            is Int -> decodeInt(key, default)
            is String -> decodeString(key, default)!!
            is Boolean -> decodeBool(key, default)
            is Float -> decodeFloat(key, default)
            is Double -> decodeDouble(key, default)
            else -> throw IllegalArgumentException("YosDataSaver: Cannot read $default (${default!!::class.java})")
        }
        return@with res as T
    }

    override fun remove(key: String) {
        mmkv.removeValueForKey(key)
        notifyExternalDataChanged(key, null)
    }

    override fun contains(key: String) = mmkv.contains(key)
}
