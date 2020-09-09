package pl.sviete.dom;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class AisSyncGateWorker extends Worker {

    private static final String TAG = AisSyncGateWorker.class.getSimpleName();
    SensorManager smm;
    List<Sensor> sensor;

    public AisSyncGateWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context applicationContext = getApplicationContext();

        Log.e(TAG, "AIS sync data with gate - doWork");

        // Do the work here - sync with gate
        Intent reportIntent = new Intent(getApplicationContext(), AisFuseLocationService.class);
        reportIntent.putExtra("getLastKnownLocation", true);
        getApplicationContext().startService(reportIntent);


        // TODO
        //        try {
        //            smm = (SensorManager) applicationContext.getSystemService(Context.SENSOR_SERVICE);
        //            sensor = smm.getSensorList(Sensor.TYPE_ALL);
        //            for (Sensor s : sensor) {
        //                Log.i(TAG, s.toString());
        //            }
        //        } catch (Exception e) {
        //            Log.w(TAG, e.getMessage());
        //        }


        //
        return Result.success();
    }


    @Override
    public void onStopped() {
        super.onStopped();
        Log.i(TAG, "AIS sync data with gate - OnStopped");
    }
}
