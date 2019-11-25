package pl.sviete.dom.connhist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

import pl.sviete.dom.R;

public class AisConnectionHistAdapter extends ArrayAdapter<AisConnectionHistHolder> {
    Context context;
    int layoutResourceId;
    AisConnectionHistHolder data[] = null;

    public AisConnectionHistAdapter(Context context, ArrayList<AisConnectionHistHolder> connectionHistHolders) {
        super(context, 0, connectionHistHolders);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        AisConnectionHistHolder holder = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.connection_history_list_item, parent, false);
        }
        // Lookup view for data population
        TextView connection_name = (TextView) convertView.findViewById(R.id.connection_name);
        TextView connection_time = (TextView) convertView.findViewById(R.id.connection_time);
        TextView connection_gate_id = (TextView) convertView.findViewById(R.id.connection_gate_id);
        ImageView connection_icon = (ImageView) convertView.findViewById(R.id.id_connection_icon);
        // Populate the data into the template view using the data object
        connection_name.setText(holder.connName);
        connection_time.setText(holder.connTime);
        connection_gate_id.setText(holder.gateID);

        if (holder.connIcon.equals("www")) {
            connection_icon.setImageResource(R.drawable.ic_www_connection_from_history);
        } else {
            connection_icon.setImageResource(R.drawable.ic_local_connection_from_history);
        }


        // Return the completed view to render on screen
        return convertView;
    }

}
