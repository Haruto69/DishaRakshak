package com.example.drdisplay.navigation.animateMap

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Offline styling for the geometry present in an OSM extract. */
class OsmMapOverlay(graph: RoadGraph) : Overlay() {
    private data class Feature(val points: List<GeoPoint>, val tags: Map<String, String>, val closed: Boolean)
    private val bounds = graph.bounds
    private val features = graph.ways.flatMap { way ->
        val closed = way.nodes.size >= 4 && way.nodes.first() == way.nodes.last()
        val runs = mutableListOf<List<GeoPoint>>()
        var run = mutableListOf<GeoPoint>()
        way.nodes.forEach { id ->
            val node = graph.nodes[id]
            if (node == null) {
                if (run.size >= 2) runs.add(run)
                run = mutableListOf()
            } else run.add(GeoPoint(node.lat, node.lon))
        }
        if (run.size >= 2) runs.add(run)
        runs.map { Feature(it, way.tags, closed && it.size == way.nodes.size) }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val point = Point()

    private fun areaColor(tags: Map<String, String>): Int? {
        val land = tags["landuse"]
        val natural = tags["natural"]
        val leisure = tags["leisure"]
        return when {
            natural == "water" || tags["waterway"] == "riverbank" -> Color.rgb(170, 211, 223)
            leisure in setOf("park", "garden", "recreation_ground", "golf_course") -> Color.rgb(185, 221, 171)
            natural in setOf("wood", "grassland", "scrub") || land in setOf("forest", "grass", "meadow", "orchard") -> Color.rgb(197, 225, 183)
            land == "farmland" -> Color.rgb(232, 225, 192)
            land in setOf("industrial", "commercial", "retail") -> Color.rgb(231, 217, 225)
            land == "residential" -> Color.rgb(232, 229, 223)
            tags["amenity"] in setOf("school", "college", "university", "hospital") -> Color.rgb(246, 240, 211)
            tags.containsKey("building") && tags["building"] != "no" -> Color.rgb(214, 204, 194)
            else -> null
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        canvas.drawColor(Color.rgb(245, 245, 245))
        val zoom = mapView.zoomLevelDouble
        val projection = mapView.projection
        val density = mapView.resources.displayMetrics.density
        val saved = canvas.save()
        bounds?.let { b ->
            val topLeft = projection.toPixels(GeoPoint(b.north, b.west), Point())
            val bottomRight = projection.toPixels(GeoPoint(b.south, b.east), Point())
            canvas.clipRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        }
        fun pathFor(feature: Feature): Path {
            val path = Path()
            feature.points.forEachIndexed { index, geo ->
                projection.toPixels(geo, point)
                if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                else path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            if (feature.closed) path.close()
            return path
        }
        val areas = features.filter { it.closed && areaColor(it.tags) != null }
        // Land first, buildings after, then roads.
        for (building in listOf(false, true)) {
            areas.filter { it.tags.containsKey("building") == building }.forEach { feature ->
                val path = pathFor(feature)
                paint.style = Paint.Style.FILL
                paint.color = areaColor(feature.tags)!!
                canvas.drawPath(path, paint)
                if (building && zoom >= 16) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = density * 0.7f
                    paint.color = Color.rgb(183, 171, 160)
                    canvas.drawPath(path, paint)
                }
            }
        }
        val roads = features.filter { it.tags.containsKey("highway") }
        fun roadWidth(type: String): Float {
            val base = when (type) {
                "motorway", "trunk" -> 8f
                "primary" -> 7f
                "secondary", "tertiary" -> 6f
                "residential", "unclassified" -> 4.5f
                "service" -> 3f
                else -> 2f
            }
            return base * density * Math.pow(2.0, (zoom - 17.0).coerceIn(-3.0, 3.0)).toFloat()
        }
        for (casing in listOf(true, false)) {
            roads.forEach { road ->
                val type = road.tags["highway"] ?: return@forEach
                val width = roadWidth(type)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = width + if (casing) density * 1.5f else 0f
                paint.color = if (casing) Color.rgb(192, 185, 177) else when (type) {
                    "motorway", "trunk" -> Color.rgb(233, 143, 156)
                    "primary", "secondary" -> Color.rgb(249, 222, 158)
                    "footway", "path", "cycleway" -> Color.rgb(212, 176, 145)
                    else -> Color.WHITE
                }
                canvas.drawPath(pathFor(road), paint)
            }
        }
        if (zoom >= 16) {
            val occupied = mutableListOf<RectF>()
            paint.textSize = 11f * density
            paint.textAlign = Paint.Align.CENTER
            val named = roads.filter { !it.tags["name"].isNullOrBlank() } +
                areas.filter { !it.tags["name"].isNullOrBlank() && !it.tags.containsKey("building") }
            named.forEach { feature ->
                val name = feature.tags["name"] ?: return@forEach
                projection.toPixels(feature.points[feature.points.size / 2], point)
                if (point.x !in 0..canvas.width || point.y !in 0..canvas.height) return@forEach
                val width = paint.measureText(name)
                val rect = RectF(point.x - width / 2 - 4 * density, point.y - 14 * density,
                    point.x + width / 2 + 4 * density, point.y + 4 * density)
                if (occupied.any { RectF.intersects(it, rect) }) return@forEach
                occupied.add(rect)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * density
                paint.color = Color.WHITE
                canvas.drawText(name, point.x.toFloat(), point.y.toFloat(), paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(65, 67, 64)
                canvas.drawText(name, point.x.toFloat(), point.y.toFloat(), paint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
