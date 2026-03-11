package io.modelcontextprotocol.sample.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import java.util.Locale

// Extension function to fetch weather alerts for a given state
suspend fun HttpClient.getAlerts(state: String): Result<List<String>> = runCatching {
    val uri = "/alerts/active/area/$state"
    val alerts = this.get(uri).body<AlertsResponse>()

    alerts.features.map { feature ->
        """
            Event: ${feature.properties.event}
            Area: ${feature.properties.areaDesc}
            Severity: ${feature.properties.severity}
            Status: ${feature.properties.status}
            Headline: ${feature.properties.headline}
        """.trimIndent()
    }
}

// Extension function to fetch forecast information for given latitude and longitude
suspend fun HttpClient.getForecast(latitude: Double, longitude: Double): Result<List<String>> = runCatching {
    val lat = String.format(Locale.US, "%.4f", latitude)
    val lon = String.format(Locale.US, "%.4f", longitude)
    val uri = "/points/$lat,$lon"
    val points = this.get(uri).body<PointsResponse>()

    val forecastUrl = points.properties.forecast
        ?: error("No forecast URL available for coordinates: $latitude, $longitude")
    val forecast = this.get(forecastUrl).body<ForecastResponse>()

    forecast.properties.periods.map { period ->
        """
            ${period.name}:
            Temperature: ${period.temperature}°${period.temperatureUnit}
            Wind: ${period.windSpeed} ${period.windDirection}
            ${period.shortForecast}
        """.trimIndent()
    }
}

@Serializable
data class PointsResponse(
    val properties: PointsProperties,
)

@Serializable
data class PointsProperties(
    val forecast: String? = null,
)

@Serializable
data class ForecastResponse(
    val properties: ForecastProperties,
)

@Serializable
data class ForecastProperties(
    val periods: List<ForecastPeriod> = emptyList(),
)

@Serializable
data class ForecastPeriod(
    val name: String? = null,
    val temperature: Int? = null,
    val temperatureUnit: String? = null,
    val windSpeed: String? = null,
    val windDirection: String? = null,
    val shortForecast: String? = null,
)

@Serializable
data class AlertsResponse(
    val features: List<AlertFeature> = emptyList(),
)

@Serializable
data class AlertFeature(
    val properties: AlertProperties,
)

@Serializable
data class AlertProperties(
    val event: String? = null,
    val areaDesc: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val headline: String? = null,
)
