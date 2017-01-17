package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;


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

public class DetailActivity extends AppCompatActivity {
    private static final double MAX_Y = 100;
    private LineChartView chartView;
    List<LineChartView.Point> pricePoints = new ArrayList<>();
    List<LineChartView.Point> changePoints = new ArrayList<>();
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


            chartView = (LineChartView) findViewById(R.id.chart_view);
            chartView.setManualMinY(0);

            View nextButton = findViewById(R.id.next_data);
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<LineChartView.Point> NewPricePoints = new ArrayList<>();
                    for(int i = 0; i < 5; ++i){
                        NewPricePoints.add(pricePoints.get(i));
                    }
                    chartView.setPoints(NewPricePoints);

                }
            });
        }
    }

    private void nextChartData(String symbol, boolean isPricePoint) {

        for (int i = 0; i < 5; i++) {
            int y = (int) (Math.random() * MAX_Y);
            pricePoints.add(new LineChartView.Point(i, y));
        }
//        chartView.setPoints(pricePoints);
    }
    private void createPriceGraph(Cursor cursor){
        //All points for the graph will be created in this function but organized by the changeDate Function to
        //minimize access of db.

        mCursor = getContentResolver().query(StockContract.StockEntry.CONTENT_URI,
                mProjection, StockContract.StockEntry.COLUMN_STOCK_SYMBOL + "= ?",
                new String[]{mSymbol}, null);

        if(mCursor.moveToFirst()){
            while(mCursor.moveToNext()){
                double numberValue;
                String price = mCursor.getString(1);
                String dateValue = mCursor.getString(3);// 1 is the array location for the column of prices
                Log.v("Date", dateValue);
                /* If the value cannot be converted to a numeric value, the program will skip that date
                by not entering it to the array
                 */
                try {
                     numberValue = Double.parseDouble(price);

                     Date date = DateFormat.getDateTimeInstance().parse(dateValue);

                     Calendar cal = GregorianCalendar.getInstance();
                     cal.setTime(date);
                     int day = cal.get(Calendar.DAY_OF_MONTH);
                     int minute = cal.get(Calendar.MINUTE);
                    Log.v("minute", Integer.toString(minute));
                     months.add(cal.get(Calendar.MONTH));
                   pricePoints.add(new LineChartView.Point(minute, (long) numberValue));
                }catch (NumberFormatException e){
                    Log.v("Number Exception", "Confirmed");
                } catch (ParseException e) {
                    Log.v("Parse Exception", "Confirmed");
                }
                mCursor.moveToNext();
            }
            mCursor.close();
        }
    }


}