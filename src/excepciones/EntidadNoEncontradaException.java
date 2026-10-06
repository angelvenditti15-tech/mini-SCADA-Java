package excepciones;

/** Se busca un instrumento o una alarma que no existe. */
public class EntidadNoEncontradaException extends Exception {
    private static final long serialVersionUID = 1L;

    public EntidadNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
