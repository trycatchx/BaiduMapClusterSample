/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.mapapi.clusterutil.clustering.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ShapeDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;

import com.baidu.mapapi.clusterutil.clustering.Cluster;
import com.baidu.mapapi.clusterutil.clustering.ClusterItem;
import com.baidu.mapapi.clusterutil.clustering.ClusterManager;
import com.baidu.mapapi.clusterutil.projection.SphericalMercatorProjection;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.Projection;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import baidumapsdk.demo.model.LocalPictrue;

import static android.R.attr.path;


/**
 * The default view for a ClusterManager. Markers are animated in and out of clusters.
 */
public class DefaultClusterRenderer<T extends ClusterItem> implements
        com.baidu.mapapi.clusterutil.clustering.view.ClusterRenderer<T> {
    private static final boolean SHOULD_ANIMATE = false;
    private final BaiduMap mMap;
    public final ClusterManager<T> mClusterManager;

    /**
     * Markers for single ClusterItems.
     */
    private MarkerCache<T> mMarkerCache = new MarkerCache<T>();

    /**
     * If cluster size is less than this size, display individual markers.
     */
    private static final int MIN_CLUSTER_SIZE = 4;

    /**
     * The currently displayed set of clusters.
     */
    private Set<Cluster<T>> mLastTimeClusters = new HashSet<Cluster<T>>();

    /**
     * 记录已经真正添加的Marker 和Cluster
     */
    private Map<Marker, Cluster<T>> mMarkerToCluster = new ConcurrentHashMap<>();
    private Map<Cluster<T>, Marker> mClusterToMarker = new ConcurrentHashMap<>();

    //记录准备添加 和已经添加在屏幕上的点（不被执行过remove）
    private Map<Cluster<T>, Integer> onReadyAddCluster = new ConcurrentHashMap<>();


    /**
     * The target zoom level for the current set of clusters.
     */

    private final ViewModifier mViewModifier = new ViewModifier();

    private ClusterManager.OnClusterClickListener<T> mClickListener;
    private ClusterManager.OnClusterInfoWindowClickListener<T> mInfoWindowClickListener;
    private ClusterManager.OnClusterItemClickListener<T> mItemClickListener;
    private ClusterManager.OnClusterItemInfoWindowClickListener<T> mItemInfoWindowClickListener;


    private final ReentrantLock markRemoveAndAddLock = new ReentrantLock();

    public DefaultClusterRenderer(Context context, BaiduMap map, ClusterManager<T> clusterManager) {
        mMap = map;
        mClusterManager = clusterManager;
    }

    @Override
    public void onAdd() {
        mClusterManager.getMarkerCollection().setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                return mItemClickListener != null && mItemClickListener.onClusterItemClick(mMarkerCache.get(marker));
            }
        });


        mClusterManager.getClusterMarkerCollection().setOnMarkerClickListener(new BaiduMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                return mClickListener != null && mClickListener.onClusterClick(mMarkerToCluster.get(marker));
            }
        });

    }

    @Override
    public void onRemove() {
        mClusterManager.getMarkerCollection().setOnMarkerClickListener(null);
        mClusterManager.getClusterMarkerCollection().setOnMarkerClickListener(null);
    }

    /**
     * ViewModifier ensures only one re-rendering of the view occurs at a time, and schedules
     * re-rendering, which is performed by the RenderTask.
     */
    @SuppressLint("HandlerLeak")
    private class ViewModifier extends Handler {
        private static final int RUN_TASK = 0;
        private static final int TASK_FINISHED = 1;
        private boolean mViewModificationInProgress = false;
        private RenderTask mNextClusters = null;
        private float curZoom;

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == TASK_FINISHED) {
                mViewModificationInProgress = false;
                if (mNextClusters != null) {
                    // Run the task that was queued up.
                    sendEmptyMessage(RUN_TASK);
                }
                return;
            }
            removeMessages(RUN_TASK);

            if (mViewModificationInProgress) {
                // Busy - wait for the callback.
                return;
            }

            if (mNextClusters == null) {
                // Nothing to do.
                return;
            }

            RenderTask renderTask;
            synchronized (this) {
                renderTask = mNextClusters;
                mNextClusters = null;
                mViewModificationInProgress = true;
            }

            renderTask.setCallback(new Runnable() {
                @Override
                public void run() {
                    sendEmptyMessage(TASK_FINISHED);
                }
            });
            renderTask.setProjection(mMap.getProjection());
            renderTask.setMapZoom(curZoom);
            new Thread(renderTask).start();
        }

        public void queue(Set<Cluster<T>> clusters, float curZoom) {
            synchronized (this) {
                // Overwrite any pending cluster tasks - we don't care about intermediate states.
                mNextClusters = new RenderTask(clusters);
                this.curZoom = curZoom;
            }
            sendEmptyMessage(RUN_TASK);
        }
    }

    /**
     * Determine whether the cluster should be rendered as individual markers or a cluster.
     */
    protected boolean shouldRenderAsCluster(Cluster<T> cluster) {
        return cluster.getSize() > MIN_CLUSTER_SIZE;
    }

    protected void onRemoveCluster(Cluster<T> cluster) {

    }


    /**
     * Transforms the current view (represented by DefaultClusterRenderer.mLastTimeClusters and DefaultClusterRenderer.mZoom) to a
     * new zoom level and set of clusters.
     * <p/>
     * This must be run off the UI thread. Work is coordinated in the RenderTask, then queued up to
     * This must be run off the UI thread. Work is coordinated in the RenderTask, then queued up to
     * be executed by a MarkerModifier.
     * <p/>
     * There are three stages for the render:
     * <p/>
     * 1. Markers are added to the map
     * <p/>
     * 2. Markers are animated to their final position
     * <p/>
     * 3. Any old markers are removed from the map
     * <p/>
     * When zooming in, markers are animated out from the nearest existing cluster. When zooming
     * out, existing clusters are animated to the nearest new cluster.
     */
    private class RenderTask implements Runnable {
        final Set<Cluster<T>> clusters;
        private Runnable mCallback;
        private Projection mProjection;

        private float mMapZoom;

        private RenderTask(Set<Cluster<T>> clusters) {
            this.clusters = clusters;
        }

        /**
         * A callback to be run when all work has been completed.
         *
         * @param callback
         */
        public void setCallback(Runnable callback) {
            mCallback = callback;
        }

        public void setProjection(Projection projection) {
            this.mProjection = projection;
        }


        public void setMapZoom(float zoom) {
            this.mMapZoom = zoom;

        }

        @SuppressLint("NewApi")
        public void run() {
            if (clusters.equals(mLastTimeClusters)) {
                mCallback.run();
                return;
            }

            final MarkerModifier markerModifier = new MarkerModifier();

            final float zoom = mMapZoom;

            final Set<Cluster<T>> clustersToRemove = mLastTimeClusters;

            final LatLngBounds visibleBounds = mMap.getMapStatus().bound;
            // TODO: Add some padding, so that markers can animate in from off-screen.

            // 添加点Mark到Map 中去.和保存在newMarkers中
            final Set<Cluster<T>> newClusters = Collections.newSetFromMap(
                    new ConcurrentHashMap<Cluster<T>, Boolean>());

            for (Cluster<T> c : clusters) {
                if (zoom != mMap.getMapStatus().zoom) break;

                markerModifier.add(true,
                        new CreateMarkerTask(c, newClusters, null));
            }

            // 所有的marker都被添加了
            markerModifier.waitUntilFree();


            // 移除老的cluster
            for (final Cluster<T> cluster : clustersToRemove) {
                if (zoom != mMap.getMapStatus().zoom) break;

                if (!newClusters.contains(cluster)) {
                    markerModifier.remove(visibleBounds.contains(cluster.getPosition()), cluster);
                    // 移除需要移除的点
                    clustersToRemove.remove(cluster);
                }
            }

            markerModifier.waitUntilFree();

            //剩余多少个点没有被移除，先保留着，下一轮移除
            newClusters.addAll(clustersToRemove);
            //保留起来
            mLastTimeClusters = newClusters;

            mCallback.run();
        }
    }

    @Override
    public void onClustersChanged(Set<Cluster<T>> clusters, float curZoom) {
        mViewModifier.queue(clusters, curZoom);
    }

    @Override
    public void setOnClusterClickListener(ClusterManager.OnClusterClickListener<T> listener) {
        mClickListener = listener;
    }

    @Override
    public void setOnClusterInfoWindowClickListener(ClusterManager
                                                            .OnClusterInfoWindowClickListener<T> listener) {
        mInfoWindowClickListener = listener;
    }

    @Override
    public void setOnClusterItemClickListener(ClusterManager.OnClusterItemClickListener<T> listener) {
        mItemClickListener = listener;
    }

    @Override
    public void setOnClusterItemInfoWindowClickListener(ClusterManager
                                                                .OnClusterItemInfoWindowClickListener<T> listener) {
        mItemInfoWindowClickListener = listener;
    }


    /**
     * Handles all markerWithPosition manipulations on the map. Work (such as adding, removing, or
     * animating a markerWithPosition) is performed while trying not to block the rest of the app's
     * UI.
     */
    @SuppressLint("HandlerLeak")
    private class MarkerModifier extends Handler implements MessageQueue.IdleHandler {
        private static final int BLANK = 0;

        private final Lock lock = new ReentrantLock();
        private final Condition busyCondition = lock.newCondition();

        private Queue<CreateMarkerTask> mCreateMarkerTasks = new LinkedList<CreateMarkerTask>();
        private Queue<CreateMarkerTask> mOnScreenCreateMarkerTasks = new LinkedList<CreateMarkerTask>();
        private Queue<Cluster<T>> mRemoveClusterTasks = new LinkedList<Cluster<T>>();
        private Queue<Cluster<T>> mOnScreenRemoveClusterTasks = new LinkedList<Cluster<T>>();

        /**
         * Whether the idle listener has been added to the UI thread's MessageQueue.
         */
        private boolean mListenerAdded;


        private MarkerModifier() {
            super(Looper.getMainLooper());
        }

        /**
         * Creates markers for a cluster some time in the future.
         *
         * @param priority whether this operation should have priority.
         */
        public void add(boolean priority, CreateMarkerTask c) {
            lock.lock();
            sendEmptyMessage(BLANK);
            if (priority) {
                mOnScreenCreateMarkerTasks.add(c);
            } else {
                mCreateMarkerTasks.add(c);
            }
            lock.unlock();
        }

        /**
         * Removes a markerWithPosition some time in the future.
         *
         * @param priority whether this operation should have priority.
         * @param c        the markerWithPosition to remove.
         */
        public void remove(boolean priority, Cluster<T> c) {
            lock.lock();
            sendEmptyMessage(BLANK);
            if (priority) {
                mOnScreenRemoveClusterTasks.add(c);
            } else {
                mRemoveClusterTasks.add(c);
            }
            lock.unlock();
        }


        @Override
        public void handleMessage(Message msg) {
            if (!mListenerAdded) {
                Looper.myQueue().addIdleHandler(this);
                mListenerAdded = true;
            }
            removeMessages(BLANK);

            lock.lock();
            try {

                // Perform up to 10 tasks at once.
                // Consider only performing 10 remove tasks, not adds and animations.
                // Removes are relatively slow and are much better when batched.
                for (int i = 0; i < 10; i++) {

                    performNextTask();
                }

                if (!isBusy()) {
                    mListenerAdded = false;
                    Looper.myQueue().removeIdleHandler(this);
                    // Signal any other threads that are waiting.
                    busyCondition.signalAll();
                } else {
                    // Sometimes the idle queue may not be called - schedule up some work regardless
                    // of whether the UI thread is busy or not.
                    // TODO: try to remove this.

                    sendEmptyMessageDelayed(BLANK, 10);

                }
            } finally {
                lock.unlock();
            }
        }


        /**
         * Perform the next task. Prioritise any on-screen work.
         */
        private void performNextTask() {
            //先加
            if (!mOnScreenCreateMarkerTasks.isEmpty()) {
                mOnScreenCreateMarkerTasks.poll().perform(this);
            } else if (!mOnScreenRemoveClusterTasks.isEmpty()) {
                removeCluster(mOnScreenRemoveClusterTasks.poll());
            } else if (!mCreateMarkerTasks.isEmpty()) {
                mCreateMarkerTasks.poll().perform(this);
            } else if (!mRemoveClusterTasks.isEmpty()) {
                removeCluster(mRemoveClusterTasks.poll());
            }
        }

        private void removeCluster(Cluster<T> cluster) {
            //这里加锁的原因是有可能 图片通过Glide下载回来的时候，刚好把需要删除的图片，又加载上去
            markRemoveAndAddLock.lock();
            onReadyAddCluster.remove(cluster);

            Marker m = mClusterToMarker.get(cluster);
            if (m != null) {

                mClusterToMarker.remove(cluster);
                mMarkerCache.remove(m);
                mMarkerToCluster.remove(m);

                mClusterManager.getMarkerManager().remove(m);
            } else {
                //说明还没有下载完。回调去取消Gilde 的图片下载
                onRemoveCluster(cluster);
            }
            markRemoveAndAddLock.unlock();

        }

        /**
         * @return true if there is still work to be processed.
         */
        public boolean isBusy() {
            try {
                lock.lock();
                return !(mCreateMarkerTasks.isEmpty() && mOnScreenCreateMarkerTasks.isEmpty()
                        && mOnScreenRemoveClusterTasks.isEmpty() && mRemoveClusterTasks.isEmpty());
            } finally {
                lock.unlock();
            }
        }

        /**
         * Blocks the calling thread until all work has been processed.
         */
        public void waitUntilFree() {
            while (isBusy()) {
                // Sometimes the idle queue may not be called - schedule up some work regardless
                // of whether the UI thread is busy or not.
                // TODO: try to remove this.
                sendEmptyMessage(BLANK);
                lock.lock();
                try {
                    if (isBusy()) {
                        busyCondition.await();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public boolean queueIdle() {
            // When the UI is not busy, schedule some work.
            sendEmptyMessage(BLANK);
            return true;
        }
    }


    //异步回调回来的icon ，需要
    public void setIconByCluster(String path, Cluster<T> cluster, MarkerOptions markerOptions) {
        markRemoveAndAddLock.lock();
        Integer size = onReadyAddCluster.get(cluster);
        if (size != null && cluster.getSize() == size) {
            Marker marker = mClusterToMarker.get(cluster);
            if (marker != null) {
                marker.setIcon(markerOptions.getIcon());
            } else {
                marker = mClusterManager.getClusterMarkerCollection().addMarker(markerOptions);
            }

            mMarkerToCluster.put(marker, cluster);
            mClusterToMarker.put(cluster, marker);
        }
        markRemoveAndAddLock.unlock();
    }



    /**
     * A cache of markers representing individual ClusterItems.
     */
    private static class MarkerCache<T> {
        private Map<T, Marker> mCache = new HashMap<T, Marker>();
        private Map<Marker, T> mCacheReverse = new HashMap<Marker, T>();

        public Marker get(T item) {
            return mCache.get(item);
        }

        public T get(Marker m) {
            return mCacheReverse.get(m);
        }

        public void put(T item, Marker m) {
            mCache.put(item, m);
            mCacheReverse.put(m, item);
        }

        public void remove(Marker m) {
            T item = mCacheReverse.get(m);
            mCacheReverse.remove(m);
            mCache.remove(item);
        }
    }

    /**
     * Called before the marker for a ClusterItem is added to the map.
     */
    protected void onBeforeClusterItemRendered(T item, MarkerOptions markerOptions) {
    }

    /**
     * Called before the marker for a Cluster is added to the map.
     * The default implementation draws a circle with a rough count of the number of items.
     */
    protected void onBeforeClusterRendered(Cluster<T> cluster, MarkerOptions markerOptions) {

    }


    /**
     * Creates markerWithPosition(s) for a particular cluster, animating it if necessary.
     */
    private class CreateMarkerTask {
        private final Cluster<T> cluster;
        private final Set<Cluster<T>> newClusters;
        private final LatLng animateFrom;

        /**
         */
        public CreateMarkerTask(Cluster<T> c, Set<Cluster<T>> newClusters, LatLng animateFrom) {
            this.cluster = c;
            this.newClusters = newClusters;
            this.animateFrom = animateFrom;

        }

        private void perform(MarkerModifier markerModifier) {
            // Don't show small clusters. Render the markers inside, instead.
            markRemoveAndAddLock.lock();
            //真正添加Marker 的地方

            Marker marker = mClusterToMarker.get(cluster);
            if (marker == null || (marker != null
                    && mMarkerToCluster.get(marker).getSize() != cluster.getSize())) {
                //异步加载占时不添加Marker
                Integer size = onReadyAddCluster.get(cluster);
                if (size == null || size != cluster.getSize()) {
                    onReadyAddCluster.put(cluster,cluster.getSize());
                    onBeforeClusterRendered(cluster, new MarkerOptions()
                            .position(cluster.getPosition()));
                }
            }
            markRemoveAndAddLock.unlock();
            newClusters.add(cluster);

        }
    }
}
