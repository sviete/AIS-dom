package pl.sviete.dom.connhist;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;


import pl.sviete.dom.BrowserActivityNative;
import pl.sviete.dom.Config;
import pl.sviete.dom.R;

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
            JSONArray m_jArry = new JSONArray(AisConnectionHistJSON.getHistoryConnectionsData(getApplicationContext()));
            for (int i = m_jArry.length()-1; i>=0; i--) {
                JSONObject jo_inside = m_jArry.getJSONObject(i);
                String url = jo_inside.getString("url");
                String time = jo_inside.getString("time");
                String gate = jo_inside.getString("gate");
                String ip = jo_inside.getString("ip");
                String user = "";
                if (jo_inside.has("user")) {
                    user = jo_inside.getString("user");
                }
                String desc = "";
                if (jo_inside.has("desc")) {
                    desc = jo_inside.getString("desc");
                }
                mConnectionsList.add(new AisConnectionHistHolder(url, time, gate, ip, user, desc));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public void deleteConnection(View view){
        RelativeLayout vwParentRow = (RelativeLayout)view.getParent().getParent();
        TextView tv = vwParentRow.findViewById(R.id.connection_url);
        String mCurrentName = tv.getText().toString();
        AisConnectionHistJSON.delConnection(getApplicationContext(), mCurrentName);
        onResume();
    }


    public void useConnection(View view){
        RelativeLayout vwParentRow;
        if (view.getId() == R.id.id_connection_icon){
            vwParentRow = (RelativeLayout)view.getParent().getParent();
        } else {
            vwParentRow = (RelativeLayout) view.getParent();
        }
        TextView tvGate = vwParentRow.findViewById(R.id.connection_gate_id);
        String mCurrentGate = tvGate.getText().toString();

        Config config  = new Config(getApplicationContext());
        config.setAppLaunchUrl(mCurrentGate);
        // go to app
        startActivity(new Intent(getApplicationContext(), BrowserActivityNative.class));
    }


}