package modelo;

import java.io.Serializable;
import java.time.LocalDate;

/** Resultado de una verificacion de calibracion (error maximo encontrado vs tolerancia del instrumento). */
public record RegistroCalibracion(
        String tag,
        LocalDate fecha,
        String tecnico,
        double errorMaximoPct,
        double toleranciaPct,
        boolean aprobada,
        String observaciones) implements Serializable {
}
