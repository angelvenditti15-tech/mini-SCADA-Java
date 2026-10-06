package interfaces;

import java.time.LocalDate;

/** Todo lo que requiere calibracion periodica. */
public interface ICalibrable {
    double calcularErrorMaximoPct(double[] valoresPatron, double[] valoresLeidos);
    boolean calibracionVencida(LocalDate hoy);
    long diasParaVencerCalibracion(LocalDate hoy);
}
