package modelo;

/** Variable de proceso medida. El prefijo sigue la nomenclatura ISA 5.1 (PT, TT, LT, FT). */
public enum TipoMedicion {
    PRESION("PT", "bar", "Presion"),
    TEMPERATURA("TT", "C", "Temperatura"),
    NIVEL("LT", "%", "Nivel"),
    CAUDAL("FT", "m3/h", "Caudal");

    private final String prefijo;
    private final String unidad;
    private final String nombre;

    TipoMedicion(String prefijo, String unidad, String nombre) {
        this.prefijo = prefijo;
        this.unidad = unidad;
        this.nombre = nombre;
    }

    public String getPrefijo() { return prefijo; }
    public String getUnidad()  { return unidad; }
    public String getNombre()  { return nombre; }
}
