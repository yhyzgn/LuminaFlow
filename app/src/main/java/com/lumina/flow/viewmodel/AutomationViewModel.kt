package com.lumina.flow.viewmodel

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.flow.data.AutomationDao
import com.lumina.flow.data.AutomationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val dao: AutomationDao
) : ViewModel() {

    val automations =
        dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(entity: AutomationEntity) {
        viewModelScope.launch { dao.insert(entity) }
    }

    fun delete(entity: AutomationEntity) {
        viewModelScope.launch { dao.delete(entity) }
    }

    // 完整 importFromUrl 实现
    fun importFromUrl(url: String) {
        viewModelScope.launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val yamlContent = response.body?.string() ?: return@launch

                val yaml = Yaml()
                val list: List<Map<String, Any>> = yaml.load(yamlContent)

                list.forEach { map ->
                    val entity = AutomationEntity(
                        name = map["name"] as? String ?: "Imported Task",
                        triggerType = map["triggerType"] as? String ?: "TIME",
                        hour = (map["hour"] as? Number)?.toInt(),
                        minute = (map["minute"] as? Number)?.toInt(),
                        daysOfWeek = map["daysOfWeek"] as? String ?: "",
                        latitude = (map["latitude"] as? Number)?.toDouble(),
                        longitude = (map["longitude"] as? Number)?.toDouble(),
                        radius = (map["radius"] as? Number)?.toFloat() ?: 100f,
                        actionsJson = map["actionsJson"] as? String
                            ?: """[{"type":"NOTIFICATION","param":"Imported task"}]""",
                        conditionJson = map["conditionJson"] as? String ?: "{}"
                    )
                    dao.insert(entity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 你可以在 UI 层加 Toast 显示错误
            }
        }
    }

    fun exportToYaml(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val list = automations.value
            if (list.isEmpty()) {
                onComplete(false)
                return@launch
            }

            val yaml = Yaml()
            val exportList = list.map { entity ->
                mapOf(
                    "name" to entity.name,
                    "triggerType" to entity.triggerType,
                    "hour" to entity.hour,
                    "minute" to entity.minute,
                    "daysOfWeek" to entity.daysOfWeek,
                    "latitude" to entity.latitude,
                    "longitude" to entity.longitude,
                    "radius" to entity.radius,
                    "actionsJson" to entity.actionsJson,
                    "conditionJson" to entity.conditionJson
                )
            }

            try {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "lumina_flow.yml")
                FileWriter(file).use { writer ->
                    yaml.dump(exportList, writer)
                }
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
}