package dk.madslee.imageSequence;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.util.Log;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.RejectedExecutionException;


public class RCTImageSequenceView extends ImageView {
    private Integer framesPerSecond = 24;
    private Boolean loop = true;
    private ArrayList<AsyncTask> activeTasks;
    private HashMap<Integer, Bitmap> bitmaps;
    private RCTResourceDrawableIdHelper resourceDrawableIdHelper;
    private AnimationDrawable mAnimationDrawable;
    private Integer interval = 0;
    private Handler intervalHandler;
    private Runnable intervalRunnable;

    public RCTImageSequenceView(Context context) {
        super(context);

        resourceDrawableIdHelper = new RCTResourceDrawableIdHelper();
    }

    /**
     * 下面两个方法都是当视图被移除时触发，此时将定时任务移除
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (intervalHandler != null && intervalRunnable != null) {
            intervalHandler.removeCallbacks(intervalRunnable);
            intervalRunnable = null;
            intervalHandler = null;
        }
    }
    @Override
    public void onStartTemporaryDetach() {
        super.onStartTemporaryDetach();
        if (intervalHandler != null && intervalRunnable != null) {
            intervalHandler.removeCallbacks(intervalRunnable);
            intervalRunnable = null;
            intervalHandler = null;
        }
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        private final Integer index;
        private final String uri;
        private final Context context;

        public DownloadImageTask(Integer index, String uri, Context context) {
            this.index = index;
            this.uri = uri;
            this.context = context;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            if (this.uri.startsWith("http")) {
                return this.loadBitmapByExternalURL(this.uri);
            }
            if (this.uri.startsWith("file")) {
                return this.loadBitmapByFileURL(this.uri.replace("file:", ""));
            }
            if (this.uri.startsWith("asset")) {
                return this.loadBitmapByAssetUrl(this.uri.replace("asset:", ""));
            }
            return this.loadBitmapByLocalResource(this.uri);
        }
        // 读取drawable文件
        private Bitmap loadBitmapByLocalResource(String uri) {
            return BitmapFactory.decodeResource(this.context.getResources(), resourceDrawableIdHelper.getResourceDrawableId(this.context, uri));
        }
        // 读取远程图片
        private Bitmap loadBitmapByExternalURL(String uri) {
            Bitmap bitmap = null;

            try {
                InputStream in = new URL(uri).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bitmap;
        }
        // 从文件系统读取图片
        private Bitmap loadBitmapByFileURL(String uri) {
            return BitmapFactory.decodeFile(uri);
        }
        // 从assets目录读取图片
        private Bitmap loadBitmapByAssetUrl(String uri) {
            Bitmap bitmap = null;
            AssetManager asm = this.context.getAssets();
            try {
                InputStream in = asm.open(uri);
                bitmap = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (!isCancelled()) {
                onTaskCompleted(this, index, bitmap);
            }
        }
    }

    private void onTaskCompleted(DownloadImageTask downloadImageTask, Integer index, Bitmap bitmap) {
        if (index == 0) {
            // first image should be displayed as soon as possible.
            this.setImageBitmap(bitmap);
        }

        bitmaps.put(index, bitmap);
        activeTasks.remove(downloadImageTask);

        if (activeTasks.isEmpty()) {
            setupAnimationDrawable();
        }
    }

    public void setImages(ArrayList<String> uris) {
        if (isLoading()) {
            // cancel ongoing tasks (if still loading previous images)
            for (int index = 0; index < activeTasks.size(); index++) {
                activeTasks.get(index).cancel(true);
            }
        }

        activeTasks = new ArrayList<>(uris.size());
        bitmaps = new HashMap<>(uris.size());

        for (int index = 0; index < uris.size(); index++) {
            DownloadImageTask task = new DownloadImageTask(index, uris.get(index), getContext());
            activeTasks.add(task);

            try {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } catch (RejectedExecutionException e){
                Log.e("image-sequence", "DownloadImageTask failed" + e.getMessage());
                break;
            }
        }
    }

    public void setFramesPerSecond(Integer framesPerSecond) {
        this.framesPerSecond = framesPerSecond;

        // updating frames per second, results in building a new AnimationDrawable (because we cant alter frame duration)
        if (isLoaded()) {
            setupAnimationDrawable();
        }
    }

    public void setLoop(Boolean loop) {
        this.loop = loop;

        // updating looping, results in building a new AnimationDrawable
        if (isLoaded()) {
            setupAnimationDrawable();
        }
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
        if (isLoaded()) {
            setupAnimationDrawable();
        }
    }

    private boolean isLoaded() {
        return !isLoading() && bitmaps != null && !bitmaps.isEmpty();
    }

    private boolean isLoading() {
        return activeTasks != null && !activeTasks.isEmpty();
    }

    private void setupAnimationDrawable() {
        AnimationDrawable animationDrawable = new AnimationDrawable();
        for (int index = 0; index < bitmaps.size(); index++) {
            BitmapDrawable drawable = new BitmapDrawable(this.getResources(), bitmaps.get(index));
            animationDrawable.addFrame(drawable, 1000 / framesPerSecond);
        }
        // 当没有设置动画间隔的时候，根据 this.loop 的值设置动画是否循环播放
        // 当设置了动画间隔，直接设置动画不循环播放，通过 timertask 模拟循环播放
        if (this.interval > 0) {
            animationDrawable.setOneShot(true);
        } else {
            animationDrawable.setOneShot(!this.loop);
        }
        this.setImageDrawable(animationDrawable);
        this.mAnimationDrawable = animationDrawable;
        playAnimation();
    }

    /**
     * 播放动画，根据 interval 的值是否设定决定是否通过 timertask 循环播放
     */
    private void playAnimation() {
        // 当没有 drawable 对象或该视图不可见时不执行动画播放逻辑
        if (mAnimationDrawable == null) {
            return;
        }
        if (interval > 0) {
            // 将 runnable 赋值到私有变量，当视图被移除时取消定时任务
            intervalHandler = new Handler();
            intervalRunnable = new Runnable() {
                @Override
                public void run() {
                    playAnimation();
                }
            };
            intervalHandler.postDelayed(intervalRunnable, interval * 1000);
        }
        mAnimationDrawable.stop();
        mAnimationDrawable.start();
    }
}
