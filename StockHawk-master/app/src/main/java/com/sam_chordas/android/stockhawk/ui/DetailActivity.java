package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.StockContract;

import org.hogel.android.linechartview.LineChartView;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class DetailActivity  extends DemoBase implements SeekBar.OnSeekBarChangeListener,
        OnChartGestureListener, OnChartValueSelectedListener {
    private static final double MAX_Y = 100;
    private LineChart mChartView;
    private SeekBar mBarSetFrequency, mBarRangeScale;
    private TextView mFrequencyText, mDateRangeText;
    List<PointF> pricePoints = new ArrayList<>();
    List<PointF> changePoints = new ArrayList<>();
    List<Integer> months = new ArrayList<>(); //Every entry will contain the #value of the month

    String[] mProjection = {StockContract.StockEntry.COLUMN_BIDPRICE, StockContract.StockEntry.COLUMN_CHANGE,
            StockContract.StockEntry.COLUMN_PERCENT_CHANGE, StockContract.StockEntry.COLUMN_TIME};
    String mSymbol;
    Cursor mCursor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_detail);


        Intent recieved = getIntent();
        if (recieved != null) {
           mSymbol = recieved.getStringExtra("Stock_id");


            createPriceGraph(mCursor);


            mChartView = (LineChart) findViewById(R.id.chart_view);
            mBarSetFrequency = (SeekBar) findViewById(R.id.seekBar1);
            mBarRangeScale = (SeekBar) findViewById(R.id.seekBar2);
            mDateRangeText = (TextView) findViewById(R.id.textViewDateRange);
            mFrequencyText = (TextView) findViewById(R.id.textViewFrequency);

            mBarRangeScale.setMax(4);
            mBarRangeScale.setProgress(1);
            mBarRangeScale.setMax(60);
            mBarSetFrequency.setProgress(6);

            mChartView.setOnChartGestureListener(this);
            mChartView.setOnChartValueSelectedListener(this);
            mChartView.setDrawGridBackground(false);





            View nextButton = findViewById(R.id.next_data);
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {


                }
            });
        }
    }

    private void nextChartData(String symbol, boolean isPricePoint) {

        for (int i = 0; i < 5; i++) {
            int y = (int) (Math.random() * MAX_Y);
            pricePoints.add(new PointF(i, y));
        }
//        chartView.setPoints(pricePoints);
    }
    private void createPriceGraph(Cursor cursor){
        //All points for the graph will be created in this function but organized by the changeDate Function to
        //minimize access of db.

        mCursor = getContentResolver().query(StockContract.StockEntry.CONTENT_URI,
                mProjection, StockContract.StockEntry.COLUMN_STOCK_SYMBOL + "= ?",
                new String[]{mSymbol}, null);

        float min = -1;

        if(mCursor.moveToFirst()){
            while(mCursor.moveToNext()){
                float numberValue;
                String price = mCursor.getString(1);
                String dateValue = mCursor.getString(3);// 1 is the array location for the column of prices
                Log.v("Date", dateValue);
                /* If the value cannot be converted to a numeric value, the program will skip that date
                by not entering it to the array
                 */
                try {
                     numberValue = Float.parseFloat(price);
                    if(min > numberValue){
                        min = numberValue - 1;
                    }
                     Date date = DateFormat.getDateTimeInstance().parse(dateValue);

                     Calendar cal = GregorianCalendar.getInstance();
                     cal.setTime(date);
                     int day = cal.get(Calendar.DAY_OF_MONTH);
                     int minute = cal.get(Calendar.MINUTE);
                    Log.v("minute", Integer.toString(minute));
                     months.add(cal.get(Calendar.MONTH));
                   pricePoints.add(new PointF(minute, numberValue));
                }catch (NumberFormatException e){
                    Log.v("Number Exception", "Confirmed");
                } catch (ParseException e) {
                    Log.v("Parse Exception", "Confirmed");
                }
                mCursor.moveToNext();
            }
            mCursor.close();
        }
        mChartView.setMinimumHeight(-10);
    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
        Log.i("Gesture", "START, x: " + me.getX() + ", y: " + me.getY());
    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
        Log.i("Gesture", "END, lastGesture: " + lastPerformedGesture);

        // un-highlight values after the gesture is finished and no single-tap
        if(lastPerformedGesture != ChartTouchListener.ChartGesture.SINGLE_TAP)
            mChartView.highlightValues(null); // or highlightTouch(null) for callback to onNothingSelected(...)
    }

    @Override
    public void onChartLongPressed(MotionEvent me) {
        Log.i("LongPress", "Chart longpressed.");
    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {
        Log.i("DoubleTap", "Chart double-tapped.");
    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {
        Log.i("SingleTap", "Chart single-tapped.");
    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
        Log.i("Fling", "Chart flinged. VeloX: " + velocityX + ", VeloY: " + velocityY);
    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
        Log.i("Scale / Zoom", "ScaleX: " + scaleX + ", ScaleY: " + scaleY);
    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        Log.i("Translate / Move", "dX: " + dX + ", dY: " + dY);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        Log.i("Entry selected", e.toString());
        Log.i("LOWHIGH", "low: " + mChartView.getLowestVisibleX() + ", high: " + mChartView.getHighestVisibleX());
        Log.i("MIN MAX", "xmin: " + mChartView.getXChartMin() + ", xmax: " + mChartView.getXChartMax() + ", ymin: " + mChartView.getYChartMin() + ", ymax: " + mChartView.getYChartMax());
    }

    @Override
    public void onNothingSelected() {
        Log.i("Nothing selected", "Nothing selected.");
    }
}