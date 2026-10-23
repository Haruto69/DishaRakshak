package com.example.drdisplay.navigation.animateMap

import android.content.Context
import android.net.Uri
import com.example.drdisplay.parseOsmNames
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import android.util.Log

data class POI(
    val name: String,
    val lat: Double,
    val lon: Double
)

object PoiLoader {

    fun loadPOIs(
        context: Context,
        uri: Uri?
    ): List<POI> {

        val names =
            parseOsmNames(
                context,
                uri
            )

        return names.mapNotNull { entry ->

            val regex =
                Regex(
                    "(.*) \\((-?[0-9.]+),(-?[0-9.]+)\\)"
                )

            val match =
                regex.matchEntire(entry)
                    ?: return@mapNotNull null

            POI(
                name =
                    match.groupValues[1],

                lat =
                    match.groupValues[2]
                        .toDouble(),

                lon =
                    match.groupValues[3]
                        .toDouble()
            )
        }
    }
}
