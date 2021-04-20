package com.learning.android.stickercreator.stickerprocessing;

public class ConnectedComponentsLabeler {
    /**
     * Classe che definisce un etichettatore di componenti connesse
     * per un array 2-dimensionale con nozione di sfondo. Implementa l'algoritmo
     * di Hoshen-Kopelman per etichettare le componenti.
     */

    // Etichette delle componenti. mLabels[0] è il numero di componenti trovate
    private int[] mLabels;
    private int[] clusterSizes;

    // La matrice da etichettare ..
    private int[][] mMatBitmap;
    // .. e le sue dimensioni
    private int mWidth;
    private int mHeight;

    /**
     * Costruttore della classe. Esegue l'etichettatura.
     * @param matBitmap la matrice da etichettare, avente 0 negli elementi considerati sfondo
     */
    public ConnectedComponentsLabeler(int[][] matBitmap){
        this.mMatBitmap = matBitmap;
        this.mHeight = matBitmap.length;
        this.mWidth = matBitmap[0].length;

        this.mLabels = new int[ matBitmap.length * matBitmap[0].length];
        //mLabels[0]=0;: implicito

        // Esegue l'etichettatura
        this.label();
    }


    /**
     * Filtra la matrice lasciando solo la componente connessa più estesa, rietichettando
     * sia quest'ultima che lo sfondo.
     * Esegue l'etichettamento se non effettuato.
     * @param fgLabel l'etichetta per la componente connessa più estesa
     * @param bgLabel l'etichetta per lo sfondo
     * @param tollerance tolleranza in percentuale sulla dimensione della componente connessa più grande
     * @return la matrice originale rietichettata. null se la dimensione della componente più grande è
     * troppo poco estesa, sotto la tolleranza impostata
     */
    public int[][] largestConnectedComponentFilter(int fgLabel, int bgLabel, int tollerance) {

        // Esegui l'etichettamento se non eseguito
        if (mLabels[0] == 0) this.label();

        if (tollerance > 100 || tollerance < 0) throw new IllegalArgumentException();

        // Conta per ogni componente il numero di pixel
        int largestConnectedComponent = findLargestConnectedComponent();

        // Controllo sulla tolleranza
        if ( (clusterSizes[largestConnectedComponent]+0.0)/(mWidth*mHeight)*100 < tollerance) return null;

        // Scansione della matrice con rietichettamento secondo specifiche
        for (int i = 0; i< mHeight; i++) {
            for (int j = 0; j < mWidth; j++) {
                if (mMatBitmap[i][j] == largestConnectedComponent) mMatBitmap[i][j] = fgLabel;
                else if (mMatBitmap[i][j] != 0 || mMatBitmap[i][j] == 0) mMatBitmap[i][j] = bgLabel;
            }
        }


        return mMatBitmap;
    }

    /**
     * Trova la più grande componente connessa non di sfondo.
     * Esegue l'etichettamento se non effettuato.
     * @return l'indice della più grande componente connessa non di sfondo.
     */
    private int findLargestConnectedComponent(){

        if (mLabels[0] == 0) this.label();

        // Conteggio della dimensione delle componenti
        clusterSizes = new int[ mWidth * mHeight];

        for (int[] row : mMatBitmap) {
            for (int lable : row) {
                clusterSizes[lable]++;
            }
        }
        // Si azzera la prima poichè di sfondo
        clusterSizes[0]=0;

        // Ricerca dell'indice con maggiori occorrenze
        int max = 0;
        int maxIndex = 0;
        for (int i=0; i<clusterSizes.length; i++){
            if (clusterSizes[i] > max) {
                max = clusterSizes[i];
                maxIndex = i;
            }
        }


        return maxIndex;
    }

    /**
     * Applica l'algoritmo di Hoshen-Kopelman per etichettare le
     *  componenti connesse.
     */
    private void label(){

        // Scansione della matrice
        for (int i = 0; i< mHeight; i++)
            for (int j = 0; j < mWidth; j++)

                // Trovato un pixel non di sfondo
                if (mMatBitmap[i][j] != 0) {

                    int up = (i == 0 ? 0 : mMatBitmap[i - 1][j]);
                    int left = (j == 0 ? 0 : mMatBitmap[i][j - 1]);

                    // Nuovo cluster
                    if (up == 0 && left == 0) mMatBitmap[i][j] = makeSet();

                    // Trovati due cluster: uniscili
                    else if (up > 0 && left > 0) mMatBitmap[i][j] = union(up, left);

                    // Trovato cluster o a sinistra o sopra
                    else mMatBitmap[i][j] = Math.max(up,left);
                }

        // Rietichetta tutte le componenti connesse
        int[] newLabels = new int[mLabels.length];

        // Scansione della matrice per pixel
        for (int i = 0; i< mHeight; i++)
            for (int j = 0; j< mWidth; j++)

                // Trovato un pixel non di sfondo
                if (mMatBitmap[i][j] != 0) {

                    int x = find(mMatBitmap[i][j]);

                    // Crea una nuova etichetta se sprovvisto e ri-assegna
                    if (newLabels[x] == 0) {
                        newLabels[0]++;
                        newLabels[x] = newLabels[0];
                    }
                    mMatBitmap[i][j] = newLabels[x];
                }

        mLabels = newLabels;
    }


    /**
     * Classico metodo find di un generico algoritmo union-find based che trova
     * il punto più rappresentativo del set.
     * @param x il punto del set.
     * @return il punto più rappresentativo del set.
     */
    private int find(int x) {
        while (mLabels[x] != x) x = mLabels[x];


        return x;
    }

    /**
     * Metodo union di un generico algoritmo union-find based che unisce due set
     * disgiunti. Non possiede la nozione di rank o depth in quanto non esiste un
     * costo da minimizzare, ma solo componenti da etichettare.
     * @param x un punto di un set.
     * @param y un punto di un set.
     * @return il punto più rappresentativo dell'unione dei due set.
     */
    private int union(int x, int y) {
        int label = find(y);
        mLabels[find(x)] = label;


        return label;
    }

    /**
     * Classico metodo makeSe di un generico algoritmo union-find based che
     * crea un nuovo set.
     * @return il set appena creato.
     */
    private int makeSet() {
        mLabels[0]++;
        mLabels[mLabels[0]] = mLabels[0];


        return mLabels[0];
    }
}
