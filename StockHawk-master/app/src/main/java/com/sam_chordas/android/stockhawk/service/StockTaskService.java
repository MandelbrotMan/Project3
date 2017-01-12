package com.sam_chordas.android.stockhawk.service;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.data.StockContract;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService {


  private static final String[] NOTIFY_STOCK_PROJECTION = new String[]{
          StockContract.StockEntry.COLUMN_STOCK_SYMBOL,
          StockContract.StockEntry.COLUMN_TIME,
          StockContract.StockEntry.COLUMN_PERCENT_CHANGE,
          StockContract.StockEntry.COLUMN_CHANGE,
          StockContract.StockEntry.COLUMN_BIDPRICE,
          StockContract.StockEntry.COLUMN_ISUP,
          StockContract.StockEntry.COLUMN_ISCURRENT,
  };

  // these indices must match the projection
  private static final int INDEX_STOCK_SYMBOL = 0;
  private static final int INDEX_COLUMN_TIME = 1;
  private static final int INDEX_PERCENT_CHANGE = 2;
  private static final int INDEX_CHANGE = 3;
  private static final int INDEX_BIDPRICE = 4;
  private static final int INDEX_ISUP = 5;
  private static final int INDEX_ISCURRENT = 6;

  private OkHttpClient client = new OkHttpClient();
  private Context mContext;
  private String mStoredSymbols = "";
  private boolean isUpdate;
  public boolean valid;

  public StockTaskService() {

  }

  public StockTaskService(Context context) {
    mContext = context;
  }

  String fetchData(String url) throws IOException {
    Request request = new Request.Builder()
            .url(url)
            .build();

    Response response = client.newCall(request).execute();
    return response.body().string();
  }

  @Override
  public int onRunTask(TaskParams params) {
    Cursor initQueryCursor = null;
    if (mContext == null) {
      mContext = this;
    }
    StringBuilder urlStringBuilder = new StringBuilder();
    try {
      // Base URL for the Yahoo query
      urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
      urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
              + "in (", "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    if (params.getTag().equals("init") || params.getTag().equals("periodic")) {
      Log.v("Periodic Sync called", "Tested");
      isUpdate = true;
      String Selection = "(" + StockContract.StockEntry.COLUMN_STOCK_SYMBOL + " NOT NULL) GROUP BY (" + StockContract.StockEntry.COLUMN_STOCK_SYMBOL + ")";
      initQueryCursor = mContext.getContentResolver().query(StockContract.StockEntry.CONTENT_URI,
              new String[]{StockContract.StockEntry.COLUMN_STOCK_SYMBOL}, Selection,
              null, null);
      if(initQueryCursor.moveToFirst() == false){
        initQueryCursor = null;
      }
      if (initQueryCursor == null) {
        // Init task. Populates DB with quotes for the symbols seen below
        try {
          urlStringBuilder.append(
                  URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      } else if (initQueryCursor != null) {
        //DatabaseUtils.dumpCursor(initQueryCursor);
        initQueryCursor.moveToFirst();
        //initQueryCursor.moveToNext();
        for (int i = 0; i < initQueryCursor.getCount()-1; i++) {
          String temp = initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol"));

            mStoredSymbols +=  "\"" +
                    temp + "\",";
          initQueryCursor.moveToNext();
        }
        //The last item String should be treated with a ) instead of a ,
        String temp = initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol"));
        mStoredSymbols +=  "\"" +
                temp + "\")";

        Log.v("To be encoded, ", mStoredSymbols);
        try {
          urlStringBuilder.append(URLEncoder.encode(mStoredSymbols, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
      }
    } else if (params.getTag().equals("add")) {
      isUpdate = false;
      // get symbol from params.getExtra and build query
      String stockInput = params.getExtras().getString("symbol");
      try {
        urlStringBuilder.append(URLEncoder.encode("\"" + stockInput + "\")", "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }
    }
    // finalize the URL for the API query.
    urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
            + "org%2Falltableswithkeys&callback=");

    String urlString;
    String getResponse;
    int result = GcmNetworkManager.RESULT_FAILURE;

    if (urlStringBuilder != null) {
      urlString = urlStringBuilder.toString();
      Log.v("string for json: ", urlString);
      try {
        getResponse = fetchData(urlString);

        result = GcmNetworkManager.RESULT_SUCCESS;
        ContentValues contentValues = new ContentValues();
        // update ISCURRENT to 0 (false) so new data is current
        if (isUpdate) {
          contentValues.put(QuoteColumns.ISCURRENT, 0);

          mContext.getContentResolver().update(StockContract.StockEntry.CONTENT_URI, contentValues,
                  null, null);

        }
        ArrayList<ContentValues> valuesArrayList = Utils.quoteJsonToContentVals(getResponse, mContext);
        ContentValues fromJson[] = new ContentValues[valuesArrayList.size()];
        Log.v("Size of Return :", Integer.toString(valuesArrayList.size()));
        fromJson = valuesArrayList.toArray(fromJson);

        mContext.getContentResolver().bulkInsert(StockContract.StockEntry.CONTENT_URI, fromJson
        );
        valid = Utils.valid;
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    if(initQueryCursor != null) {
      initQueryCursor.close();
    }

    return result;
  }
}
