package com.learning.android.stickercreator.stickerprocessing;

public class Pixel {
    /**
     * Classe involucro di ascissa e ordinata che identificano
     *  un pixel bidimensionale.
     */

    // Lasciati pubblici per chiarezza del codice che utilizzerà questa classe
    public int x;
    public int y;

    /**
     * Costruttore della classe.
     * @param x l'ascissa
     * @param y l'ordinata
     */
    public Pixel(int x, int y){
        this.x = x;
        this.y = y;
    }
}
