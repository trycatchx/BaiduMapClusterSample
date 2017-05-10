/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.mapapi.clusterutil.clustering;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

import com.baidu.mapapi.clusterutil.MarkerManager;
import com.baidu.mapapi.clusterutil.clustering.algo.Algorithm;
import com.baidu.mapapi.clusterutil.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.baidu.mapapi.clusterutil.clustering.algo.PreCachingAlgorithmDecorator;
import com.baidu.mapapi.clusterutil.clustering.view.ClusterRenderer;
import com.baidu.mapapi.clusterutil.clustering.view.DefaultClusterRenderer;
import com.baidu.mapapi.clusterutil.projection.Point;
import com.baidu.mapapi.clusterutil.projection.SphericalMercatorProjection;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.dmsys.airdisk.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import baidumapsdk.demo.map.MarkerClusterDemo;
import baidumapsdk.demo.model.LocalPictrue;
import baidumapsdk.demo.util.DensityUtil;
import baidumapsdk.demo.util.SdCardFileTool;
import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static com.baidu.mapapi.clusterutil.clustering.algo.NonHierarchicalDistanceBasedAlgorithm.PROJECTION;

/**
 * Groups many items on a map based on zoom level.
 * <p/>
 * ClusterManager should be added to the map
 * <li>
 */
public class ClusterManager<T extends ClusterItem> implements
        BaiduMap.OnMapStatusChangeListener, BaiduMap.OnMarkerClickListener {
    public static double MAX_DISTANCE_AT_ZOOM;

    private final MarkerManager mMarkerManager;
    private final MarkerManager.Collection mMarkers;
    private final MarkerManager.Collection mClusterMarkers;

    private Algorithm<T> mAlgorithm;
    private final ReadWriteLock mAlgorithmLock = new ReentrantReadWriteLock();
    private ClusterRenderer<T> mRenderer;

    private BaiduMap mMap;
    private volatile MapStatus mPreviousCameraPosition;
    private volatile LatLngBounds mPreviousLatLngBounds;
    private ClusterTask mClusterTask;
    private final ReadWriteLock mClusterTaskLock = new ReentrantReadWriteLock();

    private OnClusterItemClickListener<T> mOnClusterItemClickListener;
    private OnClusterInfoWindowClickListener<T> mOnClusterInfoWindowClickListener;
    private OnClusterItemInfoWindowClickListener<T> mOnClusterItemInfoWindowClickListener;
    private OnClusterClickListener<T> mOnClusterClickListener;

    private OnMapStatusChangeFinish onMapStatusChangeFinishListener;

    public ClusterManager(Context context, BaiduMap map) {
        this(context, map, new MarkerManager(map));
    }

    public ClusterManager(Context context, BaiduMap map, MarkerManager markerManager) {
        mMap = map;
        mMarkerManager = markerManager;
        mClusterMarkers = markerManager.newCollection();
        mMarkers = markerManager.newCollection();
        mRenderer = new DefaultClusterRenderer<T>(context, map, this);
        mAlgorithm = new PreCachingAlgorithmDecorator<T>(new NonHierarchicalDistanceBasedAlgorithm<T>());
        mClusterTask = new ClusterTask(null);
        mRenderer.onAdd();
        MAX_DISTANCE_AT_ZOOM = (DensityUtil.dip2px(context, 88)) * Math.sqrt(2) * 2;
    }

    public MarkerManager.Collection getMarkerCollection() {
        return mMarkers;
    }

    public MarkerManager.Collection getClusterMarkerCollection() {
        return mClusterMarkers;
    }

    public MarkerManager getMarkerManager() {
        return mMarkerManager;
    }

    public void setRenderer(ClusterRenderer<T> view) {
        mRenderer.setOnClusterClickListener(null);
        mRenderer.setOnClusterItemClickListener(null);
        mClusterMarkers.clear();
        mMarkers.clear();
        mRenderer.onRemove();
        mRenderer = view;
        mRenderer.onAdd();
        mRenderer.setOnClusterClickListener(mOnClusterClickListener);
        mRenderer.setOnClusterInfoWindowClickListener(mOnClusterInfoWindowClickListener);
        mRenderer.setOnClusterItemClickListener(mOnClusterItemClickListener);
        mRenderer.setOnClusterItemInfoWindowClickListener(mOnClusterItemInfoWindowClickListener);
    }


//

    public void clearItems() {
        mAlgorithmLock.writeLock().lock();
        try {
            mAlgorithm.clearItems();
        } finally {
            mAlgorithmLock.writeLock().unlock();
        }
    }

    public void addItems(Collection<T> items) {
        mAlgorithmLock.writeLock().lock();
        try {
            mAlgorithm.addItems(items);
        } finally {
            mAlgorithmLock.writeLock().unlock();
        }

    }

    public void addItem(T myItem) {
        mAlgorithmLock.writeLock().lock();
        try {
            mAlgorithm.addItem(myItem);
        } finally {
            mAlgorithmLock.writeLock().unlock();
        }
    }

    public void removeItem(T item) {
        mAlgorithmLock.writeLock().lock();
        try {
            mAlgorithm.removeItem(item);
        } finally {
            mAlgorithmLock.writeLock().unlock();
        }
    }

    /**
     * Force a re-cluster. You may want to call this after adding new item(s).
     */
    public void cluster(float zoom, final LatLngBounds visibleBounds) {
        mClusterTaskLock.writeLock().lock();
        try {
            // Attempt to cancel the in-flight request.
            mClusterTask.cancel(true);
            mClusterTask = new ClusterTask(visibleBounds);

            if (zoom <= 0 || zoom > 23) {
                zoom = mMap.getMapStatus().zoom;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                mClusterTask.execute(zoom);
            } else {
                mClusterTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, zoom);
            }
        } finally {
            mClusterTaskLock.writeLock().unlock();
        }
    }


    @Override
    public void onMapStatusChangeStart(MapStatus mapStatus) {

    }

    @Override
    public void onMapStatusChange(MapStatus mapStatus) {

    }

    @Override
    public void onMapStatusChangeFinish(MapStatus mapStatus) {
        if (mRenderer instanceof BaiduMap.OnMapStatusChangeListener) {
            ((BaiduMap.OnMapStatusChangeListener) mRenderer).onMapStatusChange(mapStatus);
        }

        //
        if (mPreviousCameraPosition != null
                && Math.abs((int) mPreviousCameraPosition.zoom - (int) mapStatus.zoom) < 1
                && mPreviousCameraPosition.target.latitude == mapStatus.target.latitude
                && mPreviousCameraPosition.target.longitude == mapStatus.target.longitude) {
            return;
        }
        //屏幕的范围没有变动
        if (mPreviousLatLngBounds != null &&
                mPreviousLatLngBounds.contains(mapStatus.bound.northeast)
                &&   mPreviousLatLngBounds.contains(mapStatus.bound.southwest)) {
            mPreviousCameraPosition = mapStatus;

            cluster(mapStatus.zoom,mapStatus.bound);
            return;
        }

        mPreviousCameraPosition = mapStatus;
        mPreviousLatLngBounds = mapStatus.bound;

        if (onMapStatusChangeFinishListener != null) {
            onMapStatusChangeFinishListener.onMapStatusChangeFinish(mapStatus);
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return getMarkerManager().onMarkerClick(marker);
    }

    /**
     * Runs the clustering algorithm in a background thread, then re-paints when results come back.
     */
    private class ClusterTask extends AsyncTask<Float, Void, Set<Cluster<T>>> {
        private float curZoom = 0.f;
        private LatLngBounds visibleBounds = null;

        public ClusterTask(LatLngBounds visibleBounds) {
            this.visibleBounds = visibleBounds;
        }

        @Override
        protected Set<Cluster<T>> doInBackground(Float... zoom) {
            mAlgorithmLock.readLock().lock();
            curZoom = zoom[0];
            try {
                return mAlgorithm.getClusters(zoom[0],
                        visibleBounds != null ? visibleBounds : mMap.getMapStatus().bound);
            } finally {
                mAlgorithmLock.readLock().unlock();
            }
        }

        @Override
        protected void onPostExecute(Set<Cluster<T>> clusters) {
            if (!isCancelled()) {
                mRenderer.onClustersChanged(clusters, curZoom);
            }

        }
    }

    /**
     * Sets a callback that's invoked when a Cluster is tapped. Note: For this listener to function,
     * the ClusterManager must be added as a click listener to the map.
     */
    public void setOnClusterClickListener(OnClusterClickListener<T> listener) {
        mOnClusterClickListener = listener;
        mRenderer.setOnClusterClickListener(listener);
    }

    /**
     * Sets a callback that's invoked when a Cluster is tapped. Note: For this listener to function,
     * the ClusterManager must be added as a info window click listener to the map.
     */
    public void setOnClusterInfoWindowClickListener(OnClusterInfoWindowClickListener<T> listener) {
        mOnClusterInfoWindowClickListener = listener;
        mRenderer.setOnClusterInfoWindowClickListener(listener);
    }

    /**
     * Sets a callback that's invoked when an individual ClusterItem is tapped. Note: For this
     * listener to function, the ClusterManager must be added as a click listener to the map.
     */
    public void setOnClusterItemClickListener(OnClusterItemClickListener<T> listener) {
        mOnClusterItemClickListener = listener;
        mRenderer.setOnClusterItemClickListener(listener);
    }

    /**
     * Sets a callback that's invoked when an individual ClusterItem's Info Window is tapped. Note: For this
     * listener to function, the ClusterManager must be added as a info window click listener to the map.
     */
    public void setOnClusterItemInfoWindowClickListener(OnClusterItemInfoWindowClickListener<T> listener) {
        mOnClusterItemInfoWindowClickListener = listener;
        mRenderer.setOnClusterItemInfoWindowClickListener(listener);
    }

    public void setOnMapStatusChangeFinishListener(OnMapStatusChangeFinish onMapStatusChangeFinishListener) {
        this.onMapStatusChangeFinishListener = onMapStatusChangeFinishListener;
    }

    /**
     * Called when a Cluster is clicked.
     */
    public interface OnClusterClickListener<T extends ClusterItem> {
        public boolean onClusterClick(Cluster<T> cluster);
    }

    /**
     * Called when a Cluster's Info Window is clicked.
     */
    public interface OnClusterInfoWindowClickListener<T extends ClusterItem> {
        public void onClusterInfoWindowClick(Cluster<T> cluster);
    }

    /**
     * Called when an individual ClusterItem is clicked.
     */
    public interface OnClusterItemClickListener<T extends ClusterItem> {
        public boolean onClusterItemClick(T item);
    }

    /**
     * Called when an individual ClusterItem's Info Window is clicked.
     */
    public interface OnClusterItemInfoWindowClickListener<T extends ClusterItem> {
        public void onClusterItemInfoWindowClick(T item);
    }

    public interface OnMapStatusChangeFinish {
        public void onMapStatusChangeFinish(MapStatus mapStatus);
    }


}
