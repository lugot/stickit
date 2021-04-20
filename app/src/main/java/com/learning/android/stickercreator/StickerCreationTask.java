package com.learning.android.stickercreator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.learning.android.stickercreator.stickerprocessing.*;
import java.io.IOException;
import java.util.ArrayList;

public class StickerCreationTask extends AsyncTask<Uri,Integer,Bitmap> {

    private static final String TAG = "StickerCreationTask";

    private Context context;

    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private int mStickerWidth;
    private int mStickerHeight;
    public AsyncResponse mDelegate = null;

    private static long time;
    private static long timeInf;


    StickerCreationTask(Context context, int stickerWidth, int stickerHeight, ProgressBar prog, TextView progText){
        this.context = context;
        this.mStickerWidth = stickerWidth;
        this.mStickerHeight = stickerHeight;
        this.mProgressBar =prog;
        this.mProgressText =progText;
    }


    @Override
    protected Bitmap doInBackground(Uri... uris) {

        // Prendo tempo di esecuzione
        long start = System.currentTimeMillis();


        /* ---- FASE 1: Raccolta dell'immagine ---- */
        Log.d(TAG,"Inizio fase1");

        Uri photoUri = uris[0];
        Bitmap originalBmp = null;

        try {

            // Ruota la bitmap se necessario.
            originalBmp = StickerCreationUtils.handleSamplingAndRotationBitmap(context, photoUri);
            // Ritaglia la bitmap a un quadrato.
            originalBmp = StickerCreationUtils.crop(originalBmp);
            
        } catch (IOException e){
            e.printStackTrace();

            Log.e(TAG, "Error during saving of image: " + e.getMessage());
            return null;
        }

        // Scalamento alle dimensioni dello sticker
        originalBmp = Bitmap.createScaledBitmap(originalBmp,mStickerWidth,mStickerHeight,true);
        // Copia sulla quale applicare la maschera
        Bitmap originalCopy = Bitmap.createBitmap(originalBmp);



        /* ---- FASE 2: Segmentazione Semantica ---- */
        Log.d(TAG,"Fine fase1, inizio fase2");
        publishProgress(10);

        // Inizializzazione del segmentatore
        SemanticSegmentator semSegm = null;
        try {
            semSegm = new SemanticSegmentator(context);
        } catch (IOException e) {
            Log.e(TAG,"IOException form Segmentator Constructor: model not found or not loadable.");
            return null;
        }

        // Scalamento in preparazione alla segmentazione e conteggio degli scalamenti futuri necesasri
        int targetDim = semSegm.getInputSize();
        int numOfScaling = 0;
        while (originalBmp.getHeight() > targetDim){

            // Scala la bitmap a metà
            int bmpDim = originalBmp.getHeight() / 2;
            originalBmp = Bitmap.createScaledBitmap(originalBmp, bmpDim, bmpDim,true);

            // Segna il numero di scalamenti
            numOfScaling++;
        }

        long timeStartInf = System.currentTimeMillis();

        // Segmentazione della bitmap
        int[][] segmentedMatrixBmp = semSegm.segment(originalBmp);

        // Controllo sulla presenza di persone
        if (segmentedMatrixBmp == null) return  null;

        // Tempo finale inferenza totale
        timeInf = System.currentTimeMillis() - timeStartInf;



        /* ---- FASE 3: Isolamento componente connessa ---- */
        Log.d(TAG,"Fine fase2, inizio fase3");
        publishProgress(30);

        // Inizializzazione del labeler ed esecuzione
        ConnectedComponentsLabeler ccLabeler = new ConnectedComponentsLabeler(segmentedMatrixBmp);
        int[][] labeledMatrixBmp = ccLabeler.largestConnectedComponentFilter(Color.GRAY, Color.TRANSPARENT,3);

        // Se trovata una macchia troppo piccola ritorna nullo
        if(labeledMatrixBmp == null) return null;



        /* ---- FASE 4: Riempimento eventuali buchi ---- */
        Log.d(TAG,"Fine fase3, inizio fase4");
        publishProgress(50);

        // Ricerca del bordo
        ArrayList<Pixel> border = StickerCreationUtils.findBorder(segmentedMatrixBmp,0);

        // Ricerca di un unico bordo per connettività
        border = StickerCreationUtils.refineBorder(border);

        // Creazione di una matrice di solo bordo
        int[][] maskMatrixBmp = new int[labeledMatrixBmp.length][labeledMatrixBmp[0].length];
        for (Pixel p : border) maskMatrixBmp[p.x][p.y] = Color.GRAY;

        // Ricerca di un pixel dentro il bordo.
        Pixel startFloodingPixel = null;
        for (int i=0; i<mStickerHeight/2 && startFloodingPixel == null; i++) {
            for (int j=0; j<mStickerWidth/2 && startFloodingPixel == null; j++) {
                boolean foundBorder = false;

                // Trovato pixel associato alla figura di una persona
                if (labeledMatrixBmp[i][j] == Color.GRAY) {

                    // Controllo che non sia di bordo
                    for (Pixel p : border) {
                        if (p.x == i && p.y == j) {
                            foundBorder = true;
                            break;
                        }
                    }

                    // Assegnazione del pixel iniziale per eseguire il flooding
                    if (!foundBorder) startFloodingPixel = new Pixel(i,j);
                }
            }
        }

        // Esecuzione del flooding
        maskMatrixBmp = StickerCreationUtils.floodFill(maskMatrixBmp, startFloodingPixel, Color.GRAY, Color.GRAY);



        /* ---- FASE 5: Riscalamento e applicazione del filtro ---- */
        Log.d(TAG,"Fine fase4, inizio fase5");
        publishProgress(70);

        // Riscalamento fino alle dimensioni dell'originale
        for (int i=0; i<numOfScaling; i++) {
            maskMatrixBmp = StickerCreationUtils.scale2x(maskMatrixBmp);
        }

        // Ricerca del bordo
        border = StickerCreationUtils.findBorder(maskMatrixBmp, Color.TRANSPARENT);

        // Crescita del bordo
        maskMatrixBmp = StickerCreationUtils.growBorder(maskMatrixBmp,border,2,Color.WHITE);


        Log.d(TAG,"Fine fase5, applicazione della maschera");

        // Applicazione della maschera sulla immagine origianle
        Bitmap sticker = StickerCreationUtils.applyMask(originalCopy, maskMatrixBmp, mStickerWidth, mStickerHeight,  Color.TRANSPARENT, Color.WHITE);
        publishProgress(100);
        time = System.currentTimeMillis() - start;


        return sticker;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {

        mProgressBar.setProgress(values[0]);

        //Impostazione del progress message
        String text = "";
        if (values[0] == 10)       text += context.getString(R.string.looking_for_someone);
        else if (values[0] == 30)  text += context.getString(R.string.removing_the_garbage);
        else if (values[0] == 50)  text += context.getString(R.string.filling_the_holes);
        else if (values[0] == 70)  text += context.getString(R.string.creating_the_sticker);
        else if (values[0] == 100) text += context.getString(R.string.done);


        mProgressText.setText(text);
    }

    @Override
    protected void onPostExecute(Bitmap bmp){

        //Nascondo la progressbar
        mProgressBar.setVisibility(View.GONE);
        mProgressText.setVisibility(View.GONE);

        //Restituzione della bitmap tramite il delegator
        Object [] res = new Object[3];
        res[0]=bmp;
        res[1]=time;
        res[2]=timeInf;


        mDelegate.processFinish(res);
    }
}
