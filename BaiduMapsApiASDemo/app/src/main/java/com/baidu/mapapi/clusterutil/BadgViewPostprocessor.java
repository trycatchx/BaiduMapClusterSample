package com.baidu.mapapi.clusterutil;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.Gravity;
import android.view.View;

import com.baidu.mapapi.clusterutil.clustering.Cluster;
import com.baidu.mapapi.clusterutil.clustering.ClusterItem;
import com.baidu.mapapi.clusterutil.ui.IconGenerator;
import com.dmsys.airdisk.R;
import com.facebook.cache.common.CacheKey;
import com.facebook.cache.common.SimpleCacheKey;
import com.facebook.common.references.CloseableReference;
import com.facebook.imagepipeline.bitmaps.PlatformBitmapFactory;
import com.facebook.imagepipeline.request.BasePostprocessor;
import com.makeramen.roundedimageview.RoundedImageView;

import java.lang.ref.WeakReference;

import baidumapsdk.demo.model.LocalPictrue;
import q.rorbin.badgeview.QBadgeView;

import static android.R.attr.radius;

/**
 * Created by jiong103 on 2017/5/9.
 */

public class BadgViewPostprocessor extends BasePostprocessor {


    private WeakReference<Activity> mActivity;
    Cluster<LocalPictrue> cluster;

    public BadgViewPostprocessor(Activity context, Cluster<LocalPictrue> cluster) {
        mActivity = new WeakReference<Activity>(context);
        this.cluster = cluster;
    }


    @Override
    public CloseableReference<Bitmap> process(
            Bitmap sourceBitmap,
            PlatformBitmapFactory bitmapFactory) {

        Activity context = mActivity.get();
        if (context == null) {
            return super.process(sourceBitmap,bitmapFactory);
        }

        long time = System.currentTimeMillis();
        IconGenerator mClusterIconGenerator = new IconGenerator(context.getApplicationContext());
        View multiProfile = context.getLayoutInflater().inflate(R.layout.multi_profile, null, false);
        mClusterIconGenerator.setContentView(multiProfile);
        RoundedImageView mClusterImageView = (RoundedImageView) multiProfile.findViewById(R.id.image);

        if (cluster.getSize() > 1) {
            String numberText = cluster.getSize() > 999 ? "999+" : String.valueOf(cluster.getSize());
            new QBadgeView(context)
                    .bindTarget(mClusterImageView)
                    .setBadgeText(numberText)
                    .setShowShadow(true)
                    .setBadgeTextSize(12, true)
                    .setBadgeTextColor(Color.WHITE)
                    .setBadgeGravity(Gravity.TOP | Gravity.END)
                    .setBadgePadding(8.f, true)
                    .setBadgeBackgroundColor(0xff157EFB);
        }
        mClusterImageView.setImageBitmap(sourceBitmap);
        Bitmap ret = mClusterIconGenerator.makeIcon();

        CloseableReference<Bitmap> bitmapRef = bitmapFactory.createBitmap(
                ret.getWidth() ,
                ret.getHeight());
        try {
            Bitmap destBitmap = bitmapRef.get();

            destBitmap.eraseColor(android.graphics.Color.TRANSPARENT);

            Canvas canvas = new Canvas(destBitmap);
            canvas.drawBitmap(ret, 0, 0, null);
            ret.recycle();
            return CloseableReference.cloneOrNull(bitmapRef);
        } finally {
            CloseableReference.closeSafely(bitmapRef);
        }
    }





    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public CacheKey getPostprocessorCacheKey() {
        return new SimpleCacheKey("cluster.size=" + cluster.getSize());
    }

}
