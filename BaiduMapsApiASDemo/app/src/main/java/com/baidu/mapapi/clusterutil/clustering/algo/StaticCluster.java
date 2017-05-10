/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.mapapi.clusterutil.clustering.algo;

import com.baidu.mapapi.clusterutil.clustering.Cluster;
import com.baidu.mapapi.clusterutil.clustering.ClusterItem;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.media.CamcorderProfile.get;

/**
 * A cluster whose center is determined upon creation.
 */
public class StaticCluster<T extends ClusterItem> implements Cluster<T> {
    private final LatLng mCenter;
    private final List<T> mItems = new ArrayList<>();

    public StaticCluster(LatLng center) {
        mCenter = center;
    }


    public boolean add(T t) {
        return mItems.add(t);
    }

    @Override
    public LatLng getPosition() {
        return mCenter;
    }

    public boolean remove(T t) {
        return mItems.remove(t);
    }

    @Override
    public Collection<T> getItems() {
        return mItems;
    }

    @Override
    public int getSize() {
        return mItems.size();
    }

    @Override
    public String toString() {
        return "StaticCluster{"
                + "mCenter=" + mCenter
                + ", mItems.size=" + mItems.size()
                + '}';
    }


    @Override
    public int hashCode() {
        if (mCenter != null && mItems != null) {
            return (int) mCenter.latitude * 37
                    + (int) mCenter.longitude * 37 * 37
                    + (mItems.size() <= 0 ? 0 : mItems.get(0).hashCode() * 37 * 37 * 37);
        } else {
            return super.hashCode();
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StaticCluster<T> tmp = (StaticCluster<T>) obj;

        if (this.mItems.size() <= 0 || tmp.mItems.size() <= 0) {
            return this.mItems == tmp.mItems;
        } else {
            return tmp.mCenter.latitude == this.mCenter.latitude
                    && tmp.mCenter.longitude == this.mCenter.longitude
                    && tmp.mItems.get(0).equals(this.mItems.get(0));
//                    && tmp.mItems.size() == this.mItems.size();
        }

    }
}