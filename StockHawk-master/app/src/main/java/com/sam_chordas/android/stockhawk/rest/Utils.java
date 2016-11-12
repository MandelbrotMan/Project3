package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import android.util.Log;
import android.widget.Toast;

import com.sam_chordas.android.stockhawk.data.StockContract;
import com.sam_chordas.android.stockhawk.data.StockDbHelper;
import com.sam_chordas.android.stockhawk.data.StockProvider;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

  private static String LOG_TAG = Utils.class.getSimpleName();

  public static boolean showPercent = true;
  public static boolean valid = false;

  public static ArrayList quoteJsonToContentVals(String JSON, Context context){
    ArrayList<ContentValues> batchOperations = new ArrayList<>();
    JSONObject jsonObject = null;
    JSONArray resultsArray = null;
    Log.v("Json Url: ", JSON);
    try{
      jsonObject = new JSONObject(JSON);

      //single entry
      if (jsonObject != null && jsonObject.length() != 0){
        jsonObject = jsonObject.getJSONObject("query");
        int count = Integer.parseInt(jsonObject.getString("count"));
        if (count == 1){
          jsonObject = jsonObject.getJSONObject("results")
                  .getJSONObject("quote");
          if(!TestOperationSingle(jsonObject).equals("null")){
            batchOperations.add(buildBatchOperation(jsonObject));
            valid = true;
          }else{
            valid = false;
          }

        }
        //Searching for all values in db
        else{
          resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

          if (resultsArray != null && resultsArray.length() != 0){
            for (int i = 0; i < resultsArray.length(); i++){
              try {
                jsonObject = resultsArray.getJSONObject(i);
                batchOperations.add(buildBatchOperation(jsonObject));
              }catch (JSONException e){
                Log.v("stock does not exist", ":error" );
              }
            }
          } else {
            Log.v("bad entry: ", "The following does not exist");

          }
        }
      }
    } catch (JSONException e){
      Log.e(LOG_TAG, "String to JSON failed: " + e);
    }
    return batchOperations;
  }


  public static String truncateBidPrice(String bidPrice){
    bidPrice = String.format("%.2f", Float.parseFloat(bidPrice));
    return bidPrice;
  }

  public static String truncateChange(String change, boolean isPercentChange){
    String weight = change.substring(0,1);
    String ampersand = "";
    if (isPercentChange){
      ampersand = change.substring(change.length() - 1, change.length());
      change = change.substring(0, change.length() - 1);
    }else {
      change = change.substring(1, change.length());
      double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
      change = String.format("%.2f", round);
      StringBuffer changeBuffer = new StringBuffer(change);
      changeBuffer.insert(0, weight);
      changeBuffer.append(ampersand);
      change = changeBuffer.toString();
    }
    return change;
  }

  public static ContentValues buildBatchOperation(JSONObject jsonObject){
    ContentValues values = new ContentValues();
    try {

      String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());

      String change = jsonObject.getString("Change");
      values.put(StockContract.StockEntry.COLUMN_STOCK_SYMBOL, jsonObject.getString("symbol"));
      values.put(StockContract.StockEntry.COLUMN_TIME, currentDateTimeString);
      values.put(StockContract.StockEntry.COLUMN_BIDPRICE, truncateBidPrice(jsonObject.getString("Bid")));
      values.put(StockContract.StockEntry.COLUMN_PERCENT_CHANGE, jsonObject.getString("ChangeinPercent"));
      values.put(StockContract.StockEntry.COLUMN_CHANGE,truncateChange(change, false));
      values.put(StockContract.StockEntry.COLUMN_ISCURRENT, 1);

      if (change.charAt(0) == '-'){
        values.put(StockContract.StockEntry.COLUMN_ISUP, 0);
      }else{
        values.put(StockContract.StockEntry.COLUMN_ISUP, 1);
      }

    } catch (JSONException e){
      e.printStackTrace();
    }
    return values;
  }
  public static String TestOperationSingle(JSONObject jsonObject){
    String change = "NoData";
    try {
      change = jsonObject.getString("Change");
    } catch (JSONException e){
      e.printStackTrace();
    }
    return change;
  }


}
