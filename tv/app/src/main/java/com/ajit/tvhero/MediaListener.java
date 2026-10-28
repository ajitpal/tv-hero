package com.ajit.tvhero;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the guard may read media sessions (the title of what's playing) with
 * MediaSessionManager.getActiveSessions. It ignores notifications completely. Access is granted
 * once over ADB by tools/install-tv.sh (cmd notification allow_listener).
 */
public class MediaListener extends NotificationListenerService {
}
