package nl.sense_os.app.appwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import nl.sense_os.app.R;

public class SenseWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "SenseWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "Update widget");
        context.startService(new Intent(context.getString(R.string.action_widget_update)));
    }
}
