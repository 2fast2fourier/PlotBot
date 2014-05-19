package net.fastfourier.plotbot.plotbot;

    import android.content.Context;
    import android.graphics.Bitmap;
    import android.graphics.Canvas;
    import android.graphics.Color;
    import android.graphics.Paint;
    import android.graphics.Point;
    import android.graphics.Rect;
    import android.os.Environment;
    import android.util.AttributeSet;
    import android.util.Log;
    import android.view.MotionEvent;
    import android.view.View;

    import org.json.JSONArray;
    import org.json.JSONException;
    import org.json.JSONObject;

    import java.io.File;
    import java.io.FileNotFoundException;
    import java.io.FileOutputStream;
    import java.io.IOException;
    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.List;

/**
 * Created by matthewshepard on 4/22/14.
 */
public class DrawView extends View {
    public static final int PLOT_SIZE = 2000;
    public static final float POINT_MULITPLIER = 4f;

    private Bitmap screenshotData;
    private Rect sourceRect = new Rect(0, 0, 0, 0), destRect = new Rect(0, 0, 0, 0);
    private Paint highlight;
    private Canvas overlay;
    private int startX, endX, startY, endY;
    private float scale;

    private boolean hasDrawing = false;

    private float lastX, lastY;

    private ArrayList<Point[]> points = new ArrayList<Point[]>();
    private ArrayList<Point> currentLine = new ArrayList<Point>();

    public DrawView(Context context) {
        super(context);
        init();
    }

    public DrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DrawView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        highlight = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        highlight.setARGB(255, 0, 0, 0);
        highlight.setStyle(Paint.Style.STROKE);
        highlight.setStrokeWidth(4);
        screenshotData = Bitmap.createBitmap(PLOT_SIZE,PLOT_SIZE, Bitmap.Config.ARGB_8888);
        screenshotData.eraseColor(Color.WHITE);
        overlay = new Canvas(screenshotData);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (screenshotData != null) {
            int eventX = (int) event.getX(), eventY = (int) event.getY();
            float newX = (event.getX() - startX) / scale, newY = (event.getY() - startY) / scale;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = newX;
                    lastY = newY;
                    currentLine.clear();
                    if(destRect.contains(eventX, eventY)){
                        currentLine.add(new Point((int) (newX*POINT_MULITPLIER), (int) (newY*POINT_MULITPLIER)));
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (lastX < 0 || lastY < 0) {
                        lastX = newX;
                        lastY = newY;
                    }
                    if(destRect.contains(eventX, eventY)){
                        overlay.drawLine(lastX, lastY, newX, newY, highlight);
                        currentLine.add(new Point((int) (newX*POINT_MULITPLIER), (int) (newY*POINT_MULITPLIER)));
                    }
                    lastX = newX;
                    lastY = newY;
                    hasDrawing = true;
                    break;
                case MotionEvent.ACTION_UP:
                    if(destRect.contains(eventX, eventY)){
                        overlay.drawLine(lastX, lastY, newX, newY, highlight);
                        currentLine.add(new Point((int) (newX*POINT_MULITPLIER), (int) (newY*POINT_MULITPLIER)));
                    }
                    if(currentLine.size() > 1){
                        points.add(currentLine.toArray(new Point[currentLine.size()]));
                    }
                    currentLine.clear();
                    lastX = -1;
                    lastY = -1;
                    break;
            }
            postInvalidateOnAnimation();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRGB(0, 0, 0);
        if (screenshotData != null) {
            canvas.drawBitmap(screenshotData, sourceRect, destRect, null);
        }
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            updateBounds();
        }
    }

    private void updateBounds() {
        if (screenshotData == null) {
            return;
        }
        float w = getWidth(), h = getHeight();
        int bw = screenshotData.getWidth(), bh = screenshotData.getHeight();
        float scaleH = h / bh, scaleW = w / bw;
        if (scaleH < scaleW) {
            startX = (int) (w * 0.5f - scaleH * bw * 0.5f);
            endX = (int) (w - startX);
            startY = 0;
            endY = getHeight();
            scale = scaleH;
        } else {
            startX = 0;
            endX = getWidth();
            startY = (int) (h * 0.5f - scaleW * bh * 0.5f);
            endY = (int) (h - startY);
            scale = scaleW;
        }

        sourceRect.set(0, 0, bw, bh);
        destRect.set(startX, startY, endX, endY);
    }

    public void clearOverlay() {
        points.clear();
        currentLine.clear();
        screenshotData.eraseColor(Color.WHITE);
        invalidate();
    }

    public Point[][] getLineData(){
        return points.toArray(new Point[points.size()][]);
    }

    public JSONArray getLineJSON(){
        try{
            JSONArray json = new JSONArray();
            for(Point[] line : points){
                if(line.length > 1){
                    JSONObject lineJson = new JSONObject();
                    int[] x = new int[line.length];
                    int[] y = new int[line.length];
                    for(int ix=0;ix<line.length;ix++){
                        x[ix] = line[ix].x;
                        y[ix] = line[ix].y;
                    }
                    lineJson.put("x", new JSONArray(x));
                    lineJson.put("y", new JSONArray(y));
                    json.put(lineJson);
                }
            }
            return json;
        }catch (JSONException je){
            je.printStackTrace();
            return null;
        }
    }

    public String saveImage(Context context) {
        if (screenshotData != null) {
            File screenShotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File output = new File(screenShotDir, "image-" + (System.currentTimeMillis() / 1000) + ".png");
            try {
                FileOutputStream out = new FileOutputStream(output);
                screenshotData.compress(Bitmap.CompressFormat.PNG, 100, out);
                String screenshotFile = output.toString();
                out.close();
                Log.d("AnnotationView", "Drawing saved to: " + screenshotFile);
                return screenshotFile;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public boolean hasDrawing() {
        return hasDrawing;
    }
}