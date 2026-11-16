package com.meuapp.btcwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class BitcoinWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_MANUAL_REFRESH = "com.meuapp.btcwidget.ACTION_MANUAL_REFRESH"
        const val ACTION_AUTO_UPDATE = "com.meuapp.btcwidget.ACTION_AUTO_UPDATE"
        private const val AUTO_UPDATE_REQUEST_CODE = 1001
        private const val MANUAL_REFRESH_REQUEST_CODE = 1002
        private const val UPDATE_INTERVAL_MS = 10L * 60L * 1000L
        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelScheduledUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MANUAL_REFRESH,
            ACTION_AUTO_UPDATE,
            Intent.ACTION_BOOT_COMPLETED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, BitcoinWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    scheduleNextUpdate(context)
                    val showLoading = intent.action == ACTION_MANUAL_REFRESH
                    updateWidgetsAsync(context, manager, ids, goAsync(), showLoading)
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isNotEmpty()) {
            scheduleNextUpdate(context)
            updateWidgetsAsync(context, appWidgetManager, appWidgetIds, goAsync(), true)
        }
    }

    private fun getAutoUpdatePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BitcoinWidgetProvider::class.java).apply {
            action = ACTION_AUTO_UPDATE
        }
        return PendingIntent.getBroadcast(
            context,
            AUTO_UPDATE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleNextUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
            getAutoUpdatePendingIntent(context)
        )
    }

    private fun cancelScheduledUpdate(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(getAutoUpdatePendingIntent(context))
    }

    private fun updateWidgetsAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        pendingResult: PendingResult,
        showLoading: Boolean
    ) {
        if (showLoading) {
            for (id in appWidgetIds) {
                val v = RemoteViews(context.packageName, R.layout.widget_bitcoin)
                v.setTextViewText(R.id.txt_updated, "Atualizando...")
                attachRefreshClick(context, v)
                appWidgetManager.partiallyUpdateAppWidget(id, v)
            }
        }

        executor.execute {
            try {
                val data = fetchBitcoinPrice()
                val views = RemoteViews(context.packageName, R.layout.widget_bitcoin)
                attachRefreshClick(context, views)

                if (data != null) {
                    val brLocale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
                    val fmt = NumberFormat.getNumberInstance(brLocale).apply {
                        maximumFractionDigits = 0
                        minimumFractionDigits = 0
                    }

                    views.setTextViewText(R.id.txt_price_usd,
                        "US$ " + fmt.format(data.usd))
                    views.setTextViewText(R.id.txt_price_brl,
                        "R$ " + fmt.format(data.brl))

                    if (data.change24h != null && !data.change24h.isNaN()) {
                        val txt = String.format(brLocale, "%+.2f%% (24h)", data.change24h)
                        views.setTextViewText(R.id.txt_change, txt)
                        val color = if (data.change24h >= 0)
                            Color.parseColor("#22C55E")
                        else
                            Color.parseColor("#EF4444")
                        views.setTextColor(R.id.txt_change, color)
                    } else {
                        views.setTextViewText(R.id.txt_change, "Ao vivo")
                        views.setTextColor(R.id.txt_change,
                            Color.parseColor("#94A3B8"))
                    }

                    val time = SimpleDateFormat("HH:mm",
                        Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.txt_updated,
                        data.source + " • " + time + " • a cada 10 min")

                    for (id in appWidgetIds) {
                        appWidgetManager.updateAppWidget(id, views)
                    }
                } else {
                    views.setTextViewText(R.id.txt_updated,
                        "Sem conexao • Toque em ↻ para tentar")
                    for (id in appWidgetIds) {
                        appWidgetManager.partiallyUpdateAppWidget(id, views)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun attachRefreshClick(context: Context, views: RemoteViews) {
        val intent = Intent(context, BitcoinWidgetProvider::class.java).apply {
            action = ACTION_MANUAL_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            context,
            MANUAL_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, pi)
    }

    private data class BtcPrice(
        val usd: Double,
        val brl: Double,
        val change24h: Double?,
        val source: String
    )

    private fun fetchBitcoinPrice(): BtcPrice? {
        return fetchFromBinance() ?: fetchFromCoinbase() ?: fetchFromCoinGecko()
    }

    private fun fetchFromBinance(): BtcPrice? {
        return try {
            val url = "https://data-api.binance.vision/api/v3/ticker/24hr" +
                "?symbols=%5B%22BTCUSDT%22,%22BTCBRL%22%5D"
            val body = httpGet(url) ?: return null
            val arr = JSONArray(body)
            var usd: Double? = null
            var brl: Double? = null
            var change: Double? = null
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                when (item.optString("symbol")) {
                    "BTCUSDT" -> {
                        usd = item.optString("lastPrice").toDoubleOrNull()
                        change = item.optString("priceChangePercent")
                            .toDoubleOrNull()
                    }
                    "BTCBRL" -> {
                        brl = item.optString("lastPrice").toDoubleOrNull()
                    }
                }
            }
            if (usd != null && brl != null)
                BtcPrice(usd, brl, change, "Binance")
            else null
        } catch (_: Exception) { null }
    }

    private fun fetchFromCoinbase(): BtcPrice? {
        return try {
            val usdBody = httpGet(
                "https://api.coinbase.com/v2/prices/BTC-USD/spot"
            ) ?: return null
            val brlBody = httpGet(
                "https://api.coinbase.com/v2/prices/BTC-BRL/spot"
            ) ?: return null
            val usd = JSONObject(usdBody)
                .getJSONObject("data").getString("amount").toDouble()
            val brl = JSONObject(brlBody)
                .getJSONObject("data").getString("amount").toDouble()
            BtcPrice(usd, brl, null, "Coinbase")
        } catch (_: Exception) { null }
    }

    private fun fetchFromCoinGecko(): BtcPrice? {
        return try {
            val url = "https://api.coingecko.com/api/v3/simple/price" +
                "?ids=bitcoin&vs_currencies=usd,brl&include_24hr_change=true"
            val body = httpGet(url) ?: return null
            val btc = JSONObject(body).getJSONObject("bitcoin")
            val usd = btc.getDouble("usd")
            val brl = btc.getDouble("brl")
            val change = if (btc.has("usd_24h_change"))
                btc.optDouble("usd_24h_change")
            else null
            BtcPrice(usd, brl, change, "CoinGecko")
        } catch (_: Exception) { null }
    }

    private fun httpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    connectTimeout = 2500
                    readTimeout = 2500
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "BtcWidget/1.1")
                }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
