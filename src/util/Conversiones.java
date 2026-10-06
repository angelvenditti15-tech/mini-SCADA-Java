package util;

/**
 * Conversiones tipicas de instrumentacion: senal 4-20 mA, unidades de ingenieria y Pt100.
 * Todas son funciones puras (sin estado), por eso son faciles de probar.
 */
public final class Conversiones {

    private Conversiones() { }

    // Umbrales de diagnostico del lazo (criterio tipo NAMUR NE43)
    public static final double MA_FALLA_BAJA = 3.6;
    public static final double MA_RANGO_BAJO = 3.8;
    public static final double MA_RANGO_ALTO = 20.5;
    public static final double MA_FALLA_ALTA = 21.0;

    // Coeficientes Callendar-Van Dusen para Pt100 (IEC 60751)
    private static final double R0 = 100.0;
    private static final double A = 3.9083e-3;
    private static final double B = -5.775e-7;
    private static final double C = -4.183e-12;

    /** Convierte una senal 4-20 mA al valor de proceso segun el rango calibrado. */
    public static double mAAValor(double mA, double rangoMin, double rangoMax) {
        return rangoMin + (mA - 4.0) / 16.0 * (rangoMax - rangoMin);
    }

    /** Convierte un valor de proceso a la senal 4-20 mA que deberia entregar el transmisor. */
    public static double valorAmA(double valor, double rangoMin, double rangoMax) {
        return 4.0 + (valor - rangoMin) / (rangoMax - rangoMin) * 16.0;
    }

    public static double mAAPorcentaje(double mA) {
        return (mA - 4.0) / 16.0 * 100.0;
    }

    public static double porcentajeAmA(double porcentaje) {
        return 4.0 + porcentaje / 100.0 * 16.0;
    }

    /** Devuelve n valores equidistantes entre min y max (incluye ambos extremos). Ej: 0, 25, 50, 75, 100 %. */
    public static double[] puntosPatron(double rangoMin, double rangoMax, int n) {
        if (n < 2) throw new IllegalArgumentException("Se necesitan al menos 2 puntos.");
        double[] puntos = new double[n];
        for (int i = 0; i < n; i++) {
            puntos[i] = rangoMin + (rangoMax - rangoMin) * i / (n - 1);
        }
        return puntos;
    }

    public static double barAPsi(double bar) { return bar * 14.5037738; }
    public static double psiABar(double psi) { return psi / 14.5037738; }
    public static double celsiusAFahrenheit(double c) { return c * 9.0 / 5.0 + 32.0; }
    public static double fahrenheitACelsius(double f) { return (f - 32.0) * 5.0 / 9.0; }

    /** Resistencia (ohm) de una Pt100 a la temperatura dada (-200 a 850 C). */
    public static double pt100AOhm(double celsius) {
        if (celsius >= 0) {
            return R0 * (1 + A * celsius + B * celsius * celsius);
        }
        return R0 * (1 + A * celsius + B * celsius * celsius + C * (celsius - 100) * Math.pow(celsius, 3));
    }

    /** Temperatura (C) a partir de la resistencia de una Pt100. Se resuelve por biseccion (la curva es monotona). */
    public static double ohmAPt100(double ohm) {
        double bajo = -200.0;
        double alto = 850.0;
        if (ohm < pt100AOhm(bajo) || ohm > pt100AOhm(alto)) {
            throw new IllegalArgumentException(String.format(
                "Resistencia fuera del rango de una Pt100 (%.2f a %.2f ohm).", pt100AOhm(bajo), pt100AOhm(alto)));
        }
        for (int i = 0; i < 100; i++) {
            double medio = (bajo + alto) / 2.0;
            if (pt100AOhm(medio) < ohm) bajo = medio; else alto = medio;
        }
        return (bajo + alto) / 2.0;
    }
}
