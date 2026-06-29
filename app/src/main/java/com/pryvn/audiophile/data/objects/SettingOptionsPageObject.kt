package com.pryvn.audiophile.data.objects

import com.pryvn.audiophile.data.SettingOption
import com.pryvn.audiophile.data.libraries.SettingsLibrary

object SettingOptionsPageObject {
    var title:String = ""
    var desc: String? = null
    private var options: Array<SettingOption> = emptyArray()

    fun sendSettingOptionsRequest(title:String, desc: String? = null, options: Array<SettingOption>) {
        this.title = title
        this.desc = desc
        this.options = options
        SettingsLibrary.ScreenCornerSet
    }

    fun getSettingOptionsRequest(): Triple<String, String?, Array<SettingOption>> {
        val result =  Triple(this.title, this.desc, this.options)
        this.title = ""
        this.desc = null
        this.options = emptyArray()
        return result
    }
}