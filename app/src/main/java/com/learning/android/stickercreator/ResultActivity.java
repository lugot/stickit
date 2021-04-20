package com.learning.android.stickercreator;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ResultActivity extends AppCompatActivity implements AsyncResponse {

    private final static String TAG = "ResultActivity";

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    // Elementi dell'UI
    private TextView mPath;
    private TextView mTempoInf;
    private TextView mTempoTot;
    private Button mRetryButton;
    private Button mSaveButton;
    private ImageView mResultImageView;

    // Progress bar
    private ProgressBar mStickerCreationProgressBar;
    private TextView mStickerCreationProgessInfo;

    private Bitmap sticker;
    private Uri photoUri;
    private String savedImagePath;
    private long time;
    private long timeInf;

    public final static int STICKER_WIDTH = 512;
    public final static int STICKER_HEIGHT = 512;

    public SharedPreferences sharedpreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.result_activity);

        Intent intent = getIntent();
        photoUri = Uri.parse(intent.getStringExtra(ImagePickerActiviy.PHOTO_PATH));

        // Inizializza l'UI
        mPath = (TextView) findViewById(R.id.path);
        mTempoInf = (TextView) findViewById(R.id.tempo_inferenza);
        mTempoTot = (TextView) findViewById(R.id.tempo_totale);
        mResultImageView = (ImageView) findViewById(R.id.result_image_view);
        mSaveButton = (Button) findViewById(R.id.button_save);
        mRetryButton = (Button) findViewById(R.id.button_retry);

        // Inizializza la ProgressBar
        mStickerCreationProgessInfo = (TextView) findViewById(R.id.progress_text);
        mStickerCreationProgessInfo.setVisibility(View.VISIBLE);
        mStickerCreationProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mStickerCreationProgressBar.setMax(100);
        mStickerCreationProgressBar.setVisibility(View.VISIBLE);
        mStickerCreationProgressBar.setProgress(0);

        // Inizializzo i Listener
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            // @RequiresApi(api = Build.VERSION_CODES.KITKAT) TODO: va tolto?
            @Override
            public void onClick(View v) {
                checkPermissionAndSave();
            }
        });

        mRetryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); //Ritorna alla main activity
            }
        });

        // Imposto lo sfondo dell'ImageView a trasparente
        mResultImageView.setAlpha(0.9f);

        // Prendo le sharedpreferences
        sharedpreferences = getSharedPreferences(ImagePickerActiviy.MY_PREFERENCES, Context.MODE_PRIVATE);

        //Verifica la presenza di uno stato salvato e dello sticker nella cache
        File cachedFile = new File(getCacheDir(), "pic");
        if (savedInstanceState != null && cachedFile.exists()) {

            //Ripristina lo stato
            restoreState(cachedFile, savedInstanceState);
        }
        else {

            // Disabilita l'input
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

            // Avvio l'elaborazione
            segmentationPipeline();
        }
    }

    /**
     * Esegue l'elaborazione in un AsyncTask
     */
    private void segmentationPipeline() {

        StickerCreationTask stickerCreationTask = new StickerCreationTask(getApplicationContext(), STICKER_WIDTH, STICKER_HEIGHT, mStickerCreationProgressBar, mStickerCreationProgessInfo);
        stickerCreationTask.mDelegate = this;
        stickerCreationTask.execute(photoUri);
    }

    /**
     * Verifica che i permessi siano stati accordati e salva
     */
    private void checkPermissionAndSave() {

        // Controlla che i permessi siano stati accordati
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Permesso non accordato, richiedilo
            ActivityCompat.requestPermissions(ResultActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        else {
            // Permesso accordato, se l'immagine non è già stata salvata, salvala
            if (savedImagePath == null) {
                savedImagePath = saveInGallery(sticker, this);
                Toast.makeText(ResultActivity.this, getString(R.string.saved), Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(ResultActivity.this, getString(R.string.image_already_saved), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        // Non necessario ma utile se in futuro dovessero essere necessari nuovi permessi
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permesso accordato, salva
                    //NON verifico se l'immagine è già stata salvata in questo punto, in un utilizzo normale non ci si può arrivare una volta salvata l'immagine
                    Toast.makeText(ResultActivity.this, getString(R.string.saved), Toast.LENGTH_LONG).show();

                    savedImagePath = saveInGallery(sticker, this);
                }
                else {
                    // Permesso negato, non salvare
                    Toast.makeText(ResultActivity.this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     *  Ripristina lo stato dell'Activity
     *
     * @param cachedFile                File PNG contenente lo Sticker da ripristinare
     * @param savedInstanceState        Bundle contenente lo stato di time e timeInf da ripristinare
     */
    private void restoreState(File cachedFile, Bundle savedInstanceState) {
        Log.d("Directory", "Cachedir: " + cachedFile);

        //Ripristina la Bitmap
        sticker= BitmapFactory.decodeFile(cachedFile.getPath());

        //Visualizza
        mResultImageView.setImageBitmap(sticker);

        //Nasconde la ProgressBar
        mStickerCreationProgressBar.setVisibility(View.GONE);
        mStickerCreationProgessInfo.setVisibility(View.GONE);

        //Ripristina time e timeInf
        time = savedInstanceState.getLong("time");
        timeInf = savedInstanceState.getLong("timeInf");

        //Visualizza
        setTime(time,timeInf);
    }

    /**
     * Imposta il valore della stringa di feedback
     *
     * @param time  Il tempo di elaborazione totale
     */
    private void setTime(long time, long timeInf) {

        String testo = getString(R.string.processed_using);
        String testo1 = getString(R.string.inference_time) + " " + timeInf + "ms";
        String testo2 = getString(R.string.total_time) + " " + time + "ms";

        //Verifica le sharedpreferences per sapere come è stata elaborata
        if(sharedpreferences.getBoolean("usaGPU", true)) {
            testo += " " + getString(R.string.gpu);
        }
        else{
            testo += " " + getString(R.string.cpu);
        }

        mPath.setText(testo);
        mTempoInf.setText(testo1);
        mTempoTot.setText(testo2);
    }

    /**
     * Salva il risultato dell'elaborazione in galleria
     *
     * @param bitmap    La bitmap che si desidera salvare
     *
     * @return          Il percorso dell'immagine salvata
     */
    private static String saveInGallery(Bitmap bitmap, Context context) {

        ContentValues values = new ContentValues();
        OutputStream output;

        // Ottieni il percorso della scheda SD
        File filepath = Environment.getExternalStorageDirectory();

        // Crea la cartella StickIT nella scheda SD
        File dir = new File(filepath.getAbsolutePath() + "/StickIT/");

        dir.mkdirs();

        // Crea un nome univoco per l'immagine
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

        File file = new File(dir, "Stiker" + timeStamp + ".png" );

        //Creazione del file
        createFile(file,bitmap);

        //Inserimento dei dati relativi all'immagine
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());

        //Inserimento dell'immagine
        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);


        return file.getPath();
    }

    /**
     *  Crea un file PNG data una Bitmap
     *
     * @param file         Il file su cui scrivere
     * @param bitmap       La Bitmap da scrivere
     */
    private static void createFile(File file, Bitmap bitmap){
        try {
            OutputStream output;
            output = new FileOutputStream(file);

            //La bitmap ha lo sfondo trasparente
            bitmap.setHasAlpha(true);

            // Comprimi la bitmap in un .png con qualità 100%
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            output.flush();
            output.close();
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Quando termina l'AsyncTask visualizza il risultato dell'elaborazione
     *
     * @param output [0]        La bitmap elaborata
     * @param output [1]        Il tempo impiagato per l'elaborazione (Long)
     * @param output [2]        Im tempo di inferenza (Long)
     */
    @Override
    public void processFinish(Object [] output) {

        if (output[0] != null) {

            // Se non ci sono stati errori
            // Mostra il risultato
            mResultImageView.setImageBitmap((Bitmap)output[0]);
            sticker = (Bitmap)output[0];
            setTime((long)output[1], (long)output[2]);
            time=(long)output[1];
            timeInf=(long)output[2];

            // Abilita l'input
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
        else {

            // Crea un messaggio di errore
            AlertDialog.Builder goHome = new AlertDialog.Builder(this);
            goHome.setMessage(getString(R.string.no_faces_detected));
            goHome.setCancelable(false);
            goHome.setPositiveButton(getString(R.string.retry), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Torna all'activity principale
                    ResultActivity.this.finish();
                }
            });

            // Mostra il messaggio di errore
            AlertDialog alertNofaces = goHome.create();
            alertNofaces.show();
        }
    }


    /**
     * Salva lo stato dell'applicazione se non è già presente uno stato salvato
     */
    private void saveCurrentStiker() {

        // Verifica la presenza di uno stato salvato nella cache
        File cachedBpm = new File(getCacheDir(), "pic");
        if (!cachedBpm.exists()) {

            // Se non è presente
            // Salva la Bitmap "sticker" nella cache
            createFile(cachedBpm,sticker);

            Log.d("saved", "file created");
        }

    }

    // Salva lo stato dell'istanza
    @Override
    public void onSaveInstanceState (Bundle savedInstanceState){
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putLong("time", time);
        savedInstanceState.putLong("timeInf", timeInf);

        // Salva lo sticker se necessario (se non nè già stato salvato)
        saveCurrentStiker();
    }

    // Non è necessario salvare lo stato in modo persistente
    @Override
    public void onPause() {
        super.onPause();
        Log.d("Pause", "Pause");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("Resume", "Resume");
    }

}
