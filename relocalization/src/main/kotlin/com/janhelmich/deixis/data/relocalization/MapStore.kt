package com.janhelmich.deixis.data.relocalization

import java.io.File

/** A saved map's identity and size, for listing without loading the whole thing. */
data class MapSummary(
    val id: String,
    val label: String,
    val createdAtMillis: Long,
    val markerCount: Int,
    val keyframeCount: Int,
    val sizeBytes: Long,
)

/** Where offline maps live between runs. */
interface MapStore {
    fun save(map: WorldMap)
    fun load(id: String): WorldMap?
    fun list(): List<MapSummary>
    fun delete(id: String): Boolean
}

/**
 * A [MapStore] backed by one binary file per map under a directory (in practice the app's
 * private files dir). Listing reads only each map's header, not its descriptor payload.
 */
class FileMapStore(private val dir: File) : MapStore {

    init { dir.mkdirs() }

    private fun fileFor(id: String) = File(dir, "${sanitize(id)}.dxmap")

    override fun save(map: WorldMap) {
        val tmp = File(dir, "${sanitize(map.id)}.dxmap.tmp")
        tmp.outputStream().use { WorldMapCodec.write(map, it) }
        // Replace atomically so a crash mid-write cannot leave a half-map behind.
        check(tmp.renameTo(fileFor(map.id))) { "could not commit map ${map.id}" }
    }

    override fun load(id: String): WorldMap? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return file.inputStream().use(WorldMapCodec::read)
    }

    override fun list(): List<MapSummary> =
        dir.listFiles { f -> f.extension == "dxmap" }.orEmpty().mapNotNull { file ->
            runCatching {
                val map = file.inputStream().use(WorldMapCodec::read)
                MapSummary(map.id, map.label, map.createdAtMillis, map.markers.size,
                    map.keyframes.size, file.length())
            }.getOrNull()
        }.sortedByDescending { it.createdAtMillis }

    override fun delete(id: String): Boolean = fileFor(id).delete()

    private fun sanitize(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
