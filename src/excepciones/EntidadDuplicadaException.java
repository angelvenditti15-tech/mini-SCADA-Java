package excepciones;

/** Se intenta registrar un instrumento con un tag que ya existe. */
public class EntidadDuplicadaException extends Exception {
    private static final long serialVersionUID = 1L;

    public EntidadDuplicadaException(String mensaje) {
        super(mensaje);
    }
}
