/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package baidumapsdk.demo.map;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import com.baidu.mapapi.clusterutil.clustering.Cluster;
import com.baidu.mapapi.clusterutil.clustering.ClusterManager;
import com.baidu.mapapi.clusterutil.clustering.view.PersonRenderer;
import com.baidu.mapapi.clusterutil.projection.Point;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMap.OnMapLoadedCallback;
import com.baidu.mapapi.map.LogoPosition;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.dmsys.airdisk.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import baidumapsdk.demo.model.LocalPictrue;
import baidumapsdk.demo.util.CoordinateTransformUtil;
import baidumapsdk.demo.util.SdCardFileTool;
import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static com.baidu.mapapi.clusterutil.clustering.ClusterManager.MAX_DISTANCE_AT_ZOOM;
import static com.baidu.mapapi.clusterutil.clustering.algo.NonHierarchicalDistanceBasedAlgorithm.PROJECTION;


/**
 * 此Demo用来说明点聚合功能
 */
public class MarkerClusterDemo extends Activity implements OnMapLoadedCallback {

    MapView mMapView;
    volatile BaiduMap mBaiduMap;
    MapStatus ms;
    private ClusterManager<LocalPictrue> mClusterManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marker_cluster_demo);
        mMapView = (MapView) findViewById(R.id.bmapView);
        mMapView.showZoomControls(false);
        mMapView.setLogoPosition(LogoPosition.logoPostionRightBottom);
//        ms = new MapStatus.Builder().target(new LatLng(39.914935, 116.403119)).zoom(4).build();
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMaxAndMinZoomLevel(21, 4);
        mBaiduMap.setOnMapLoadedCallback(this);
//        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(ms));


        mBaiduMap.getUiSettings().setOverlookingGesturesEnabled(false);
        mBaiduMap.getUiSettings().setRotateGesturesEnabled(false);

        // 定义点聚合管理类ClusterManager
        mClusterManager = new ClusterManager<LocalPictrue>(this, mBaiduMap);
        mClusterManager.setRenderer(new PersonRenderer(this,mBaiduMap,mClusterManager));

        // 设置地图监听，当地图状态发生改变时，进行点聚合运算
        mBaiduMap.setOnMapStatusChangeListener(mClusterManager);
        // 设置maker点击时的响应
        mBaiduMap.setOnMarkerClickListener(mClusterManager);
        location();

        mClusterManager.setOnClusterClickListener(new ClusterManager.OnClusterClickListener<LocalPictrue>() {
            @Override
            public boolean onClusterClick(Cluster<LocalPictrue> cluster) {
                Toast.makeText(MarkerClusterDemo.this,
                        "有" + cluster.getSize() + "个点", Toast.LENGTH_SHORT).show();

                return false;
            }
        });
        mClusterManager.setOnClusterItemClickListener(new ClusterManager.OnClusterItemClickListener<LocalPictrue>() {
            @Override
            public boolean onClusterItemClick(LocalPictrue item) {
                Toast.makeText(MarkerClusterDemo.this,
                        "点击单个Item", Toast.LENGTH_SHORT).show();

                return false;
            }
        });

        //移动的回调
        mClusterManager.setOnMapStatusChangeFinishListener(new ClusterManager.OnMapStatusChangeFinish() {
            @Override
            public void onMapStatusChangeFinish(MapStatus mapStatus) {

                markerGetAndSet(mapStatus.zoom,mapStatus.bound);

            }
        });
    }

    //获取屏幕上的点，并且开始计算以及显示
    public void markerGetAndSet(final float zoom, final LatLngBounds visibleBounds) {

        Observable.fromCallable(new Callable<ArrayList<LocalPictrue>>() {

            @Override
            public ArrayList<LocalPictrue> call() {

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

                //右上角的经纬度 wgs 格式
                double [] wgs_northeast = CoordinateTransformUtil.bd09towgs84( expandVisibleBounds.northeast.longitude,
                        expandVisibleBounds.northeast.latitude);
                //左下角的经纬度 WGS 格式，
                double [] wgs_southwest = CoordinateTransformUtil.bd09towgs84( expandVisibleBounds.southwest.longitude,
                        expandVisibleBounds.southwest.latitude);


                //传下去JNI 获取 区域内的相片

                //获取当前屏幕的点，目前是获取sdcard相册的点
                return SdCardFileTool.getMusicData(MarkerClusterDemo.this);
            }
        }).subscribeOn(Schedulers.newThread())
                .observeOn(Schedulers.newThread())
                .subscribe(new Action1<ArrayList<LocalPictrue>>() {

                    @Override
                    public void call(ArrayList<LocalPictrue> localPictrues) {
                        mClusterManager.clearItems();
                        mClusterManager.addItems(localPictrues);
                        //算法计算聚合，并显示
                        mClusterManager.cluster(zoom,visibleBounds);
                    }
                });
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        mMapView.onResume();

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
    }

    /**
     * 向地图添加Marker点
     */
    public void location() {
        // 添加Marker点


        Observable.fromCallable(new Callable<List<LocalPictrue>>() {

            @Override
            public List<LocalPictrue> call() {

                return SdCardFileTool.getMusicData(MarkerClusterDemo.this);
            }
        }).subscribeOn(Schedulers.newThread())
                .observeOn(Schedulers.newThread())
                .subscribe(new Action1<List<LocalPictrue>>() {

                    @Override
                    public void call(List<LocalPictrue> localPictrues) {
//                        mClusterManager.addItems(localPictrues);
                        ms = new MapStatus.Builder().target(localPictrues.get(0).getPosition()).zoom(8).build();
                        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(ms));

                    }
                });


    }


    @Override
    public void onMapLoaded() {
        // TODO Auto-generated method stub
        // 添加Marker点

    }




}
