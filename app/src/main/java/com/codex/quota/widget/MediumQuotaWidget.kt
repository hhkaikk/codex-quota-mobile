package com.codex.quota.widget
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
class MediumQuotaWidget : PhoneQuotaWidget(false)
class MediumQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumQuotaWidget()
}
