package com.learning.android.stickercreator;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private final static String TAG = "Settings";

    private Switch mUtilizzoGPU;
    private Button mSave;
    private Button mBack;
    SharedPreferences sharedpreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Inizializzo l'UI
        mUtilizzoGPU = (Switch) findViewById(R.id.enable_gpu);
        mSave = (Button) findViewById(R.id.save_button);
        mBack = (Button) findViewById(R.id.back_button);

        // Inizializzo le sharedpreferences
        sharedpreferences = getSharedPreferences(ImagePickerActiviy.MY_PREFERENCES, Context.MODE_PRIVATE);

        if (savedInstanceState != null){
            // Se è presente uno stato salvato ripristina il valore dello switch
            mUtilizzoGPU.setChecked(savedInstanceState.getBoolean("useGpu"));
        }
        else {
            // Altrimenti imposta lo switch al valore salvato nelle sharedpreferences
            getPreferences();
        }

        // Imposto i Listener
        mUtilizzoGPU.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                // Quando cambio mostro un toast
                if(isChecked) {
                    Toast.makeText(SettingsActivity.this, getString(R.string.gpu_on), Toast.LENGTH_LONG).show();
                }
                else {
                    Toast.makeText(SettingsActivity.this, getString(R.string.gpu_off), Toast.LENGTH_LONG).show();
                }

                Log.v("GPU=", "" + isChecked);
            }
        });


        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                salva(); //Salvo
                finish(); //Ritorno alla main activity
            }
        });

        mBack.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                finish(); //Ritorno alla main activity
            }
        });


    }


    /**
     *  Modifica le sharedpreferences
     */
    private void salva() {

        SharedPreferences.Editor editor = sharedpreferences.edit();

        if(mUtilizzoGPU.isChecked()) {
            editor.putBoolean("usaGPU", true);
        }
        else {
            editor.putBoolean("usaGPU", false);
        }

        editor.commit();

        Toast.makeText(SettingsActivity.this, getString(R.string.saved), Toast.LENGTH_LONG).show();
    }


    /**
     *  Legge le sheredpreferences e imposta lo stato dello swicth
     */
    private void getPreferences() {

        if(sharedpreferences.getBoolean("usaGPU", true)) {
            mUtilizzoGPU.setChecked(true);
        }
        else{
            mUtilizzoGPU.setChecked(false);
        }

    }

    // Salva lo stato dell'istanza
    @Override
    public void onSaveInstanceState (Bundle savedInstanceState){
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean("useGpu", mUtilizzoGPU.isChecked());
    }

    // Non è necessario salvare lo stato in modo persistente
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}