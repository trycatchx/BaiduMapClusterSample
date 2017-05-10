/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.mapapi.clusterutil.clustering.algo;

import com.baidu.mapapi.clusterutil.clustering.Cluster;
import com.baidu.mapapi.clusterutil.clustering.ClusterItem;
import com.baidu.mapapi.clusterutil.projection.Bounds;
import com.baidu.mapapi.clusterutil.projection.Point;
import com.baidu.mapapi.clusterutil.projection.SphericalMercatorProjection;
import com.baidu.mapapi.clusterutil.quadtree.PointQuadTree;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.baidu.mapapi.clusterutil.clustering.ClusterManager.MAX_DISTANCE_AT_ZOOM;

/**
 * A simple clustering algorithm with O(nlog n) performance. Resulting clusters are not
 * hierarchical.
 * <p/>
 * High level algorithm:<br>
 * 1. Iterate over items in the order they were added (candidate clusters).<br>
 * 2. Create a cluster with the center of the item. <br>
 * 3. Add all items that are within a certain distance to the cluster. <br>
 * 4. Move any items out of an existing cluster if they are closer to another cluster. <br>
 * 5. Remove those items from the list of candidate clusters.
 * <p/>
 * Clusters have the center of the first element (not the centroid of the items within it).
 */
public class NonHierarchicalDistanceBasedAlgorithm<T extends ClusterItem> implements Algorithm<T> {


    /**
     * Any modifications should be synchronized on mQuadTree.
     */
    private final Collection<QuadItem<T>> mItems = new ArrayList<QuadItem<T>>();

    /**
     * Any modifications should be synchronized on mQuadTree.
     */
    private final PointQuadTree<QuadItem<T>> mQuadTree = new PointQuadTree<QuadItem<T>>(0, 1, 0, 1);

    public static final SphericalMercatorProjection PROJECTION = new SphericalMercatorProjection(1);


    @Override
    public void addItem(T item) {
        final QuadItem<T> quadItem = new QuadItem<T>(item);
        synchronized (mQuadTree) {
            mItems.add(quadItem);
//            mQuadTree.add(quadItem);
        }
    }

    @Override
    public void addItems(Collection<T> items) {
        for (T item : items) {
            addItem(item);
        }
    }

    @Override
    public void clearItems() {
        synchronized (mQuadTree) {
            mItems.clear();
        }
    }

    @Override
    public void removeItem(T item) {
        // TODO: delegate QuadItem#hashCode and QuadItem#equals to its item.
        throw new UnsupportedOperationException("NonHierarchicalDistanceBasedAlgorithm.remove not implemented");
    }

    //上一次 在屏幕上的点。每一个相册只保留第一张图片
    final List<QuadItem<T>> lastTimePointOnScreen = new ArrayList<>();

    /**
     * cluster算法核心
     *
     * @param zoom map的级别
     * @return
     */
    @Override
    public Set<Cluster<T>> getClusters(double zoom, LatLngBounds visibleBounds) {

        final double zoomSpecificSpan = MAX_DISTANCE_AT_ZOOM / Math.pow(2, zoom) / 256;

        //加大搜索的范围 ，重新计算出新的边界 Bounds
        final double halfZoomSpecificSpan = zoomSpecificSpan * 2; //一倍边长的长度（屏幕上的）
        Point northeastP = PROJECTION.toPoint(visibleBounds.northeast); //右上角
        Point southwestP = PROJECTION.toPoint(visibleBounds.southwest); //左下角

        //莫斯托投影，y值越小，纬度越大，x值越大经度越小
        northeastP = new Point(northeastP.x + halfZoomSpecificSpan, northeastP.y - halfZoomSpecificSpan);
        southwestP = new Point(southwestP.x - halfZoomSpecificSpan, southwestP.y + halfZoomSpecificSpan);

        LatLng northeast = PROJECTION.toLatLng(northeastP);
        LatLng southwest = PROJECTION.toLatLng(southwestP);
        LatLngBounds expandVisibleBounds = new LatLngBounds.Builder()
                .include(northeast).include(southwest).build();


        final Set<Cluster<T>> results = new LinkedHashSet<Cluster<T>>();
        final Set<QuadItem<T>> visitedCandidates = new HashSet<QuadItem<T>>();
        final Map<QuadItem<T>, Double> distanceToCluster = new HashMap<QuadItem<T>, Double>();
        final Map<QuadItem<T>, StaticCluster<T>> itemToCluster = new HashMap<QuadItem<T>, com.baidu.mapapi.clusterutil.clustering.algo.StaticCluster<T>>();

        final Collection<QuadItem<T>> mItemsOnScreen = new ArrayList<QuadItem<T>>();


        synchronized (mQuadTree) {
            long time1 = System.currentTimeMillis();
            //计算出屏幕内的点.生成四叉树
            mQuadTree.clear();
            for (QuadItem<T> inBoundItem : mItems) {
                if (expandVisibleBounds.contains(inBoundItem.getPosition())) {
                    mItemsOnScreen.add(inBoundItem);
                    mQuadTree.add(inBoundItem);
                }
            }

            //计算上一次的框里面是否还有包括屏幕内的点
            if (lastTimePointOnScreen.size() > 0) {
                Collections.sort(lastTimePointOnScreen, new ComparatorCenter(visibleBounds));
            }


            Iterator<QuadItem<T>> iterator = lastTimePointOnScreen.iterator();
            while (iterator.hasNext()) {
                QuadItem<T> p = iterator.next();
                //在map缩小的时候，上一次的多个框可能合并成一个框，这时候其他框就不需要再执行search
                if (visitedCandidates.contains(p)) {
                    // Candidate is already part of another cluster.
                    iterator.remove();
                    continue;
                }
                Bounds searchBounds = createBoundsFromSpan(p.getPoint(), zoomSpecificSpan);

                // search 这个框内的所有点
                Collection<QuadItem<T>> clusterItems = mQuadTree.search(searchBounds);

                if (clusterItems.size() == 1) {
                    // Only the current marker is in range. Just add the single item to the results.
                    results.add(p);
                    visitedCandidates.add(p);
                    distanceToCluster.put(p, 0d);
                    continue;
                } else if (clusterItems.size() <= 0) {
                    iterator.remove();
                    continue;
                }

                //生成一个新的簇
                StaticCluster<T> cluster =
                        new StaticCluster<T>(p.getPosition());
                //先添加到返回结果集中
                results.add(cluster);

                //添加在第一个位置
                cluster.add(p.mClusterItem);
                distanceToCluster.put(p, 0d);
                visitedCandidates.add(p);

                //判断图片是否被很多框围住，只分配给最近距离的框
                for (QuadItem<T> clusterItem : clusterItems) {
                    Double existingDistance = distanceToCluster.get(clusterItem);
                    //框中的点，和中心点的距离的平方
                    double distance = distanceSquared(clusterItem.getPoint(), p.getPoint());
                    //本身已经被添加过了
                    if (clusterItem.equals(p)) {
                        continue;
                    }

                    if (existingDistance != null) {
                        // Item already belongs to another cluster. Check if it's closer to this cluster.
                        if (existingDistance < distance) {
                            continue;
                        }
                        // 获取到当前这个图片是被之前哪一个相册（簇）包含进去，那么需要移除
                        itemToCluster.get(clusterItem).remove(clusterItem.mClusterItem);
                    }
                    //更新当前这张图片，距离当前这个中心点的距离。即为最短距离。
                    distanceToCluster.put(clusterItem, distance);
                    //把图片加入当前的簇
                    cluster.add(clusterItem.mClusterItem);
                    //记录当前的图片属于哪一个簇
                    itemToCluster.put(clusterItem, cluster);
                }


                //把已经聚合了的点。直接存起来，下次不参与search计算
                visitedCandidates.addAll(clusterItems);
            }


            //开始算出 剩余屏幕内的点的结果集
            for (QuadItem<T> candidate : mItemsOnScreen) {
                if (visitedCandidates.contains(candidate)) {
                    // Candidate is already part of another cluster.
                    continue;
                }

                Bounds searchBounds = createBoundsFromSpan(candidate.getPoint(), zoomSpecificSpan);

                //不计算屏幕内的


                Collection<QuadItem<T>> clusterItems;
                // search 某边界范围内的clusterItems
                clusterItems = mQuadTree.search(searchBounds);
                if (clusterItems.size() == 1) {
                    // Only the current marker is in range. Just add the single item to the results.
                    lastTimePointOnScreen.add(candidate);
                    results.add(candidate);
                    visitedCandidates.add(candidate);
                    distanceToCluster.put(candidate, 0d);
                    continue;
                }
                //生成一个新的簇
                StaticCluster<T> cluster =
                        new StaticCluster<T>(candidate.getPosition());
                //记录第一张
                lastTimePointOnScreen.add(candidate);
                //先添加到返回结果集中
                results.add(cluster);

                cluster.add(candidate.mClusterItem);
                distanceToCluster.put(candidate, 0d);

                visitedCandidates.add(candidate);
                //判断图片是否被很多框围住，只分配给最近距离的框
                for (QuadItem<T> clusterItem : clusterItems) {
                    Double existingDistance = distanceToCluster.get(clusterItem);
                    //框中的点，和中心点的距离的平方
                    double distance = distanceSquared(clusterItem.getPoint(), candidate.getPoint());
                    if (clusterItem.equals(candidate)) {
                        continue;
                    }

                    if (existingDistance != null) {
                        // Item already belongs to another cluster. Check if it's closer to this cluster.
                        if (existingDistance < distance) {
                            continue;
                        }
                        // 获取到当前这个图片是被之前哪一个相册（簇）包含进去，那么需要移除
                        itemToCluster.get(clusterItem).remove(clusterItem.mClusterItem);
                    }
                    //更新当前这张图片，距离当前这个中心点的距离。即为最短距离。
                    distanceToCluster.put(clusterItem, distance);
                    //把图片加入当前的簇
                    cluster.add(clusterItem.mClusterItem);
                    //记录当前的图片属于哪一个簇
                    itemToCluster.put(clusterItem, cluster);
                }


                visitedCandidates.addAll(clusterItems);
            }

            System.out.println("getClusters time diff:" + (System.currentTimeMillis() - time1));


            //去除四个角都不在屏幕上的 cluster
            Iterator<Cluster<T>> resultsIterator = results.iterator();
            while (resultsIterator.hasNext()) {
                Cluster<T> p = resultsIterator.next();
                Bounds searchBounds = createBoundsFromSpan(PROJECTION.toPoint(p.getPosition()), zoomSpecificSpan / 2);
                if (!visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.maxX, searchBounds.minY)))
                        && !visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.minX, searchBounds.maxY)))
                        && !visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.minX, searchBounds.minY)))
                        && !visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.maxX, searchBounds.maxY)))) {
                    //加多两个点
                    resultsIterator.remove();
                }
            }

            Iterator<QuadItem<T>> lTIterator = lastTimePointOnScreen.iterator();
            while (lTIterator.hasNext()) {
                QuadItem<T> p = lTIterator.next();
                Bounds searchBounds = createBoundsFromSpan(p.getPoint(), zoomSpecificSpan / 2);
                if (!visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.maxX, searchBounds.minY)))
                        && !visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.minX, searchBounds.maxY)))
                        && !visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.minX, searchBounds.minY)))
                        && !visibleBounds.contains(PROJECTION.toLatLng(new Point(searchBounds.maxX, searchBounds.maxY)))) {
                    //加多两个点
                    lTIterator.remove();
                }
            }



        }

        return results;
    }

    public class ComparatorCenter implements Comparator<QuadItem<T>> {
        private Point centerPoint;

        public ComparatorCenter(LatLngBounds b) {

            Point northeastP = PROJECTION.toPoint(b.northeast); //右上角
            Point southwestP = PROJECTION.toPoint(b.southwest); //左下角

            centerPoint = new Point((northeastP.x+southwestP.x)/2,(northeastP.y+southwestP.y)/2);
        }

        @Override
        public int compare(QuadItem<T> lhs, QuadItem<T> rhs) {


            double distance1 = distanceSquared(centerPoint, lhs.getPoint());
            double distance2 = distanceSquared(centerPoint, rhs.getPoint());
            if (distance1 - distance2 > 0) {
                return -1;
            } else if (distance1 - distance2 < 0) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public Collection<T> getItems() {
        final List<T> items = new ArrayList<T>();
        synchronized (mQuadTree) {
            for (QuadItem<T> quadItem : mItems) {
                items.add(quadItem.mClusterItem);
            }
        }
        return items;
    }

    private double distanceSquared(Point a, Point b) {
        return (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y);
    }

    private Bounds createBoundsFromSpan(Point p, double span) {
        // TODO: Use a span that takes into account the visual size of the marker, not just its
        // LatLng.
        double halfSpan = span / 2;
        return new Bounds(
                p.x - halfSpan, p.x + halfSpan,
                p.y - halfSpan, p.y + halfSpan);
    }

    private static class QuadItem<T extends ClusterItem> implements PointQuadTree.Item, Cluster<T> {
        private final T mClusterItem;
        private final Point mPoint;
        private final LatLng mPosition;
        private Set<T> singletonSet;

        private QuadItem(T item) {
            mClusterItem = item;
            mPosition = item.getPosition();
            mPoint = PROJECTION.toPoint(mPosition);
            singletonSet = Collections.singleton(mClusterItem);
        }

        @Override
        public Point getPoint() {
            return mPoint;
        }

        @Override
        public LatLng getPosition() {
            return mPosition;
        }

        @Override
        public Set<T> getItems() {
            return singletonSet;
        }

        @Override
        public int getSize() {
            return 1;
        }

        @Override
        public int hashCode() {
            if (mClusterItem != null) {
                return mClusterItem.hashCode();
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
            if (mClusterItem != null) {
                return mClusterItem.equals(((QuadItem) obj).mClusterItem);
            } else {
                return super.equals(obj);
            }

        }
    }
}
