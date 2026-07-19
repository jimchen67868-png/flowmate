package com.example.automateclone.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Simple on-disk persistence for flows. Not a database — flows are small
 * graphs, so plain JSON in app-private storage is enough for an MVP.
 */
class FlowRepository(private val context: Context) {
    private val gson = Gson()
    private val storeFile: File
        get() = File(context.filesDir, "flows.json")

    fun loadAll(): MutableList<AutomationFlow> {
        if (!storeFile.exists()) return mutableListOf()
        val json = storeFile.readText()
        if (json.isBlank()) return mutableListOf()
        val type = object : TypeToken<MutableList<AutomationFlow>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun saveAll(flows: List<AutomationFlow>) {
        storeFile.writeText(gson.toJson(flows))
    }

    fun upsert(flow: AutomationFlow) {
        val flows = loadAll()
        val idx = flows.indexOfFirst { it.id == flow.id }
        if (idx >= 0) flows[idx] = flow else flows.add(flow)
        saveAll(flows)
    }

    fun delete(flowId: String) {
        val flows = loadAll()
        flows.removeAll { it.id == flowId }
        saveAll(flows)
    }
}
