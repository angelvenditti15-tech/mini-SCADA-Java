package modelo;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Una medicion puntual: senal en mA, valor de proceso resultante y calidad. */
public record Lectura(
        String tag,
        double valor,
        double miliamperios,
        CalidadSenal calidad,
        LocalDateTime momento) implements Serializable {
}
