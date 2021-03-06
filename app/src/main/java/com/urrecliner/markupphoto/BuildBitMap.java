package com.urrecliner.markupphoto;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Environment;
import androidx.exifinterface.media.ExifInterface;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static com.urrecliner.markupphoto.Vars.databaseIO;
import static com.urrecliner.markupphoto.Vars.mContext;
import static com.urrecliner.markupphoto.Vars.sharedAlpha;
import static com.urrecliner.markupphoto.Vars.sizeX;
import static com.urrecliner.markupphoto.Vars.utils;

class BuildBitMap {

    String sFood, sPlace, sAddress, sLatLng;
    Bitmap signatureMap;
    Activity activity;
    Context context;
    int cameraOrientation;

    public void init(String latlng, Activity activity, Context context, int cameraOrientation) {
        this.activity = activity;this.context = context;
        this.cameraOrientation = cameraOrientation;
        this.signatureMap = buildSignatureMap();
        this.sLatLng = latlng;
    }

    Photo makeSumNail(Photo photo) {
        ExifInterface exif;
        String fullFileName = photo.getFullFileName().toString();
        boolean landscape;

        Bitmap bitmap = BitmapFactory.decodeFile(fullFileName).copy(Bitmap.Config.RGB_565, false);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        try {
            exif = new ExifInterface(fullFileName);
        } catch (IOException e) {
            Toast.makeText(mContext,"No photo information on\n"+photo.getShortName(), Toast.LENGTH_LONG).show();
            return photo;
        }
        String Orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
        int orientation = Integer.parseInt(Orientation);
        if (orientation == 0)
            orientation = 1;
        if (orientation != 1) {
            Matrix matrix = new Matrix();
            if (orientation == 8)
                matrix.postRotate(-90);
            else if (orientation == 6)
                matrix.postRotate(90);
            else if (orientation == 3)
                matrix.postRotate(180);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
            width = bitmap.getWidth();
            height = bitmap.getHeight();
        }

        landscape = width > height;
        /* crop center image only */
        int sWidth;
        int sHeight;

        if (landscape) {
            sHeight = height * 11 / 16;
            sWidth = sHeight * 19 / 11;
            if (sWidth > width) {
                utils.logE("width",width+">" + sWidth+", "+height+">"+sHeight+", land=T, "+fullFileName);
                sWidth = width * 5 / 8;
            }
        }
        else {
            sWidth = width * 7 / 8;
            sHeight = sWidth * 9 / 6;
            if (sHeight > height) {
                utils.logE("height",width+">" + sWidth+", "+height+">"+sHeight+" land=F"+fullFileName);
                sHeight = height * 5 / 8;
            }
        }

        Bitmap sBitmap = Bitmap.createBitmap(bitmap, (width - sWidth)/2, (height - sHeight) /2, sWidth, sHeight);       // crop center
        int outWidth = sizeX * 5 / 18;   // smaller scale
        int outHeight = outWidth * sHeight / sWidth;
        sBitmap = Bitmap.createScaledBitmap(sBitmap, outWidth, outHeight, false);
        photo.setOrientation(orientation);
        photo.setBitmap(sBitmap);
        databaseIO.insert(photo);
        return photo;
    }

    Bitmap makeChecked(Bitmap photoMap) {

        int delta = 2;
        int delta2 = delta + delta;
        int width = photoMap.getWidth();
        int height = photoMap.getHeight();

        Bitmap outMap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outMap);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, width, height);
        final RectF rectF = new RectF(rect);
        final float roundPx = 10;
        paint.setAntiAlias(true);
        paint.setColor(mContext.getColor(R.color.xorColor));
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));
        Bitmap bitmap = Bitmap.createBitmap(photoMap, delta2, delta2, width-delta2-delta2, height-delta2-delta2);
        canvas.drawBitmap(bitmap, delta2, delta2, paint);
        return outMap;
    }

    Bitmap markDateLocSignature(Bitmap photoMap, long timeStamp, String food, String place, String address) {

        int width = photoMap.getWidth();
        int height = photoMap.getHeight();
        Bitmap newMap = Bitmap.createBitmap(width, height, photoMap.getConfig());
        Canvas canvas = new Canvas(newMap);
        canvas.drawBitmap(photoMap, 0f, 0f, null);
        markDateTime(timeStamp, width, height, canvas);
        markSignature(width, height, canvas);
        if (place.equals(" "))
            return newMap;
        this.sFood = food; this.sPlace = place; this.sAddress = address;
        markFoodPlaceAddress(width, height, canvas);
        return newMap;
    }

    private void markDateTime(long timeStamp, int width, int height, Canvas canvas) {
        final SimpleDateFormat sdfHourMin = new SimpleDateFormat("`yy/MM/dd(EEE) HH:mm", Locale.KOREA);
        int fontSize = (width>height) ? (width+height)/48 : (width+height)/54;  // date time
        String dateTime = sdfHourMin.format(timeStamp);
        int xPos = (width>height) ? width/6+fontSize: width/4+fontSize;
        int yPos = (width>height) ? height/9: height/10;
        drawTextOnCanvas(canvas, dateTime, fontSize, xPos, yPos);
    }

    private void markSignature(int width, int height, Canvas canvas) {
        int sigSize = (width + height) / 14;
        Bitmap sigMap = Bitmap.createScaledBitmap(signatureMap, sigSize, sigSize, false);
        int xPos = width - sigSize - width / 20;
        int yPos = (width>height) ? height/14: height/16;
        Paint paint = new Paint(); paint.setAlpha(Integer.parseInt(sharedAlpha));
        canvas.drawBitmap(sigMap, xPos, yPos, paint);
    }

    private void markFoodPlaceAddress(int width, int height, Canvas canvas) {

        int xPos = width / 2;
        int fontSize = (height + width) / 64;  // gps
        int yPos = height - fontSize/2;
        yPos = drawTextOnCanvas(canvas, sLatLng, fontSize, xPos, yPos);
        fontSize = fontSize * 14 / 10;  // address
        yPos -= fontSize + fontSize / 6;
        yPos = drawTextOnCanvas(canvas, sAddress, fontSize, xPos, yPos);
        fontSize = fontSize * 14 / 10;  // Place
        yPos -= fontSize + fontSize / 4;
        yPos = drawTextOnCanvas(canvas, sPlace, fontSize, xPos, yPos);
        yPos -= fontSize + fontSize / 4; // food
        drawTextOnCanvas(canvas, sFood, fontSize, xPos, yPos);
    }

    int drawTextOnCanvas(Canvas canvas, String text, int fontSize, int xPos, int yPos) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(fontSize);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        int cWidth = canvas.getWidth() * 3 / 4;
        float tWidth = paint.measureText(text);
        int pos;
        if (tWidth > cWidth) {
            int length = text.length() / 2;
            for (pos = length; pos < text.length(); pos++)
                if (text.substring(pos,pos+1).equals(" "))
                    break;
            String text1 = text.substring(pos);
            drawOutLinedText(canvas, text1, xPos, yPos, fontSize);
            yPos -= fontSize + fontSize / 4;
            text1 = text.substring(0, pos);
            drawOutLinedText(canvas, text1, xPos, yPos, fontSize);
            return yPos;
        }
        else
            drawOutLinedText(canvas, text, xPos, yPos, fontSize);
        return yPos;
    }

    void drawOutLinedText(Canvas canvas, String text, int xPos, int yPos, int textSize) {

        int color = ContextCompat.getColor(mContext, R.color.infoColor);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);
        paint.setTextSize(textSize);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth((int)(textSize/5+3));
        paint.setTypeface(mContext.getResources().getFont(R.font.nanumbarungothic));
        canvas.drawText(text, xPos, yPos, paint);

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(text, xPos, yPos, paint);
    }

    Bitmap buildSignatureMap() {
        Bitmap sigMap;
        File sigFile = new File(Environment.getExternalStorageDirectory(),"signature.png");
        if (sigFile.exists()) {
            sigMap = BitmapFactory.decodeFile(sigFile.toString(), null);
        }
        else
            sigMap = BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.signature);
        Bitmap newBitmap = Bitmap.createBitmap(sigMap.getWidth(), sigMap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawBitmap(sigMap, 0, 0, null);
        return newBitmap;
    }
}
