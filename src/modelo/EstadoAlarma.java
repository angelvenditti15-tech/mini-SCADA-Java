package modelo;

/** Estado de alarma de un instrumento. LL/HH son criticas (muy bajo / muy alto). */
public enum EstadoAlarma {
    NORMAL(0, "NORMAL"),
    BAJA(1, "L"),
    ALTA(1, "H"),
    BAJA_BAJA(2, "LL"),
    ALTA_ALTA(2, "HH"),
    FALLA_LAZO(3, "FALLA");

    private final int prioridad;
    private final String etiqueta;

    EstadoAlarma(int prioridad, String etiqueta) {
        this.prioridad = prioridad;
        this.etiqueta = etiqueta;
    }

    public int getPrioridad()   { return prioridad; }
    public String getEtiqueta() { return etiqueta; }
    public boolean esCritica()  { return prioridad >= 2; }
}
