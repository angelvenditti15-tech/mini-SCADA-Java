package excepciones;

/**
 * La senal del lazo esta fuera de los limites de falla (criterio tipo NAMUR NE43):
 * menos de 3.6 mA suele indicar lazo abierto o transmisor fallado,
 * mas de 21 mA suele indicar cortocircuito o falla del transmisor.
 */
public class FallaDeLazoException extends Exception {
    private static final long serialVersionUID = 1L;

    private final double miliamperios;

    public FallaDeLazoException(String tag, double miliamperios) {
        super(String.format("Falla de lazo en %s: senal de %.2f mA fuera de limites validos.", tag, miliamperios));
        this.miliamperios = miliamperios;
    }

    public double getMiliamperios() { return miliamperios; }
}
