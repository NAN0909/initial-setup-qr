package jp.initialsetup.helper;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;

/** OS セットアップ完了を確認し、条件がそろえば切り替え画面を開く。非永続・最長 30 分で自然に止まる。 */
public class CompletionJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Handler(getMainLooper()).post(new Runnable() {
            @Override public void run() {
                SetupApplier.launchPersonalSwitchIfReady(CompletionJob.this);
                jobFinished(params, false);
                SetupApplier.scheduleCompletionCheck(CompletionJob.this);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return false; }
}
