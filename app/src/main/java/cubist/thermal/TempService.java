package cubist.thermal;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;


import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by CubePenguin on 2017. 5. 8..
 */

public class TempService extends Service {

    public BatteryInfoReceiver mBatInfoReceiver;
    private long rxBytes = 0;
    private long txBytes = 0;
    private double batTemp = 0.0;
    public String FILENAME = null;
    public String FILEPATH = null;
    public volatile boolean running = true;
    FileOutputStream currFileStream = null;
    Thread thread;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBatInfoReceiver = new BatteryInfoReceiver();

        this.registerReceiver(this.mBatInfoReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        rxBytes = android.net.TrafficStats.getTotalRxBytes();
        txBytes = android.net.TrafficStats.getTotalTxBytes();
        batTemp = mBatInfoReceiver.get_temp();

        openLogFile();
        logging();
    }

    @Override
    public void onDestroy() {
        running = false;
        // SystemClock.sleep(1000);
        thread.interrupt();
        this.unregisterReceiver(this.mBatInfoReceiver);
        closeLogFile();
        super.onDestroy();
    }

    private void logging() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    // do sth
                    // rxBytes = android.net.TrafficStats.getTotalRxBytes();
                    // txBytes = android.net.TrafficStats.getTotalTxBytes();
                    // batTemp = mBatInfoReceiver.get_temp();
                    String[] temp = getCpuUsageStatistic();
                    String data = TextUtils.join(",", temp);
                    writeLogFile(data);
                }
            }
        });
        thread.start();
    }

    public void openLogFile() {
        try {
            final String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/Thermal/";
            final File dir = new File(dirPath);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.e("ALERT", "cannot create dir");
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
            String fDate = sdf.format(new Date());
            FILENAME = "thermal-log-" + fDate + ".csv";
            FILEPATH = dirPath + FILENAME;

            final File file = new File(dir, FILENAME);

            if(!file.exists()) {
                file.createNewFile();
            }

            currFileStream = new FileOutputStream(file);

            // ex) User 31%, System 10%, IOW 0%, IRQ 0%
            // ex) User 211 + Nice 0 + Sys 156 + Idle 1424 + IOW 0 + IRQ 7 + SIRQ 3 = 1801
            // ex)  PID PR CPU% S  #THR     VSS     RSS PCY UID      Name
            // ex) 3031  4   0% R     1   6000K   1424K  fg shell    top
            // cpuFreq, netTx, netRx, batTemp, cpuTemp
            currFileStream.write(("Time,User%,System%,IOW%,IRQ%," +
                    "User,Nice,Sys,Idle,IOW,IRQ,SIRQ,Sum7," +
                    "CPU%,#THR,VSS,RSS," +
                    "CPUfreq,netTx,netRx,batTemp,cpuTemp\n").getBytes());
            Toast.makeText(getApplicationContext(), "Log Start", Toast.LENGTH_SHORT).show();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeLogFile(String log) {
        try {
            if (currFileStream != null) {
                final String msg = log;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
                String fDate = sdf.format(new Date());
                getReadyToSend(fDate, log);
                currFileStream.write(String.format("%s,%s\n", fDate, log).getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int paramNum = 10;  // 21
    private ArrayList<String> serverData = new ArrayList<>();
    private String initialDateStr;
    private boolean isFirst = true;
    private void getReadyToSend(String fDate, String log) {
        if (isFirst) {
            initialDateStr = fDate;
            isFirst = false;
        }
        String avgedRow = avgOutData(fDate, log, 5000);  // Average out by 5 sec segment
        if (avgedRow != null) {
            serverData.add(avgedRow);
            if (serverData.size() == 13)
                serverData.remove(0);
            // Log.d("getReadyToSend", String.valueOf(avgedRow));
            // Log.d("getReadyToSend", String.valueOf(serverData.size()));
            // sendToServer("d");
            if (serverData.size() == 12) {
                String dataToSend = trimServerData(serverData);
                sendToServer(dataToSend);

            }
        }
    }

    private void sendToServer(String data) {
        Log.d("sendToServer", data);
        Intent intent = new Intent("GETDATA1");
        intent.putExtra("DATA1", data);
        sendBroadcast(intent);

        final String requestBody = data;
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="http://143.248.56.165:8000";

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        Log.d("sendToServer", response);
                        Intent intent = new Intent("GETDATA2");
                        intent.putExtra("DATA2", response);
                        sendBroadcast(intent);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("sendToServer", error.toString());
            }
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return requestBody == null ? null : requestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException uee) {
                    VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, "utf-8");
                    return null;
                }
            }
        };
        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private String trimServerData(ArrayList<String> serverData) {
        String result = "";
        result = result + "[[";
        for (String row : serverData) {
            result = result + row + ", ";
        }
        result = result + "]]";
        return result;
    }

    private long segNumber = -1;
    private float[] tempArray = new float[paramNum];
    private int cnt = 0;
    private String avgOutData(String fDate, String log, long avgTime) {
        String resultRow = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
        try {
            Date initialDate = sdf.parse(initialDateStr);
            Date newDate = sdf.parse(fDate);
            long tDifference = getDateDiff(initialDate, newDate, TimeUnit.MILLISECONDS);
            // Log.d("getReadyToSend", "Time Diff = " + String.valueOf(tDifference));
            // Log.d("getReadyToSend", log);
            long quotient = tDifference / avgTime;
            if (segNumber != -1 && quotient != segNumber) {
                for (int i=0; i < paramNum; i++) {
                    tempArray[i] = tempArray[i] / cnt;
                }
                segNumber = quotient;
                // Log.d("avgOutData", Arrays.toString(tempArray));
                // Log.d("avgOutData-count", String.valueOf(cnt));
                // clear out temp
                resultRow = Arrays.toString(tempArray);
                tempArray = new float[paramNum];
                cnt = 0;
                // Log.d("avgOutData", String.valueOf(segNumber));
            }
            // Log.d("avgOutData-temping", log);
            String[] ary = log.split(",");
            for (int i=0; i < paramNum; i++) {
                tempArray[i] = tempArray[i] + Float.parseFloat(ary[i]);
            }
            cnt++;
            if (segNumber == -1)
                segNumber = quotient;
        } catch(ParseException e) {
            e.printStackTrace();
        }
        return resultRow;
    }

    public static long getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
        long diffInMillies = date2.getTime() - date1.getTime();
        return timeUnit.convert(diffInMillies,TimeUnit.MILLISECONDS);
    }

    public void closeLogFile() {
        try {
            if (currFileStream != null) {
                currFileStream.close();
            }
            MediaScannerConnection.scanFile(getApplicationContext(),
                    new String[]{FILEPATH}, null, null);
            FILENAME = null;
            FILEPATH = null;
            currFileStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return String Array with many elements: user, system, idle and other cpu
     *         usage in percentage.
     */
    private String[] getCpuUsageStatistic() {

        String[] temp = executeTop();
        String tempString = temp[0];
        int size = 0;

        tempString = tempString.replaceAll(",", "");
        tempString = tempString.replaceAll("User", "");
        tempString = tempString.replaceAll("System", "");
        tempString = tempString.replaceAll("IOW", "");
        tempString = tempString.replaceAll("IRQ", "");
        tempString = tempString.replaceAll("%", "");
        for (int i = 0; i < 10; i++) {
            tempString = tempString.replaceAll("  ", " ");
        }
        tempString = tempString.trim();
        String[] myString = tempString.split(" ");
        size += myString.length;
        String[] cpu0 = new String[myString.length];
        for (int i = 0; i < myString.length; i++) {
            myString[i] = myString[i].trim();
            cpu0[i] = myString[i];
        }
        // ex) User 31%, System 10%, IOW 0%, IRQ 0%

        tempString = temp[1];
        tempString = tempString.replaceAll("\\+", "=");
        tempString = tempString.trim();
        myString = tempString.split("=");
        size += myString.length;
        String[] cpu1 = new String[myString.length];
        for (int i = 0; i < myString.length-1; i++) {
            myString[i] = myString[i].trim().split(" ")[1].trim();
            cpu1[i] = myString[i];
        }
        cpu1[myString.length-1] = myString[myString.length-1].trim();
        // ex) User 211 + Nice 0 + Sys 156 + Idle 1424 + IOW 0 + IRQ 7 + SIRQ 3 = 1801

        tempString = temp[2];
        size += 4;
        String[] cpu2 = new String[4];
        myString = tempString.split("%");
        tempString = myString[1];
        myString = myString[0].split(" ");
        cpu2[0] = myString[myString.length-1].trim();
        myString = tempString.split("K");
        cpu2[3] = myString[1].trim();
        tempString = myString[0];
        for (int i = 0; i < 10; i++) {
            tempString = tempString.replaceAll("  ", " ");
        }
        myString = tempString.split(" ");
        cpu2[2] = myString[myString.length-1].trim();
        cpu2[1] = myString[myString.length-2].trim();
        // Log.i("cpu1", Arrays.toString(cpu1));

        // ex)  PID PR CPU% S  #THR     VSS     RSS PCY UID      Name
        // ex) 3031  4   0% R     1   6000K   1424K  fg shell    top
        // Log.i("cpu2", Arrays.toString(cpu2));

        size += 5;
        String[] data = new String[size];
        int idx = 0;
        for (;idx < cpu0.length; idx++) {
            data[idx] = cpu0[idx];
        }
        for (int t = idx; idx < t + cpu1.length; idx++) {
            data[idx] = cpu1[idx-t];
        }
        for (int t = idx; idx < t + cpu2.length; idx++) {
            data[idx] = cpu2[idx-t];
        }
        try {
            // Log.i("cpuFreq", Integer.toString(SystemUtils.getCPUFrequencyCurrent()));
            data[idx++] = Integer.toString(SystemUtils.getCPUFrequencyCurrent());
        } catch (SystemUtils.SystemUtilsException e) {
            e.printStackTrace();
        }
        Long txTemp = android.net.TrafficStats.getTotalTxBytes();
        Long rxTemp = android.net.TrafficStats.getTotalRxBytes();
        data[idx++] = Long.toString(txTemp - txBytes);
        data[idx++] = Long.toString(rxTemp - rxBytes);
        txBytes = txTemp;
        rxBytes = rxTemp;
        data[idx++] = Double.toString(mBatInfoReceiver.get_temp());
        try {
            data[idx] = Integer.toString(SystemUtils.getCPUTemperatureCurrent());
        } catch (SystemUtils.SystemUtilsException e) {
            e.printStackTrace();
        }
        return data;
    }

    private String[] executeTop() {
        java.lang.Process p = null;
        BufferedReader in = null;
        String[] returnString = new String[3];
        try {
            p = Runtime.getRuntime().exec("top -n 1 -m 1 -s cpu");
            in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while (returnString[0] == null || returnString[0].contentEquals("")) {
                returnString[0] = in.readLine();
                // Log.d("net", returnString);
            }
            while (returnString[1] == null || returnString[1].contentEquals("")) {
                returnString[1] = in.readLine();
            }
            while (returnString[2] == null || returnString[2].contentEquals("")) {
                returnString[2] = in.readLine();
            }
            returnString[2] = in.readLine();
        } catch (IOException e) {
            Log.e("executeTop", "error in getting first line of top");
            e.printStackTrace();
        } finally {
            try {
                assert in != null;
                in.close();
                p.destroy();
            } catch (IOException e) {
                Log.e("executeTop",
                        "error in closing and destroying top process");
                e.printStackTrace();
            }
        }
        return returnString;
    }
}
