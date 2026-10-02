package com.codex.quota.widget
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
class SmallQuotaWidget : PhoneQuotaWidget(true)
class SmallQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallQuotaWidget()
}
