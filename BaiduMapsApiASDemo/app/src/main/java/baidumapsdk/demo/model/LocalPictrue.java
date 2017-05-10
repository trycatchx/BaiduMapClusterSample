package baidumapsdk.demo.model;

import com.baidu.mapapi.clusterutil.clustering.ClusterItem;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.model.LatLng;

import baidumapsdk.demo.util.BitmapTool;

/**
 * Created by jiong103 on 2017/4/20.
 */

/**
 * 每个Marker点，包含Marker点坐标以及图标
 */

public class LocalPictrue implements ClusterItem {
    public String name;
    public String path;
    public int height;
    //纬度
    public double lat;
    //经度
    public double lng;

    public LocalPictrue() {
    }

    public LocalPictrue(String name, String path, int height, double lat, double lng) {
        this.name = name;
        this.path = path;
        this.height = height;
        this.lat = lat;
        this.lng = lng;
    }

    @Override
    public LatLng getPosition() {
        return new LatLng(lat, lng);
    }

    @Override
    public BitmapDescriptor getBitmapDescriptor() {
        return BitmapDescriptorFactory
                .fromBitmap(BitmapTool.decodeSampledBitmapFromFile(path, height, height, 1));
    }

    @Override
    public int hashCode() {
        if (path != null) {
            return path.hashCode();
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
        if (path != null) {
            LocalPictrue tmp = (LocalPictrue) obj;
            return path.equals(tmp.path) && lat == tmp.lat && lng == tmp.lng;
        } else {
            return super.equals(obj);
        }
    }
}
