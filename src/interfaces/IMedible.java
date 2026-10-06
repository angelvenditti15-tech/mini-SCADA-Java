package interfaces;

import excepciones.FallaDeLazoException;
import modelo.EstadoAlarma;
import modelo.Lectura;

import java.time.LocalDateTime;

/** Todo lo que entrega una medicion y sabe evaluar sus alarmas. */
public interface IMedible {
    Lectura procesarSenal(double miliamperios, LocalDateTime momento) throws FallaDeLazoException;
    EstadoAlarma evaluarAlarma(double valor, EstadoAlarma estadoPrevio);
}
