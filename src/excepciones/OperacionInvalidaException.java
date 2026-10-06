package excepciones;

/** La operacion no corresponde al estado actual (instrumento fuera de servicio, alarma ya reconocida, etc.). */
public class OperacionInvalidaException extends Exception {
    private static final long serialVersionUID = 1L;

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
