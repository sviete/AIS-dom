package pl.sviete.dom.connhist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;


import pl.sviete.dom.AisCoreUtils;
import pl.sviete.dom.BrowserActivityNative;
import pl.sviete.dom.Config;
import pl.sviete.dom.R;
import pl.sviete.dom.WatchScreenActivity;

public class AisConnectionsHistoryActivity extends Activity {
    private static final String TAG = "AisConnectionsHistoryActivity";
    private ListView mConnectionsListView;
    private static ArrayList<AisConnectionHistHolder> mConnectionsList;
    private AisConnectionHistAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume(){
        super.onResume();
        setContentView(R.layout.connections_history_list);

        loadConnectionsToList();
        mConnectionsListView = (ListView) findViewById((R.id.connections_history_list));

        // Construct the data source
        loadConnectionsToList();
        // Create the adapter to convert the array to views
        mAdapter = new AisConnectionHistAdapter(this, mConnectionsList);

        // Attach the adapter to a ListView
        mConnectionsListView.setAdapter(mAdapter);

        mConnectionsListView.setLongClickable(true);

    }


    /**
     * load connections to list
     */
    private void loadConnectionsToList() {
        try {
            mConnectionsList =  new ArrayList<AisConnectionHistHolder>();
            JSONArray m_jArry = new JSONArray(AisConnectionHistJSON.getData(getApplicationContext()));
            for (int i = m_jArry.length()-1; i>=0; i--) {
                JSONObject jo_inside = m_jArry.getJSONObject(i);
                String name = jo_inside.getString("name");
                String url = jo_inside.getString("url");
                String time = jo_inside.getString("time");
                String gate = jo_inside.getString("host");
                String icon;
                if (url.contains("paczka.pro")) {
                    icon = "www";
                } else {
                    icon = "local";
                }
                mConnectionsList.add(new AisConnectionHistHolder(url, name, time, gate, icon));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public void deleteConnection(View view){
        RelativeLayout vwParentRow = (RelativeLayout)view.getParent().getParent();
        TextView tv = (TextView)vwParentRow.findViewById(R.id.connection_name);
        String mCurrentName = tv.getText().toString();
        AisConnectionHistJSON.delConnection(getApplicationContext(), mCurrentName);
        onResume();
    }

    public void useInternetConnection(View view){
        RelativeLayout vwParentRow = (RelativeLayout)view.getParent().getParent();
        TextView tv = (TextView)vwParentRow.findViewById(R.id.connection_gate_id);
        String mCurrentGate = tv.getText().toString();
        TextView tv2 = (TextView)vwParentRow.findViewById(R.id.connection_name);
        String mCurrentName = tv.getText().toString();

        Config config  = new Config(getApplicationContext());


        if (mCurrentGate.startsWith("dom-")){
            mCurrentName = "https://" + mCurrentGate + ".paczka.pro";
            config.setAppLaunchUrl(mCurrentName, mCurrentGate);
        } else{
            config.setAppLaunchUrl(mCurrentName, mCurrentGate);
        }
        // go to app
        if (AisCoreUtils.onWatch()){
            startActivity(new Intent(getApplicationContext(), WatchScreenActivity.class));
        } else {
            startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
        }
    }

    public void useConnection(View view){
        TextView tvConnName = (TextView)view.findViewById(R.id.connection_name);
        String mCurrentName = tvConnName.getText().toString();

        TextView tvGate = (TextView)view.findViewById(R.id.connection_gate_id);
        String mCurrentGate = tvGate.getText().toString();

        Config config  = new Config(getApplicationContext());
        config.setAppLaunchUrl(mCurrentName, mCurrentGate);
        // go to app
        if (AisCoreUtils.onWatch()){
            startActivity(new Intent(getApplicationContext(), WatchScreenActivity.class));
        } else {
            startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
        }
    }


    private void showToast(String string){
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }


}