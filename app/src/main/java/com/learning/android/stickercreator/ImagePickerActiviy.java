package com.learning.android.stickercreator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

 // NOTA: I metodi statici in questa classe sono chiamati con ImagePickerActiviy.methodName()

public class ImagePickerActiviy extends AppCompatActivity {

    private static final String TAG = "ImagePickerActiviy";
    public static final String PHOTO_PATH = "com.learning.android.stickercreator.extra.MESSAGE";
    public static final String MY_PREFERENCES = "MyPrefs";

    // Request usata per controllare l'intent nell'if
    static final int REQUEST_IMAGE_CAPTURE = 1;

    // Request usata nell'intent per scattare la foto
    private static final int REQUEST_TAKE_PHOTO = 1;

    // Request usata nell'intent per acquisire la foto
    private static final int REQUEST_GET_SINGLE_FILE = 2;

    //Request usata nell'intent per avviare la ResultActivity
    private static final int REQUEST_RESULT_ACTIVITY = 3;

    // Elementi dell'UI
    private Button mCameraButton;
    private Button mGalleryButton;

    private Toolbar toolbar;

    SharedPreferences sharedpreferences;

    // Path nella memoria interna in cui l'immagine è salvata
    String currentPhotoPath;
    Uri photoUri;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_picker);

        // Inizializza la toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Inizailizza a null il photo path
        currentPhotoPath = null;

        // Inizializza gli elementi dell'UI
        mCameraButton = (Button) findViewById(R.id.camera_button);
        mGalleryButton = (Button) findViewById(R.id.gallery_button);

        // Prende le sharedpreferences
        sharedpreferences = getSharedPreferences(MY_PREFERENCES, Context.MODE_PRIVATE);

        // Elimina tutte le immagini obsolete dalla cartella privata
        clearAppFolder();

        // Imposta i Listener
        mCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });

        mGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchOpenGalleryIntent();
            }
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; aggiunge oggetti all'action bar se è presente
        getMenuInflater().inflate(R.menu.image_picker_menu, menu);


        return true;
    }

    // Gestisce i click sulla action bar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        // Se si è cliccato su settings avvio l'activity
        if (id == R.id.action_settings) {
            dispatchOpenSettingsIntent();
        }


        return super.onOptionsItemSelected(item);
    }


    /**
     * Apre la galleria
     */
    private void dispatchOpenGalleryIntent() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_GET_SINGLE_FILE);
    }

    /**
     * Apre l'activiy settings
     */
    private void dispatchOpenSettingsIntent() {

        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    /**
     * Crea un file nella memoria interna per salvare l'immagine e
     * chiama la fotocamera per scattarla
     */
    public void dispatchTakePictureIntent() {

        // Crea l'intent
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Alloca un file alla memoria interna
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.d(TAG, "Photo file IO EXCEPTION");
            }

            if (photoFile != null) {
                // Ottiene l'URI della foto
                photoUri = FileProvider.getUriForFile(this, "com.example.android.fileprovider", photoFile);

                // Mette l'URI nell'intent per poter salvare l'immagine
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                // Chiama la camera app di base
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    /**
     * Apre la ResultActivity che poi si occuperà di processare l'immagine
     */
    private void elaborateImage(){
        if (currentPhotoPath != null) {
            Intent intent = new Intent(this, ResultActivity.class);
            intent.putExtra(PHOTO_PATH,photoUri.toString());
            startActivityForResult(intent, REQUEST_RESULT_ACTIVITY);
        }
    }

    // Call back dall'app fotocamera/galleria con l'immagine salvata/selezionata e della ResultActivity
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Result dell'intent alla fotocamera
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            elaborateImage();
        }

        // Result dell'intent alla galleria
         else if (resultCode == RESULT_OK && requestCode== REQUEST_GET_SINGLE_FILE ) {
             photoUri = data.getData();

             // Prende il path dall'URI
             if (photoUri != null) {
                currentPhotoPath = photoUri.getPath();
             }
             elaborateImage();
         }
         else if (requestCode == REQUEST_RESULT_ACTIVITY ){

             //Elimina l'immagine dalla cartella privata
             delete(currentPhotoPath);

             //Pulisce la cache (rimuove lo stato salvato della ResultActivity)
             deleteCache(this);
         }
    }

    /**
     * Alloca un file nella memoria interna
     */
    private File createImageFile() throws IOException {

        // Crea un photo name unico
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String imageFileName = "JPEG" + timeStamp + "_";

        // Restituisce un'immagine a partire da un URI
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);

        // Salva il path dell'immagine
        currentPhotoPath = image.getAbsolutePath();


        return image;
    }

    /**
     * Elimina tutte le eventuali immagini obsolete dalla cartella locale dell'app
     */
    private void clearAppFolder() {

        // Prende il path della cartella
        String path = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getPath();
        Log.d("Files", "Path: " + path);
        File directory = new File(path);

        // Crea un array contenente tutti i file della cartella
        File[] files = directory.listFiles();

        // Elimina tutti i file dalla cartella
        for (int i = 0; i < files.length; i++) {
            delete(files[i].getPath());
            Log.d("Files", "Deleted file at: " + files[i].getPath());
        }

        //Pulisce la cache
        deleteCache(this);

    }

    /**
     * Elimina il contenuto della cache e la cartella
     *
     * @param context       Activity context
     */
    private static void deleteCache(Context context) {
        try {

            //Prende la cartella della cache
            File dir = context.getCacheDir();

            //Elimina la cartella
            deleteDir(dir);
        } catch (Exception e) { e.printStackTrace();}
    }

    /**
     * Metodo ricorsivo che elimina una cartella e il suo contenuto
     *
     * @param dir       La cartella da eliminare
     *
     * @return          Il risultato dell'eliminazione
     */
    private static boolean deleteDir(File dir) {

        //Se dir è una cartella
        if (dir != null && dir.isDirectory()) {

            //Salva in un array il contenuto di dir
            String[] children = dir.list();

            //Per ogni elemento dell'array
            for (int i = 0; i < children.length; i++) {

                //Elimina l'elemento
                boolean success = deleteDir(new File(dir, children[i]));

                //In caso di fallimento restituisce false
                if (!success) {


                    return false;
                }
            }


            //Cartella svuotata correttamente, la eliminala e restituie il risultato dell'eliminazione
            return dir.delete();

        }

        //Se dir è un file lo elimina e restituisce il risultato dell'eliminazione
        else if(dir!= null && dir.isFile()) {


            return dir.delete();
        }

        //Se dir è null restituisce false (fallimento)
        else {


            return false;
        }
    }

    /**
     * Elimina l'immagine dato il path
     *
     * @param path  Il path dell'immagine da eliminare
     * */
    private void delete(String path) {

        File file = new File(path);

        if (file.exists()) {
            if (file.delete()) {
                System.out.println("Eliminata " + path);
            } else {
                Log.d(TAG, "Non eliminata " + path);
            }
        }
    }

    // Non è necessario salvare lo stato
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}