package com.sla.briefpopup;

import android.app.PendingIntent;
import android.graphics.Rect;

/**
 * Roda no processo shell/root que o Shizuku sobe via app_process. So' la'
 * dentro o hidden-API enforcement nao bloqueia setLaunchWindowingMode.
 */
interface IFreeformService {
    boolean launchFreeform(in PendingIntent intent, in Rect bounds);
}
