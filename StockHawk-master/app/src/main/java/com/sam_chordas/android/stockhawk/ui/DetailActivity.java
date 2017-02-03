package com.sam_chordas.android.stockhawk.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.SeekBar;
import android.widget.TextView;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.Utils;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.sam_chordas.android.stockhawk.MyMarkerView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.StockContract;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;


import java.text.DateFormat;
import java.text.ParseException;
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
    private float min, max;
    ArrayList<Entry> mPricePoints = new ArrayList<Entry>();
    List<PointF> changePoints = new ArrayList<>();
    List<Integer> months = new ArrayList<>();//Every entry will contain the #value of the month
    ConnectivityManager cm;
    String[] mProjection = {StockContract.StockEntry.COLUMN_BIDPRICE, StockContract.StockEntry.COLUMN_CHANGE,
            StockContract.StockEntry.COLUMN_PERCENT_CHANGE, StockContract.StockEntry.COLUMN_TIME};
    String mSymbol;
    Cursor mCursor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_detail);

        cm = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        Intent recieved = getIntent();
        if (recieved != null) {
           mSymbol = recieved.getStringExtra("Stock_id");



            mChartView = (LineChart) findViewById(R.id.chart1);
            mBarSetFrequency = (SeekBar) findViewById(R.id.FrequencySeekBar);
            mBarRangeScale = (SeekBar) findViewById(R.id.TimeSeekBar);
            mDateRangeText = (TextView) findViewById(R.id.textViewDateRange);
            mFrequencyText = (TextView) findViewById(R.id.textViewFrequency);

            mBarRangeScale.setMax(4);
            mBarRangeScale.setProgress(1);
            mBarSetFrequency.setMax(60);
            mBarSetFrequency.setProgress(6);
            mFrequencyText.setText("" + (mBarSetFrequency.getProgress() + 1));
            mDateRangeText.setText("" + (mBarRangeScale.getProgress()));
            createPriceGraph(mCursor);

            mChartView.setOnChartGestureListener(this);
            mChartView.setOnChartValueSelectedListener(this);
            mChartView.setDrawGridBackground(false);
            mBarRangeScale.setOnSeekBarChangeListener(this);
            mBarSetFrequency.setOnSeekBarChangeListener(this);

            mChartView.getDescription().setEnabled(false);

            // enaViewble touch gestures
            mChartView.setTouchEnabled(true);

            // enaViewble scaling and dragging
            mChartView.setDragEnabled(true);
            mChartView.setScaleEnabled(true);
            // mChart.setScaleXEnabled(true);
            // mChart.setScaleYEnabled(true);

            // if disabled, scaling can be done on x- and y-axis separately
            mChartView.setPinchZoom(true);

            // set an alternative background color
            // mChart.setBackgroundColor(Color.GRAY);

            // create a custom MarkerView (extend MarkerView) and specify the layout
            // to use for it
            MyMarkerView mv = new MyMarkerView(this, R.layout.custom_focus_box);
            mv.setChartView(mChartView); // For bounds control
            mChartView.setMarker(mv); // Set the marker to the chart





        }
    }

    private void sechdulePerioidicForStock(){
        if(!mSymbol.isEmpty()){
            Intent mServiceIntent = new Intent(this, StockIntentService.class);
            mServiceIntent.putExtra("tag", "add");
            mServiceIntent.putExtra("symbol", mSymbol);
            startService(mServiceIntent);
            boolean isConnected = false;
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            isConnected = activeNetwork != null &&
                    activeNetwork.isConnectedOrConnecting();
            if (isConnected){
                long period = 600L;
                long flex = 10L;
                String periodicTag = "periodic";

                // create a periodic task to pull stocks once every hour after the app has been opened. This
                // is so Widget data stays up to date.
                PeriodicTask periodicTask = new PeriodicTask.Builder()
                        .setService(StockTaskService.class)
                        .setPeriod(period)
                        .setFlex(flex)
                        .setTag(periodicTag)
                        .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
                        .setRequiresCharging(false)
                        .build();
                // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
                // are updated.
                GcmNetworkManager.getInstance(this).schedule(periodicTask);


            }
        }

    }
    private void createPriceGraph(Cursor cursor){
        //All points for the graph will be created in this function but organized by the changeDate Function to
        //minimize access of db.

        mCursor = getContentResolver().query(StockContract.StockEntry.CONTENT_URI,
                mProjection, StockContract.StockEntry.COLUMN_STOCK_SYMBOL + "= ?",
                new String[]{mSymbol}, null);



        if(mCursor.moveToFirst()){
            mPricePoints.clear();
            while(mCursor.moveToNext()){
                float numberValue;
                String price = mCursor.getString(1);
                String dateValue = mCursor.getString(3);// 1 is the array location for the column of prices
                /* If the value cannot be converted to a numeric value, the program will skip that date
                by not entering it to the array
                 */
                min = 0;
                max = 1;
                try {
                     numberValue = Float.parseFloat(price);

                    //For determining upper and lower limits
                    if(min > numberValue){
                        min = numberValue - 5;
                    }
                    if(max < numberValue){
                       max = numberValue + 5;
                    }
                     Date date = DateFormat.getDateTimeInstance().parse(dateValue);

                     Calendar cal = GregorianCalendar.getInstance();
                     cal.setTime(date);
                     int day = cal.get(Calendar.DAY_OF_MONTH);
                     int minute = cal.get(Calendar.MINUTE);
                     months.add(cal.get(Calendar.MONTH));
                   mPricePoints.add(new Entry(minute, numberValue));
                }catch (NumberFormatException e){
                    Log.v("Number Exception", "Confirmed");
                } catch (ParseException e) {
                    Log.v("Parse Exception", "Confirmed");
                }
                mCursor.moveToNext();
            }
            mCursor.close();

        }
        LineDataSet set1;

        if (mChartView.getData() != null &&
                mChartView.getData().getDataSetCount() > 0) {
            set1 = (LineDataSet)mChartView.getData().getDataSetByIndex(0);
            set1.setValues(mPricePoints);
            mChartView.getData().notifyDataChanged();
            mChartView.notifyDataSetChanged();
        } else {
            // create a dataset and give it a type
            set1 = new LineDataSet(mPricePoints, "DataSet 1");

            // set the line to be drawn like this "- - - - - -"
            set1.enableDashedLine(10f, 5f, 0f);
            set1.enableDashedHighlightLine(10f, 5f, 0f);
            set1.setColor(Color.BLACK);
            set1.setCircleColor(Color.BLACK);
            set1.setLineWidth(1f);
            set1.setCircleRadius(3f);
            set1.setDrawCircleHole(false);
            set1.setValueTextSize(9f);
            set1.setDrawFilled(true);
            set1.setFormLineWidth(1f);
            set1.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
            set1.setFormSize(15.f);

            if (Utils.getSDKInt() >= 18) {
                // fill drawable only supported on api level 18 and above
                Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_red);
                set1.setFillDrawable(drawable);
            }
            else {
                set1.setFillColor(Color.BLACK);
            }

            ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(set1); // add the datasets

            // create a data object with the datasets
            LineData data = new LineData(dataSets);

            // set data
            mChartView.setData(data);
        }

        IAxisValueFormatter formatter = new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return Float.toString(value);
            }
        };

        XAxis xAxis = mChartView.getXAxis();
        xAxis.enableGridDashedLine(10f, 10f, 0f);


        //Typeface tf = Typeface.createFromAsset(getAssets(), "Roboto-Regular.ttf");

        LimitLine ll1 = new LimitLine(max, "Upper");
        ll1.setLineWidth(4f);
        ll1.enableDashedLine(10f, 10f, 0f);
        ll1.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        ll1.setTextSize(10f);
       // ll1.setTypeface(tf);

        LimitLine ll2 = new LimitLine(min, "Lower");
        ll2.setLineWidth(4f);
        ll2.enableDashedLine(10f, 10f, 0f);
        ll2.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        ll2.setTextSize(10f);
       // ll2.setTypeface(tf);

        YAxis leftAxis = mChartView.getAxisLeft();
        leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        leftAxis.addLimitLine(ll1);
        leftAxis.addLimitLine(ll2);
        leftAxis.setAxisMaximum(max+10);
        leftAxis.setAxisMinimum(min-5);
        //leftAxis.setYOffset(20f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);

        // limit lines are drawn behind data (and not on top)
        leftAxis.setDrawLimitLinesBehindData(true);

        mChartView.getAxisRight().setEnabled(false);

        leftAxis.setValueFormatter(formatter);
        xAxis.setValueFormatter(formatter);

        mChartView.animateX(2500);

        // get the legend (only possible after setting data)
        Legend l = mChartView.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);

        // // dont forget to refresh the drawing
        // mChart.invalidate();


    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        if(mBarSetFrequency.isPressed()){

        }
        mFrequencyText.setText("" + (mBarSetFrequency.getProgress() + 1));
        mDateRangeText.setText("" + (mBarRangeScale.getProgress()));

        //setData(mSeekBarX.getProgress() + 1, mSeekBarY.getProgress());

        // redraw
       // mChart.invalidate();
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