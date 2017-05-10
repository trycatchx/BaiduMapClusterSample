package baidumapsdk.demo.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ThumbnailUtils;

import static android.graphics.BitmapFactory.decodeFile;


/**
 * Created by jiong103 on 2017/4/20.
 */

public class BitmapTool {



    public static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {


        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        return BitmapFactory.decodeFile(path, options);

    }


    public static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight, int size) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        decodeFile(path, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);


        bitmap = ThumbnailUtils.extractThumbnail(bitmap, reqWidth, reqWidth, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        return getRoundedCornerBitmap(bitmap, Color.WHITE, reqWidth / 10, reqWidth / 4, size);

    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }


    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int color, int cornerDips, int borderDips, int size) {

        //创建一个位图（大小包含了边框）
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth() + 2 * borderDips,
                bitmap.getHeight() + 2 * borderDips,
                Bitmap.Config.ARGB_8888);
        //通过这个位图的size 生成一块画布
        Canvas canvas = new Canvas(output);
        //设置画布的颜色为透明
        canvas.drawColor(Color.TRANSPARENT);
        //画一个圆角矩形底层图（白色）
        int quarterOfBorderDip = borderDips *3 / 4;
        final RectF rectF = new RectF(quarterOfBorderDip, quarterOfBorderDip,
                output.getWidth() - quarterOfBorderDip, output.getHeight() - quarterOfBorderDip);
        final Paint paint = new Paint();
        // prepare canvas for transfer
        paint.setAntiAlias(true);
        paint.setStrokeWidth((float) borderDips);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        //开始画圆角矩形
        canvas.drawRoundRect(rectF, borderDips-quarterOfBorderDip, borderDips-quarterOfBorderDip, paint);
        //把bitmap 覆盖上去，画在在中间位置
        canvas.drawBitmap(bitmap, borderDips, borderDips, null);


        if (size > 1) {
            Paint circlePaint = new Paint();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(0xff157EFB);
            circlePaint.setStyle(Paint.Style.FILL);
            //circlePaint.setAlpha(255);

            Paint textPaint = new Paint();
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(23);
            textPaint.setFakeBoldText(true);

            String text = size > 999 ? "999+" : String.valueOf(size);
            //计算文字的长度
            float width = textPaint.measureText(text);
            //通过文字的高度和长度，计算出能包含它的红点的半径
            float radius = (float) Math.sqrt(23 * 23 + width * width) /2 + 3;
            //计算出红点的x
            float rx = output.getWidth() - quarterOfBorderDip;
            //计算红点的y
            float ry = quarterOfBorderDip;
            //画圆
            canvas.drawCircle(rx, ry, radius, circlePaint);
            //画文字：文字起始点X为（rx - width / 2） ，文字基线 y：ry + 23 / 2
            canvas.drawText(text,
                    rx - width / 2, ry + 23 / 2, textPaint);
        }
        bitmap.recycle();
        return output;
    }
    public static Bitmap getRedDotBitmap(Bitmap bitmap, int borderDips, int size) {



        //通过这个位图的size 生成一块画布
        Canvas canvas = new Canvas(bitmap);
        //设置画布的颜色为透明
        canvas.drawColor(Color.TRANSPARENT);
        int quarterOfBorderDip = borderDips *3 / 4;


        if (size > 1) {
            Paint circlePaint = new Paint();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(0xff157EFB);
            circlePaint.setStyle(Paint.Style.FILL);
            //circlePaint.setAlpha(255);

            Paint textPaint = new Paint();
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(23);
            textPaint.setFakeBoldText(true);

            String text = size > 999 ? "999+" : String.valueOf(size);
            //计算文字的长度
            float width = textPaint.measureText(text);
            //通过文字的高度和长度，计算出能包含它的红点的半径
            float radius = (float) Math.sqrt(23 * 23 + width * width) /2 + 3;
            //计算出红点的x
            float rx = bitmap.getWidth() - quarterOfBorderDip;
            //计算红点的y
            float ry = quarterOfBorderDip;
            //画圆
            canvas.drawCircle(rx, ry, radius, circlePaint);
            //画文字：文字起始点X为（rx - width / 2） ，文字基线 y：ry + 23 / 2
            canvas.drawText(text,
                    rx - width / 2, ry + 23 / 2, textPaint);
        }

        return bitmap;
    }


}
