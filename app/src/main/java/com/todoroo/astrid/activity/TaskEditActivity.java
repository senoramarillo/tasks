package com.todoroo.astrid.activity;

import com.todoroo.astrid.dao.TaskDaoBlocking;
import com.todoroo.astrid.service.TaskCreator;
import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import javax.inject.Inject;
import org.tasks.injection.InjectingAppCompatActivity;
import org.tasks.intents.TaskIntents;

@AndroidEntryPoint
public class TaskEditActivity extends InjectingAppCompatActivity {

  private static final String TOKEN_ID = "id";

  @Inject TaskCreator taskCreator;
  @Inject TaskDaoBlocking taskDao;
  private CompositeDisposable disposables;

  @Override
  protected void onResume() {
    super.onResume();

    long taskId = getIntent().getLongExtra(TOKEN_ID, 0);

    disposables = new CompositeDisposable();

    if (taskId == 0) {
      startActivity(TaskIntents.getEditTaskIntent(this, taskCreator.createWithValues("")));
      finish();
    } else {
      disposables.add(
          Single.fromCallable(() -> taskDao.fetchBlocking(taskId))
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(
                  task -> {
                    startActivity(TaskIntents.getEditTaskIntent(this, task));
                    finish();
                  }));
    }
  }

  @Override
  protected void onPause() {
    super.onPause();

    disposables.dispose();
  }
}
