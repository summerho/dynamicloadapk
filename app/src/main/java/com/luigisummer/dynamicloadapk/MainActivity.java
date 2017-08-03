/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.luigisummer.dynamicloadapk;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import dalvik.system.DexClassLoader;

public class MainActivity extends AppCompatActivity {

    private String apkDir = Environment.getExternalStorageDirectory().getPath()+ File.separator + "plugin";
    private List<HashMap<String,String>> datas;
    private ListView mListView;
    private final List<String> pluginApkName = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //第一步把插件apk拷贝至sd卡的plugin目录下
        copyApkFile("apkthemeplugin-1.apk");
        copyApkFile("apkthemeplugin-2.apk");
        copyApkFile("apkthemeplugin-3.apk");
        Toast.makeText(this, "Copy complete", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.change_skin) {
            //第二步 查找并得到sd卡plugin目录下所有的apk信息
            datas = searchAllPlugin(apkDir);
            //第三步 显示查找后可用的apk插件
            showCanEnabledPlugin(datas);
            //第四步 处理点击事件，并设置相应的皮肤
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    HashMap<String,String> map = datas.get(position);
                    if (map != null) {
                        String pkgName = map.get("pkgName");
                        String apkName = pluginApkName.get(position);
                        try {
                            dynamicLoadApk(apkDir, apkName, pkgName);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        return super.onOptionsItemSelected(item);
    }

    private void dynamicLoadApk(String apkDir, String apkName, String pkgName) throws Exception {
        File optimizedDirectoryFile = getDir("dex", Context.MODE_PRIVATE);
        Log.v("hexia", optimizedDirectoryFile.getPath());
        DexClassLoader dexClassLoader = new DexClassLoader(apkDir + File.separator + apkName, optimizedDirectoryFile
                .getPath(), null, ClassLoader.getSystemClassLoader());
        Class<?> clazz = dexClassLoader.loadClass(pkgName + ".R$mipmap");
        Field field = clazz.getDeclaredField("one");
        int resId = field.getInt(R.id.class);
        Resources mResources = getPluginResource(apkName);
        if (mResources != null) {
            findViewById(R.id.background).setBackgroundDrawable(mResources.getDrawable(resId));
        }
    }

    private Resources getPluginResource(String apkName) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, apkDir + File.separator + apkName);
            Resources superRes = this.getResources();
            Resources mResources = new Resources(assetManager, superRes.getDisplayMetrics(), superRes
                    .getConfiguration());
            return mResources;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void showCanEnabledPlugin(List<HashMap<String, String>> datas) {
        if (datas == null || datas.isEmpty()) {
            Toast.makeText(this, "请先下载插件！", Toast.LENGTH_SHORT).show();
        }
        View view = LayoutInflater.from(this).inflate(R.layout.layout_item, null, false);
        mListView = (ListView) view.findViewById(R.id.listview);
        mListView.setAdapter(new SimpleAdapter(this, datas, android.R.layout.simple_list_item_1, new
                String[]{"label"}, new int[]{android.R.id.text1}));
        PopupWindow popupWindow = new PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams
                .WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new BitmapDrawable());
        popupWindow.showAtLocation(view, Gravity.TOP|Gravity.RIGHT, 0, 0);
    }

    /**
     * 查找并得到sd卡plugin目录下所有的apk信息
     * @param apkDir sd卡plugin目录
     * @return
     */
    private List<HashMap<String,String>> searchAllPlugin(String apkDir) {
        List<HashMap<String,String>> lists = new ArrayList<>();
        File dir = new File(apkDir);
        if (dir.isDirectory()) {
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String fileName) {
                    return fileName.endsWith(".apk");
                }
            };
            //过滤掉其它文件，只保留apk结尾的
            File[] apks = dir.listFiles(filter);
            for (int i = 0; i < apks.length; i++) {
                File temp = apks[i];
                pluginApkName.add(temp.getName());//存储apk名字
                String[] info = getUninstallApkInfo(this, apkDir + File.separator + temp.getName());
                HashMap<String,String> map = new HashMap<>();
                map.put("label",info[0]);
                map.put("pkgName",info[1]);
                lists.add(map);
                map = null;
            }
        }
        return lists;
    }

    /**
     * 获取未安装apk的信息，appName和pkgName
     * @param context
     * @param apkFilePath 插件apk的path
     * @return
     */
    private String[] getUninstallApkInfo(Context context, String apkFilePath) {
        String[] info = new String[2];
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(apkFilePath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            String versionName = pkgInfo.versionName;
            Drawable icon = pm.getApplicationIcon(appInfo);
            String appName = pm.getApplicationLabel(appInfo).toString();
            String pkgName = appInfo.packageName;
            info[0] = appName;
            info[1] = pkgName;
        }
        return info;
    }

    /**
     * 拷贝assets下的插件apk文件至sd卡的plugin目录下
     * @param apkName
     */
    private void copyApkFile(String apkName) {
        File file = new File(apkDir);
        if (!file.exists()) {
            file.mkdir();
        }
        File apk = new File(apkDir + File.separator + apkName);
        if (apk.exists()) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(apk);
            InputStream is = getResources().getAssets().open(apkName);
            BufferedInputStream bis = new BufferedInputStream(is);
            int len = -1;
            byte[] by = new byte[1024];
            while ((len = bis.read(by)) != -1) {
                fos.write(by, 0, len);
                fos.flush();
            }
            fos.close();
            is.close();
            bis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
