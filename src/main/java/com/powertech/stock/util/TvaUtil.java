package com.powertech.stock.util;

public class TvaUtil {
    public static final double TAUX_TVA = 0.18;

    public static double calculerTVA(double montantHT) {
        return montantHT * TAUX_TVA;
    }

    public static double calculerTTC(double montantHT) {
        return montantHT * (1 + TAUX_TVA);
    }
}
