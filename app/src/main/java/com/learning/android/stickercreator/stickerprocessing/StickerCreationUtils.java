package com.learning.android.stickercreator.stickerprocessing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import static com.learning.android.stickercreator.ResultActivity.STICKER_HEIGHT;
import static com.learning.android.stickercreator.ResultActivity.STICKER_WIDTH;

public class StickerCreationUtils {
    /**
    * Collezione di metodi statici per facilitare la
     * creazione di uno sticker.
    */

    /**
     * Risolve il problema della rotazione dell'immagine se necessario.
     * @param context       Il contesto corrente
     * @param selectedImage L'URI dell'immagine
     * @return Bitmap dell'immagine risultante
     * @throws IOException
     */
    public static Bitmap handleSamplingAndRotationBitmap(Context context, Uri selectedImage)
            throws IOException {
        int MAX_WIDTH = STICKER_WIDTH;
        int MAX_HEIGHT = STICKER_HEIGHT;


        // All'inizio decodifica con inJustDecodeBounds=true per controllare le dimensioni
        final BitmapFactory.Options options = new BitmapFactory.Options();

        // Se impostato a true il decodificatore ritorna null ma assegna comunque i campi out...
        // (es. outHeight e outWidth)
        options.inJustDecodeBounds = true;

        // Apre l'InputStream sul contenuto associato con l'URI
        InputStream imageStream = context.getContentResolver().openInputStream(selectedImage);

        // Decodifica l'input stream in una bitmap
        BitmapFactory.decodeStream(imageStream, null, options);

        // Chiude e rilascia le risorse
        imageStream.close();

        // Calcola inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);

        // Decodifica la bitmap con inSampleSize impostato
        options.inJustDecodeBounds = false;

        imageStream = context.getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);


        return rotateImageIfRequired(context, img, selectedImage);

    }

    /**
     * Calcola inSampleSize per l'uso in un oggetto BitmapFactory.Options per la decodifica di bitmap
     * usando i metodi di BitmapFactory. Questa implementazione calcola l'inSampleSize più vicina
     * alla bitmap finale decodificata con larghezza ed altezza uguali o più grandi di quelli richiesti.
     *
     * @param options   Un oggetto options con i parametri out... già popolati (attraverso un metodo
     *                  di decodifica con inJustDecodeBounds==true)
     * @param reqWidth  La larghezza richiesta della bitmap risultante
     * @param reqHeight L'altezza richiesta della bitmap risultante
     * @return          Il valore da usare per inSampleSize
     */
    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Altezza e larghezza dell'immagine originale
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        System.out.print("h" + height);
        System.out.print("w" + width);

        if (height > reqHeight || width > reqWidth) {

            // Calcola il rapporto tra altezza e larghezza rispetto a altezza richiesta e larghezza richiesta
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Sceglie il più piccolo rapporto come valore inSampleSize, questo garantisce un'immagine finale
            // con entrambe le dimensioni più grandi o uguali alle dimensioni richieste
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // Calcolo il numero totale di pixel nell'immagine per gestire il caso
            // di uno strano aspect ratio (es. panoramica). In questo caso il numero
            // totale di pixel potrebbe essere troppo grande per stare in memoria
            // quindi si scala maggiormente.
            final float totalPixels = width * height;

            // Qualunque immagine più grande del doppio delle dimensioni richieste
            // viene ulteriormente scalata.
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
            System.out.println("sample " + inSampleSize);
        }


        return inSampleSize;
    }

    /**
     * Ruota l'immagine se richiesto.
     * @param img           bitmap dell'immagine
     * @param selectedImage URI dell'immagine
     * @return Bitmap risultante dopo la manipolazione
     */
    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) throws IOException {

        InputStream input = context.getContentResolver().openInputStream(selectedImage);
        // Uso la classe ExifInterface per ottenere il tag della rotazione della fotocamera
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(selectedImage.getPath());

        // Salvo il TAG_ORIENTATION
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        //Eseguo il metodo rotateImage ruotando di 90°, 180° o 270° in base al caso
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:


                return img;
        }
    }

    /**
     * Esegue la rotazione della bitmap di un angolo degree
     * @param img       Bitmap da ruotare
     * @param degree    Angolo di rotazione
     * @return          Bitmap ruotata
     */
    private static Bitmap rotateImage(Bitmap img, int degree) {

        // Crep una matrice identità
        Matrix matrix = new Matrix();

        //Ruoto la matrice di un angolo degree
        matrix.postRotate(degree);

        //Creo una bitmap ruotata applicando la matrice alla bitmap iniziale
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);

        //permetto al garbage collector di eliminare img se non è referenziata.
        img.recycle();


        return rotatedImg;
    }

    /**
     * Ritaglia la bitmap ad una bitmap quadrata
     * @param bitmap    Bitmap iniziale
     * @return          Bitmap ritagliata
     */
    public static Bitmap crop(Bitmap bitmap) {
        // prendo le dimensioni originali
        int photoH = bitmap.getHeight();
        int photoW = bitmap.getWidth();

        // ritaglio a immagine quadrata in base alle dimensioni
        if (photoH > photoW) {
            int startingPoint = (photoH - photoW) / 2;
            bitmap = Bitmap.createBitmap(bitmap, 0, startingPoint, photoW, photoW);
        } else {
            int startingPoint = (photoW - photoH) / 2;
            bitmap = Bitmap.createBitmap(bitmap, startingPoint, 0, photoH, photoH);
        }


        return bitmap;
    }

    /**
     * Scala una bitmap rappresentata con una matrice, usando l'algoritmo scale2x utilizzato
     * in emulatori e per fare pixel arts.
     * @param bmp la matrice da scalare.
     * @return la matrice scalata.
     */
    public static int[][] scale2x (int[][] bmp) {

        // Creazione della matrice scalata
        int[][] scaledBmp = new int[bmp.length *2 ][bmp[0].length * 2];

        // https://www.scale2x.it/algorithm
        // Per rispetto verso il progettista dell'algoritmo NON sono stati rinominati i nomi delle
        //  variabili così come la struttura del codice
        //
        //  ABC
        //  DEF -> E0E1
        //  GHI    E2E3
        //
        // L'algoritmo 'espande' il pixel E in quattro pixel E0, E1, E2, E3 calcolando il valore di questi
        //  confrontando il valore dei pixel adiacenti a E:
        //  controlla il colore dei pixel in verticale (B e H) e in orizzontale (D e F): se entrambi diversi assegna
        //  i valori di Ex in base al colore dei pixel 'vicini'. Ad esempio E1 si trova nel vertice in alto a destra,
        //  gli verrà assegnato il valore di B o F se uguali, E altrimenti

        // Commentate le chiamate di codice inutile, ma mantenuta la struttura (vertici mai utilizzati)
        int e0, e1, e2, e3;
        int a, b, c, d, e, f, g, h, i;

        for (int x=0; x<bmp.length; x++) {
            for (int y=0; y<bmp[0].length; y++) {

                //a = StickerCreationUtils.getPixel(bmp,x-1,y-1);
                b = StickerCreationUtils.getPixel(bmp,x-1,y);
                //c = StickerCreationUtils.getPixel(bmp,x-1,y+1);
                d = StickerCreationUtils.getPixel(bmp,x,y-1);
                e = StickerCreationUtils.getPixel(bmp,x,y);
                f = StickerCreationUtils.getPixel(bmp,x,y+1);
                //g = StickerCreationUtils.getPixel(bmp,x+1,y-1);
                h = StickerCreationUtils.getPixel(bmp,x+1,y);
                //i = StickerCreationUtils.getPixel(bmp,x+1,y+1);

                if (b != h && d != f) {
                    e0 = d == b ? d : e;
                    e1 = b == f ? f : e;
                    e2 = d == h ? d : e;
                    e3 = h == f ? f : e;
                } else {
                    e0 = e;
                    e1 = e;
                    e2 = e;
                    e3 = e;
                }

                scaledBmp[ x*2 ][ y*2 ] = e0;
                scaledBmp[ x*2 ][ y*2 +1 ] = e1;
                scaledBmp[ x*2 +1 ][ y*2 ] = e2;
                scaledBmp[ x*2 +1 ][ y*2 +1 ] = e3;
            }
        }


        return scaledBmp;
    }


    /**
     * Involucro dell'accesso bmp[x][y] che esegue controlli OutOfBound, in caso assegna il valore più
     *  vicino al bordo.
     * @param bmp la matrice dalla quale accedere al valore.
     * @param x la coordinata.
     * @param y l'ascissa.
     * @return bmp[x][y] con controllo di OutOfBounds.
     */
    private static int getPixel(int[][] bmp, int x, int y){
        if (x < 0) x = 0;
        if (x >= bmp.length) x = bmp.length -1;
        if (y < 0) y = 0;
        if (y >= bmp[0].length) y = bmp[0].length -1;


        return bmp[x][y];
    }


    /**
     * Implementazione dell'algoritmo flood fill in quattro direzioni.
     * https://en.wikipedia.org/wiki/Flood_fill.
     * @param matrixBmp la matrice sulla quale eseguire il fill.
     * @param startPixel il pixel di partenza.
     * @param replaceColor colore di rimpiazzamento.
     * @param borderColor colore del bordo.
     * @return la matrice riempita di colore.
     */
    public static int[][] floodFill(int[][] matrixBmp, Pixel startPixel, int replaceColor, int borderColor) {
        matrixBmp[startPixel.x][startPixel.y] = replaceColor;

        // Implementazione a coda
        LinkedList<Pixel> queue = new LinkedList<>();
        queue.add(startPixel);

        while (!queue.isEmpty()) {
            Pixel p = queue.remove();

            // Controlla se il pixel in alto è da colorare
            if (p.x -1 >= 0 && matrixBmp[p.x -1][p.y] != borderColor && matrixBmp[p.x -1][p.y] != replaceColor) {

                // Coloralo
                matrixBmp[p.x -1][p.y] = replaceColor;
                // Accodalo
                queue.add(new Pixel(p.x -1, p.y));
            }

            // Controlla se il pixel a sinistra è da colorare
            if (p.y -1 >= 0 && matrixBmp[p.x][p.y -1] != borderColor && matrixBmp[p.x][p.y -1] != replaceColor) {
                matrixBmp[p.x][p.y -1] = replaceColor;
                queue.add(new Pixel(p.x, p.y -1));
            }

            // Controlla se il pixel in basso è da colorare
            if (p.x +1 < matrixBmp.length && matrixBmp[p.x +1][p.y] != borderColor && matrixBmp[p.x +1][p.y] != replaceColor) {
                matrixBmp[p.x +1][p.y] = replaceColor;
                queue.add(new Pixel(p.x +1, p.y));
            }

            // Controlla se il pixel a destra è da colorare
            if (p.y +1 < matrixBmp[0].length && matrixBmp[p.x][p.y +1] != borderColor && matrixBmp[p.x][p.y +1] != replaceColor) {
                matrixBmp[p.x][p.y +1] = replaceColor;
                queue.add(new Pixel(p.x, p.y +1));
            }
        }


        return matrixBmp;
    }

    /**
     * Trova il bordo in una matrice dato il colore di sfondo.
     * @param matBmp la matrice sulla quale ricercare il bordo.
     * @param bgColor il colore di sfondo.
     * @return array di Pixel del bordo
     */
    public static ArrayList<Pixel> findBorder(int[][] matBmp, int bgColor) {
        int bmpHeight = matBmp.length;
        int bmpWidth = matBmp[0].length;

        ArrayList<Pixel> border = new ArrayList<>();

        // Scansione della matrice
        for (int i = 0; i < bmpHeight; i++) {
            for (int j = 0; j < bmpWidth; j++) {

                // Trovato pixel non di sfondo
                if (matBmp[i][j] != bgColor) {

                    if ((i == 0) || (j == 0) || (i == bmpHeight - 1) || (j == bmpWidth - 1) ||      // Se sono al bordo
                            (i > 0 && matBmp[i - 1][j] == bgColor) ||                               // Se sopra c'è sfondo
                            (j > 0 && matBmp[i][j - 1] == bgColor) ||                               // Se a sinistra c'è sfondo
                            (i < bmpHeight - 1 && matBmp[i + 1][j] == bgColor) ||                   // Se sotto c'è sfondo
                            (j < bmpWidth - 1 && matBmp[i][j + 1] == bgColor)) {                    // Se a destra c'è sfondo

                        border.add(new Pixel(i,j));
                    }
                }
            }
        }


        return border;
    }


    /**
     * Espandi il bordo di una matrice.
     * @param matrixBmp la matrice sulla quale far crescre il bordo.
     * @param border il bordo come array di pixel.
     * @param radious il raggio del bordo.
     * @param borderColor il colore del bordo.
     * @return la matrice col bordo espanso.
     */
    public static int[][] growBorder (int[][] matrixBmp, ArrayList<Pixel> border, int radious, int borderColor){

        for (Pixel px : border){
            // Pattern circolare attorno al pixel del bordo
            ArrayList<Pixel> pattern =
                    StickerCreationUtils.getCircularPattern(px.x, px.y,radious,
                            0,0, matrixBmp.length, matrixBmp[0].length);

            // Applicazione del pattern per ricolorare
            for (Pixel patternPixel : pattern)
                matrixBmp[patternPixel.x][patternPixel.y] = borderColor;
        }


        return matrixBmp;
    }

    /**
     * Prende un bordo come array di pixel e isola la parte connessa di lunghezza maggiore.
     * @param b il bordo da raffinare.
     * @return il bordo con la parte connessa di lunghezza maggiore isolata.
     */
    public static ArrayList<Pixel> refineBorder(ArrayList<Pixel> b) {

        // Coda per mantenere la connettività. La coda è di interi, indici di b
        //  poichè si usa un approccio a sentinella (null / not null) sugli elementi di b
        LinkedList<Integer> queue = new LinkedList<>();
        // Lista dei bordi trovati
        ArrayList<ArrayList<Pixel>> borders = new ArrayList<>();

        // Il valore null viene utilizzato come sentinella in b, ogni pixel viene ricopiato
        //  in una delle liste dentro borders, per ogni ciclo di questo while troviamo un
        //  bordo connesso
        while (!containsAllNull(b)) {

            // Bordo attuale
            ArrayList<Pixel> border = new ArrayList<>();

            // Trova il primo Pixel non nullo, legale poichè siamo dentro al while
            for (int i=0; queue.isEmpty(); i++) {
                if (b.get(i) != null) queue.add(i);
            }

            while (!queue.isEmpty()) {

                // Indice del Pixel attuale
                int pxIndex = queue.remove();

                // Ricopia pixel in uno nuovo e appendi alla lista
                Pixel p = new Pixel(b.get(pxIndex).x, b.get(pxIndex).y);
                border.add(p);
                // Rendi nullo il pixle in b
                b.set(pxIndex, null);

                // Ricerca di pixel vicini (8 direzioni)
                for (int i = 0; i < b.size(); i++) {
                    if (b.get(i) != null && isNeighbour(p, b.get(i))) {

                        // Accoda i pixel trovati
                        if (!queue.contains(i)) queue.add(i);
                    }
                }
            }

            // Attuale bordo non ha più pixel connessi, aggiungi alla lista dei bordi
            borders.add(border);
        }

        // Ricerca sui bordi trovati e trova il max
        ArrayList<Pixel> maxSizeBorder = borders.get(0);
        for (ArrayList<Pixel> border : borders) {
            if (border.size() > maxSizeBorder.size()) maxSizeBorder = border;
        }


        return maxSizeBorder;
    }

    /**
     * Controlla se un ArrayList contiene tutti valori nulli
     * @param a l'ArrayList da controllare
     * @return true se contiene tutti nulli, false altrimenti
     */
    private static boolean containsAllNull(ArrayList<Pixel> a) {
        for (Pixel p : a) {
            if (p != null) return false;
        }


        return true;
    }


    /**
     * Controlla se i due pixel sono adiacenti (anche diagonalmente)
     * @param a il primo pixel
     * @param b il secondo pixel
     * @return true se i due pixel sono vicini, false altrimenti
     */
    private static boolean isNeighbour(Pixel a, Pixel b) {

        // Controllo sulla vicinanza
        if (a.x - b.x <= 1 && a.x - b.x >= -1 &&
            a.y - b.y <= 1 && a.y - b.y >= -1) {
            return true;
        }


        return false;
    }

    /**
     * Crea un pattern circolare attorno ad un pixel dato bordo e raggio. Controlla ed elimina eventuali
     * OutOfBounds.
     * @param x l'ascissa di partenza del pattern.
     * @param y l'ordinata di partenza del pattern.
     * @param radious il raggio del pattern.
     * @param lowerBoundX bound inferiore sulla ascissa.
     * @param lowerBoundY bound inferiore per l'ordinata.
     * @param upperBoundX bound superiore per l'ascissa.
     * @param upperBoundY bound superiore per l'ordinata.
     * @return il pattern circolare.
     */
    private static ArrayList<Pixel> getCircularPattern(int x, int y, int radious, int lowerBoundX, int lowerBoundY, int upperBoundX, int upperBoundY){
        ArrayList<Pixel> pattern = new ArrayList<>();

        // Crea quadrato attorno al punti desiderato
        for (int i=-radious; i<radious; i++) {
            for (int j=-radious; j<radious; j++) {
                pattern.add( new Pixel(x+i,y+j) );
            }}

        // Smussa per creare cerchio e elimina punti che vanno fuori dai limiti
        Iterator<Pixel> itr = pattern.iterator();
        while (itr.hasNext()) {
            Pixel px = itr.next();

            // Calcola la distanza euclidea tra il punto e il centro del pattern
            double euclideanDistance = Math.sqrt((px.x- x)*(px.x - x) + (px.y - y)*(px.y - y));

            // Elimina punti fuori dal raggio o in OutOfBounds
            if (euclideanDistance < radious ||
                    px.x < lowerBoundX || px.x >= upperBoundX ||
                    px.y < lowerBoundY || px.y >= upperBoundY){
                itr.remove();
            }}


        return pattern;
    }


    /**
     * Applica una maschera contornata ad una bitmap. Rende trasparente le parti di sfondo e colora
     * le parti di contorno.
     * @param bmp la bitmap da mascherare della stessa dimensione della maschera.
     * @param matrixMask la matrice-maschera della stessa dimensione della bitmap.
     * @param bgColor il colore di sfondo della matrice-maschera.
     * @param borderColor il colore di bordo della matrice-maschera da riportare sulla bitmap.
     * @return la bitmap mascherata e contornata, trasparente nello sfondo.
     * @throws IllegalArgumentException se le dimensioni della matrice e della bitmap non coincidono.
     */
    public static Bitmap applyMask(Bitmap bmp, int[][] matrixMask, int targetWidth, int targetHeight, int bgColor, int borderColor){

        // Controllo sulle dimensioni
        if (bmp.getWidth() != matrixMask[0].length || bmp.getHeight() != matrixMask.length) {
            throw new IllegalArgumentException();
        }

        // Rendi la bitmap mutable se non lo è già
        if (!bmp.isMutable()) bmp = bmp.copy(Bitmap.Config.ARGB_8888,true);

        for (int i = 0; i < targetHeight; i++) {
            for (int j = 0; j < targetWidth; j++) {

                try {
                    // Trovato sfondo
                    if (matrixMask[i][j] == Color.TRANSPARENT) bmp.setPixel(i, j, bgColor); //TODO:!!!
                    // Trovato contorno
                    else if (matrixMask[i][j] == borderColor) bmp.setPixel(i, j, borderColor);

                } catch (IllegalArgumentException e){ //TODO: ??
                    //Log.d(TAG,"IllegalArgument");
                }
            }
        }


        return bmp;
    }
}
