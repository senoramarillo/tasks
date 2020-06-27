package org.tasks.dashclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.todoroo.astrid.activity.MainActivity;
import com.todoroo.astrid.api.Filter;
import com.todoroo.astrid.dao.TaskDaoBlocking;
import com.todoroo.astrid.data.Task;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.List;
import javax.inject.Inject;
import org.tasks.LocalBroadcastManager;
import org.tasks.R;
import org.tasks.preferences.DefaultFilterProvider;
import org.tasks.preferences.Preferences;
import timber.log.Timber;

@AndroidEntryPoint
public class DashClockExtension extends com.google.android.apps.dashclock.api.DashClockExtension {

  @Inject DefaultFilterProvider defaultFilterProvider;
  @Inject TaskDaoBlocking taskDao;
  @Inject Preferences preferences;
  @Inject LocalBroadcastManager localBroadcastManager;

  private final BroadcastReceiver refreshReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          refresh();
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();

    localBroadcastManager.registerRefreshReceiver(refreshReceiver);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    localBroadcastManager.unregisterReceiver(refreshReceiver);
  }

  @Override
  protected void onUpdateData(int i) {
    refresh();
  }

  private void refresh() {
    final String filterPreference = preferences.getStringValue(R.string.p_dashclock_filter);
    Filter filter = defaultFilterProvider.getFilterFromPreference(filterPreference);

    int count = taskDao.count(filter);

    if (count == 0) {
      publish(null);
    } else {
      Intent clickIntent = new Intent(this, MainActivity.class);
      clickIntent.putExtra(MainActivity.OPEN_FILTER, filter);
      ExtensionData extensionData =
          new ExtensionData()
              .visible(true)
              .icon(R.drawable.ic_check_white_24dp)
              .status(Integer.toString(count))
              .expandedTitle(getResources().getQuantityString(R.plurals.task_count, count, count))
              .expandedBody(filter.listingTitle)
              .clickIntent(clickIntent);
      if (count == 1) {
        List<Task> tasks = taskDao.fetchFiltered(filter);
        if (!tasks.isEmpty()) {
          extensionData.expandedTitle(tasks.get(0).getTitle());
        }
      }
      publish(extensionData);
    }
  }

  private void publish(ExtensionData data) {
    try {
      publishUpdate(data);
    } catch (Exception e) {
      Timber.e(e);
    }
  }
}
