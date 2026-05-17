package com.quokkalabs.strangeplanet.bluetooth

import android.content.Context
import com.quokkalabs.strangeplanet.data.model.BtDeviceInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * One shared, persisted list of recently-linked Bluetooth adversaries,
 * surfaced in every game's lobby ("Previous Adversaries"). Most-recent
 * first, deduped by address, capped at [MAX].
 */
object BtHistory {

    private const val PREFS = "strangeplanet_bt"
    private const val KEY = "recent_adversaries"
    private const val MAX = 6

    fun load(context: Context): List<BtDeviceInfo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BtDeviceInfo(o.getString("name"), o.getString("address"))
            }
        }.getOrDefault(emptyList())
    }

    /** Record a freshly-linked device at the front of the history. */
    fun remember(context: Context, device: BtDeviceInfo): List<BtDeviceInfo> {
        if (device.address.isBlank()) return load(context)
        val updated = (listOf(device) + load(context).filter {
            it.address != device.address
        }).take(MAX)
        val arr = JSONArray()
        for (d in updated) {
            arr.put(JSONObject().apply {
                put("name", d.name)
                put("address", d.address)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        return updated
    }
}
