package baidumapsdk.demo.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.provider.MediaStore;

import com.baidu.mapapi.model.inner.GeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import baidumapsdk.demo.model.LocalPictrue;

import static baidumapsdk.demo.util.CoordinateTransformUtil.wgs84tobd09;
import static com.baidu.platform.comapi.map.h.f;


public class SdCardFileTool {


    public static ArrayList<LocalPictrue> getMusicData(Context context) {

        ArrayList<LocalPictrue> musicList = new ArrayList<LocalPictrue>();
        ContentResolver cr = context.getContentResolver();

        // 获取SD卡上的音频
//		Cursor cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//				null, null, null, MediaStore.Images.Media.DEFAULT_SORT_ORDER);

        String[] projection = {MediaStore.MediaColumns.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME};

        Cursor cursor = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, MediaStore.Images.Media.DEFAULT_SORT_ORDER);
        readCursor(context, cursor, musicList);

        return musicList;
    }

    private static void readCursor(Context context, Cursor cursor, List<LocalPictrue> musicList) {

        if (null == cursor) {
            return;
        }
        ExifInterface exif = null;
        float[] latLong = new float[2];
        int height = DensityUtil.dip2px(context, 88);
        if (cursor.moveToFirst()) {
            do {
                String title = cursor.getString(cursor
                        .getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));

                String url = cursor.getString(cursor
                        .getColumnIndex(MediaStore.Images.Media.DATA));
                try {
                    exif = new ExifInterface(url);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String LATITUDE = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
                String LATITUDE_REF = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
                String LONGITUDE = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
                String LONGITUDE_REF = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);

                // your Final lat Long Values
                Double Latitude = new Double(0);
                Double Longitude = new Double(0);


                if ((LATITUDE != null)
                        && (LATITUDE_REF != null)
                        && (LONGITUDE != null)
                        && (LONGITUDE_REF != null)) {

                    if (LATITUDE_REF.equals("N")) {
                        Latitude = convertToDegree(LATITUDE);
                    } else {
                        Latitude = 0 - convertToDegree(LATITUDE);
                    }

                    if (LONGITUDE_REF.equals("E")) {
                        Longitude = convertToDegree(LONGITUDE);
                    } else {
                        Longitude = 0 - convertToDegree(LONGITUDE);
                    }

                } else {
                    continue;
                }

                double[] LatLng = CoordinateTransformUtil.wgs84tobd09(Longitude,Latitude);
                if(LatLng.length == 2) {
                    musicList.add(new LocalPictrue(title, url, height, LatLng[1], LatLng[0]));
                }
            } while (cursor.moveToNext());

            if (cursor != null) {
                cursor.close();
            }
        }
    }
    private static void readCursor1(Context context, Cursor cursor, List<LocalPictrue> musicList) {

        if (null == cursor) {
            return;
        }
        ExifInterface exif = null;
        float[] latLong = new float[2];
        int height = DensityUtil.dip2px(context, 88);
        if (cursor.moveToFirst()) {
            do {
                String title = cursor.getString(cursor
                        .getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));

                String url = cursor.getString(cursor
                        .getColumnIndex(MediaStore.Images.Media.DATA));
                try {
                    exif = new ExifInterface(url);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                exif.getLatLong(latLong);


                //22.546415
                //113.942184
                // 113.966627,22.557602
                //模拟数据
                for(int i = 0;i<5;i++) {
                    float random = (float) Math.random();
                    float random1 = (float) Math.random();
                    musicList.add(new LocalPictrue(title, url,height,latLong[0]+random,random1+latLong[1]));
                    musicList.add(new LocalPictrue(title, url,height,latLong[0]+random,random1+latLong[1]));
                }


            } while (cursor.moveToNext());

            if (cursor != null) {
                cursor.close();
            }
        }
    }


    private static Double convertToDegree(String stringDMS) {
        Double result = null;
        String[] DMS = stringDMS.split(",", 3);

        String[] stringD = DMS[0].split("/", 2);
        Double D0 = new Double(stringD[0]);
        Double D1 = new Double(stringD[1]);
        Double FloatD = D0 / D1;

        String[] stringM = DMS[1].split("/", 2);
        Double M0 = new Double(stringM[0]);
        Double M1 = new Double(stringM[1]);
        Double FloatM = M0 / M1;

        String[] stringS = DMS[2].split("/", 2);
        Double S0 = new Double(stringS[0]);
        Double S1 = new Double(stringS[1]);
        Double FloatS = S0 / S1;

        result = new Double(FloatD + (FloatM / 60) + (FloatS / 3600));

        return result;


    }

    ;


}
