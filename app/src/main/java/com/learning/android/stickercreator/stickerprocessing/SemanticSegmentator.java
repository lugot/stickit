package com.learning.android.stickercreator.stickerprocessing;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import com.learning.android.stickercreator.ImagePickerActiviy;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class SemanticSegmentator {
    /**
     * Classe che definisce un segmentatore semantico basato su TensorFlow Lite
     * e sul modello DeepLab_v3+, opportunamente convertito per essere utilizzato
     * da TensorFlow Lite e non quantizzato. La classe si focalizza sul riconoscimento e
     * segmentazione di persone.
     */

    private static final String TAG = "SemanticSegmentator";

    // Nome del model nella directory 'assets'
    private static final String MODEL_PATH = "graph.tflite";

    // Parametri del modello
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    private final static int INPUT_SIZE = 257;
    private final static int NUM_CLASSES = 21;
    private final static int COLOR_CHANNELS = 3;
    private final static int BYTES_PER_POINT = 4;

    // Mappatura in memoria del model
    ByteBuffer mModelBuffer;

    // ByteBuffer utilizzati per l'input e output dalla rete
    private ByteBuffer mImageData;
    private ByteBuffer mOutputs;
    private SharedPreferences sharedpreferences;

    /**
     * Costruttore del segmentatore.
     * @param context utilizzato per accedere alla rete neurale in memoria.
     * @throws IOException
     */
    public SemanticSegmentator(Context context) throws IOException {
        if (context == null) throw new IllegalArgumentException();

        // raccolgo le preferences per l'utilizzo di NNAPI o GPU
        sharedpreferences = context.getSharedPreferences(ImagePickerActiviy.MY_PREFERENCES, Context.MODE_PRIVATE);

        // Carica il model nel ByteBuffer
        mModelBuffer = loadModelFile(context);
        if (mModelBuffer == null) throw new IllegalArgumentException();

        // Alloca il ByteBuffer di input: dimensioni pari alle dimensioni di input della rete (257x257)
        //  per il numero di canali colore (RGB = 3) per il numero di byte per pixel (Un float in Java pesa
        //  4 byte). Tot: 792588 byte.
        mImageData = ByteBuffer.allocateDirect(
        1 * INPUT_SIZE * INPUT_SIZE * COLOR_CHANNELS * BYTES_PER_POINT);
        // htons per ordinare i byte in Little/Big endian secondo l'architettura
        mImageData.order(ByteOrder.nativeOrder());

        // Alloca il ByteBuffer di output: dimensioni pari alle dimensioni di input della rete (257x257)
        //  per il numero di classi di oggetti che riesce a segmentare il model (21) per il numero di byte
        //  per pixel (float = 4byte). Tot: 5548116
        mOutputs = ByteBuffer.allocateDirect(
        1 * INPUT_SIZE * INPUT_SIZE * NUM_CLASSES * BYTES_PER_POINT);
        mOutputs.order(ByteOrder.nativeOrder());

    }


    /**
     * Segmenta la figura delle persone utilizzando la rete neurale.
     * @param bmp immagine da segmentare. Deve essere quadrata e di dimensioni minori o uguali delle dimensioni
     *               di input della rete recuperabili con il metodo {@link #getInputSize()}.
     * @return l'immagine segmentata sottoforma di un matrice di interi con un '1' se nel corrispondente
     *         pixel è stata trovata la figura di una persona, '0' altrimenti.
     * @throws IllegalArgumentException nel caso in cui i vincoli sulla Bitamp non sono stati rispettati
     */
    public int[][] segment(Bitmap bmp) {
        if (bmp == null) throw new IllegalArgumentException();
        int bmpHeight = bmp.getHeight();
        int bmpWidth = bmp.getWidth();

        // Controllo delle dimensioni della Bitmap in ingresso
        if ( bmpWidth != bmpHeight|| bmpWidth > INPUT_SIZE) throw new IllegalArgumentException();

        // Converti la bitmap in una matrice di interi per l'elborazione,
        //  la matrice è sovradimensionata alle dimensioni della rete,
        //  i pixel non apparteneti all'immagine originale sono neri e non interferiscono
        //  con la bontà del risultato finale
        int[][] bmpPixels = new int[INPUT_SIZE][INPUT_SIZE];
        for (int i = 0; i < bmpHeight; i++) {
            for (int j = 0; j < bmpWidth; j++) {
                bmpPixels[i][j] = bmp.getPixel(j, i);
            }
        }

        // Riavvolgi i ByteBuffer per preparali a ricevere dati
        mImageData.rewind();
        mOutputs.rewind();

        // Carica la bitmap nel ByteBuffer in ingresso
        for (int[] row : bmpPixels) {
            for (int pixel : row) {

                // Rappresentazione di un int che memorizza un colore ARGB
                // +--------+--------+--------+--------+
                // |  ALPHA |   RED  |  GREEN |  BLUE  |
                // +--------+--------+--------+--------+
                // I byte sono selezionabili tramite shift a destra e mascheramento
                //  RED: shift a destra di 16 e mascheramento sui primi 8bit
                //  GREEN: shift a destra di 8 e masheramento sui primi 8bit
                //  BLUE: (shift a destra di 0), mascheramento sui primi 8bit

                // La rete non è quantizzata: float (32bit) in ingresso
                mImageData.putFloat((((pixel >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                mImageData.putFloat((((pixel >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                mImageData.putFloat(((pixel & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        // Crea l'interprete utilizzando TensorFlow Lite
        Interpreter.Options options = new Interpreter.Options();

         //imposto uso GPU in base a sharedpreferences
        if (sharedpreferences.getBoolean("usaGPU",true)) {

            GpuDelegate delegate = new GpuDelegate();
            options.addDelegate(delegate);
            Log.d(TAG,"Sto usando GPU");
        }

        // Istanziamento dell'interprete
        Interpreter interpreter = new Interpreter(mModelBuffer, options);


        // Avvio della segmentazione
        final long start = System.currentTimeMillis();
        interpreter.run(mImageData, mOutputs);

        Log.d(TAG,"Tempo inferenza: " + (System.currentTimeMillis() - start));


        // Creazione della maschera di uscita, dimensioni maggiorate
        int[][] oversizedMaskBitmap = new int[INPUT_SIZE][INPUT_SIZE];

        float maxScore = 0;
        float score = 0;
        boolean notFoundPerson = true;

        // Scansione per pixel della maschera di uscita
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int classIndex = 0;

                // Selezione della classe di oggetto che ha avuto score più elevato in quel pixel,
                //  trattasi di un argmax in c
                for (int c = 0; c < NUM_CLASSES; c++) {
                    score = mOutputs.getFloat((i * INPUT_SIZE * NUM_CLASSES + j * NUM_CLASSES + c) * BYTES_PER_POINT);
                    if (c == 0 || score > maxScore) {
                        maxScore = score;
                        classIndex = c;
                    }
                }

                // La rete ruota l'immagine (trasposizione della matrice con indici j e i invertiti)
                // Selezioniamo solo la classe di indice 15 (persone)
                if (classIndex == 15) {
                    oversizedMaskBitmap[j][i] = 1;
                    notFoundPerson = false;
                } else {
                    oversizedMaskBitmap[j][i] = 0;
                }
            }
        }

        // Se non è stata trovata alcuna persona ritorno nullo
        if (notFoundPerson) return null;

        // La matrice-maschera deve corrispondere alle dimensioni originali della Bitmap passata.
        int[][] maskBitmap = new int[bmpHeight][bmpWidth];
        for (int i = 0; i < bmpHeight; i++) {
            for (int j = 0; j < bmpWidth; j++) {
                maskBitmap[i][j] = oversizedMaskBitmap[i][j];
            }
        }

        // Chiusura del'interprete
        interpreter.close();


        return maskBitmap;
    }

    /**
     * Ritorna la dimensione (uguale in larghezza e altezza) di input accettata dal model.
     * Eventuale Bitmap da segmentare dovrà avere dimensioni minori o uguali di quelle specificate.
     * @return la dimensione in altezza (e larghezza) dell'input del model.
     */
    public static int getInputSize(){
        return INPUT_SIZE;
    }

    /**
     * Carica in un ByteBuffer il model della rete neurale.
     * @param context contesto usato per accedere alla risorsa.
     * @return il model caricato in un ByteBuffer
     * @throws IOException se il model non viene caricato
     */
    private static ByteBuffer loadModelFile(Context context)
            throws IOException {

        if (context == null) throw new IllegalArgumentException();

        // Crea assetFileDescriptor per accedere al model
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);

        // Stream di Input per accedere al model
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        // Offset del file in memoria
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        // Mappatura del file
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
