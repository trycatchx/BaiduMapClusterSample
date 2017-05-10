/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.mapapi.clusterutil.clustering.algo;

import android.support.v4.util.LruCache;

import com.baidu.mapapi.clusterutil.clustering.Cluster;
import com.baidu.mapapi.clusterutil.clustering.ClusterItem;
import com.baidu.mapapi.model.LatLngBounds;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Optimistically fetch clusters for adjacent zoom levels, caching them as necessary.
 */
public class PreCachingAlgorithmDecorator<T extends ClusterItem> implements Algorithm<T> {
    private final Algorithm<T> mAlgorithm;

    // TODO: evaluate maxSize parameter for LruCache.
    private final LruCache<Integer, Set<? extends Cluster<T>>> mCache =
            new LruCache<>(8);
    private final ReadWriteLock mCacheLock = new ReentrantReadWriteLock();

    public PreCachingAlgorithmDecorator(Algorithm<T> algorithm) {
        mAlgorithm = algorithm;
    }

    public void addItem(T item) {
        mAlgorithm.addItem(item);
        clearCache();
    }

    @Override
    public void addItems(Collection<T> items) {
        mAlgorithm.addItems(items);
        clearCache();
    }

    @Override
    public void clearItems() {
        mAlgorithm.clearItems();
        clearCache();
    }

    public void removeItem(T item) {
        mAlgorithm.removeItem(item);
        clearCache();
    }

    private void clearCache() {
        mCache.evictAll();
    }

    @Override
    public Set<Cluster<T>> getClusters(double zoom, LatLngBounds visibleBounds) {

        Set<Cluster<T>> results = mAlgorithm.getClusters(zoom, visibleBounds);
        // TODO: Check if requests are already in-flight.
        return results;
    }

    @Override
    public Collection<T> getItems() {
        return mAlgorithm.getItems();
    }



}
