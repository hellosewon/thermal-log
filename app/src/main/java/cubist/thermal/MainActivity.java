package cubist.thermal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    // public BatteryInfoReceiver mBatInfoReceiver;

    private TextView mDataTextView;
    private TextView mResultTextView;
    private BroadcastReceiver mMessageReceiver1 = new BroadcastReceiver() {
        @Override

        public void onReceive(Context context, Intent intent) {
            String str1 = intent.getStringExtra("DATA1");
            mDataTextView.setText(str1);
        }

    };

    private BroadcastReceiver mMessageReceiver2 = new BroadcastReceiver() {
        @Override

        public void onReceive(Context context, Intent intent) {
            String str2 = intent.getStringExtra("DATA2");
            mResultTextView.append("\n" + str2);
        }

    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                Log.i("action", "temp");
                //BackgroundTask bgTask = new BackgroundTask();
                //bgTask.execute(mBatInfoReceiver.get_temp());
                // Log.i("batTemp", Double.toString(mBatInfoReceiver.get_temp()));
            }
        });

        /*
        mBatInfoReceiver = new BatteryInfoReceiver();

        this.registerReceiver(this.mBatInfoReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        */

        Button btn_start = (Button) findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        TempService.class);
                startService(intent);
                Log.d("service", "start");
            }
        });

        Button btn_stop = (Button) findViewById(R.id.btn_stop);
        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),
                        TempService.class);
                stopService(intent);
                Log.d("service", "stop");
            }
        });

        // Added for Server Result Logging
        mDataTextView = (TextView) findViewById(R.id.txt_data);
        mResultTextView = (TextView) findViewById(R.id.txt_result);
        registerReceiver( mMessageReceiver1, new IntentFilter("GETDATA1"));
        registerReceiver( mMessageReceiver2, new IntentFilter("GETDATA2"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
