package com.felix.galleryload;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {
    private ProgressDialog mProgressDialog;

    /**
     * 存储文件夹中的图片数量
     */
    private int mPicsSize;
    /**
     * 图片数量最多的文件夹
     */
    private File mImgDir;

    private GridView mGirdView;
    /**
     * 临时的辅助类，用于防止同一个文件夹的多次扫描
     */
    private HashSet<String> mDirPaths = new HashSet<>();

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            mProgressDialog.dismiss();

            //所有的图片
            List<String> mImgs = Arrays.asList(mImgDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".jpg") || filename.endsWith(".png");
                }
            }));
            //可以看到文件夹的路径和图片的路径分开保存，极大的减少了内存的消耗；
            ListAdapter mAdapter = new MyAdapter(getApplicationContext(), mImgs, mImgDir.getAbsolutePath());
            mGirdView.setAdapter(mAdapter);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGirdView = findViewById(R.id.id_gridView);
        getImages();
    }

    private void getImages() {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "暂无外部存储", Toast.LENGTH_SHORT).show();
            return;
        }
        // 显示进度条
        mProgressDialog = ProgressDialog.show(this, null, "正在加载...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                loadPics();
            }
        }).start();
    }

    /**
     * 利用ContentProvider扫描手机中的图片，此方法在运行在子线程中 完成图片的扫描，最终获得jpg最多的那个文件夹
     */
    private void loadPics() {
        Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        ContentResolver mContentResolver = this.getContentResolver();
        // 只查询jpeg和png的图片
        Cursor mCursor = mContentResolver.query(mImageUri, null,
                MediaStore.Images.Media.MIME_TYPE + "=? or " + MediaStore.Images.Media.MIME_TYPE + "=?",
                new String[]{"image/jpeg", "image/png"}, MediaStore.Images.Media.DATE_MODIFIED);

        if (mCursor != null) {
            while (mCursor.moveToNext()) {
                // 获取图片的路径
                String path = mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.Media.DATA));
                // 获取该图片的父路径名
                File parentFile = new File(path).getParentFile();
                String dirPath = parentFile.getAbsolutePath();

                //利用一个HashSet防止多次扫描同一个文件夹（不加这个判断，图片多起来还是相当恐怖的~~）
                if (mDirPaths.contains(dirPath)) {
                    continue;
                } else {
                    mDirPaths.add(dirPath);
                }

                String[] list = parentFile.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.endsWith(".jpg") || filename.endsWith(".png");
                    }
                });
                int picSize = list.length;
                if (picSize > mPicsSize) {
                    mPicsSize = picSize;
                    mImgDir = parentFile;
                }
            }
            mCursor.close();
        }
        //扫描完成，辅助的HashSet也就可以释放内存了
        mDirPaths = null;
        // 通知Handler扫描图片完成
        mHandler.sendEmptyMessage(0x110);
    }
}
