package modelo;

import excepciones.FallaDeLazoException;
import interfaces.ICalibrable;
import interfaces.IMedible;
import util.Conversiones;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * Transmisor de campo con salida 4-20 mA. Las subclases definen el tipo de variable,
 * el principio de medicion, la tolerancia y el intervalo de calibracion.
 */
public abstract class Instrumento implements IMedible, ICalibrable, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Pattern FORMATO_TAG = Pattern.compile("^[A-Z]{2,4}-\\d{3,4}[A-Z]?$");

    private final TipoMedicion tipo;
    private final String tag;
    private final String descripcion;
    private final double rangoMin;
    private final double rangoMax;

    private double spBajoBajo;
    private double spBajo;
    private double spAlto;
    private double spAltoAlto;
    private double histeresisPct;

    private LocalDate ultimaCalibracion;
    private EstadoServicio estadoServicio = EstadoServicio.EN_SERVICIO;
    private EstadoAlarma estadoAlarma = EstadoAlarma.NORMAL;
    private Double ultimoValor;
    private CalidadSenal ultimaCalidad;

    protected Instrumento(TipoMedicion tipo, String tag, String descripcion,
                          double rangoMin, double rangoMax, LocalDate ultimaCalibracion) {
        if (tag == null) throw new IllegalArgumentException("El tag no puede ser nulo.");
        String tagNormalizado = tag.trim().toUpperCase();
        if (!FORMATO_TAG.matcher(tagNormalizado).matches()) {
            throw new IllegalArgumentException("Formato de tag invalido: '" + tag + "'. Ejemplo valido: " + tipo.getPrefijo() + "-101");
        }
        if (!tagNormalizado.startsWith(tipo.getPrefijo() + "-")) {
            throw new IllegalArgumentException("El tag de un instrumento de " + tipo.getNombre().toLowerCase()
                + " debe empezar con " + tipo.getPrefijo() + "- (ISA 5.1).");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion no puede estar vacia.");
        }
        if (rangoMax <= rangoMin) {
            throw new IllegalArgumentException("El rango maximo debe ser mayor al minimo.");
        }
        if (ultimaCalibracion == null) {
            throw new IllegalArgumentException("Falta la fecha de ultima calibracion.");
        }
        this.tipo = tipo;
        this.tag = tagNormalizado;
        this.descripcion = descripcion.trim();
        this.rangoMin = rangoMin;
        this.rangoMax = rangoMax;
        this.ultimaCalibracion = ultimaCalibracion;

        // Alarmas por defecto: LL 5 %, L 15 %, H 85 %, HH 95 % del span, histeresis 2 %
        double span = rangoMax - rangoMin;
        configurarAlarmas(rangoMin + 0.05 * span, rangoMin + 0.15 * span,
                          rangoMin + 0.85 * span, rangoMin + 0.95 * span, 2.0);
    }

    // ---------- Datos propios de cada tipo de instrumento ----------

    public abstract String getPrincipioMedicion();
    public abstract double getToleranciaCalibracionPct();
    public abstract int getIntervaloCalibracionDias();

    // ---------- Alarmas ----------

    /** Define los setpoints LL < L < H < HH y la histeresis (% del span) que evita alarmas intermitentes. */
    public final void configurarAlarmas(double bajoBajo, double bajo, double alto, double altoAlto, double histeresisPct) {
        if (!(rangoMin <= bajoBajo && bajoBajo < bajo && bajo < alto && alto < altoAlto && altoAlto <= rangoMax)) {
            throw new IllegalArgumentException("Los setpoints deben cumplir: min <= LL < L < H < HH <= max.");
        }
        if (histeresisPct < 0) {
            throw new IllegalArgumentException("La histeresis no puede ser negativa.");
        }
        double h = histeresisPct / 100.0 * (rangoMax - rangoMin);
        double menorSeparacion = Math.min(bajo - bajoBajo, Math.min(alto - bajo, altoAlto - alto));
        if (h >= menorSeparacion / 2.0) {
            throw new IllegalArgumentException("La histeresis es demasiado grande para la separacion entre setpoints.");
        }
        this.spBajoBajo = bajoBajo;
        this.spBajo = bajo;
        this.spAlto = alto;
        this.spAltoAlto = altoAlto;
        this.histeresisPct = histeresisPct;
    }

    /**
     * Evalua el estado de alarma con histeresis: una alarma activa solo se despeja cuando el valor
     * vuelve mas alla del setpoint por el margen de histeresis. Asi una senal que oscila justo en el
     * setpoint no genera decenas de alarmas.
     */
    @Override
    public EstadoAlarma evaluarAlarma(double valor, EstadoAlarma estadoPrevio) {
        double h = histeresisPct / 100.0 * (rangoMax - rangoMin);
        double limAltoAlto = estadoPrevio == EstadoAlarma.ALTA_ALTA ? spAltoAlto - h : spAltoAlto;
        double limAlto = (estadoPrevio == EstadoAlarma.ALTA || estadoPrevio == EstadoAlarma.ALTA_ALTA) ? spAlto - h : spAlto;
        double limBajoBajo = estadoPrevio == EstadoAlarma.BAJA_BAJA ? spBajoBajo + h : spBajoBajo;
        double limBajo = (estadoPrevio == EstadoAlarma.BAJA || estadoPrevio == EstadoAlarma.BAJA_BAJA) ? spBajo + h : spBajo;

        if (valor >= limAltoAlto) return EstadoAlarma.ALTA_ALTA;
        if (valor >= limAlto)     return EstadoAlarma.ALTA;
        if (valor <= limBajoBajo) return EstadoAlarma.BAJA_BAJA;
        if (valor <= limBajo)     return EstadoAlarma.BAJA;
        return EstadoAlarma.NORMAL;
    }

    // ---------- Senal 4-20 mA ----------

    @Override
    public Lectura procesarSenal(double miliamperios, LocalDateTime momento) throws FallaDeLazoException {
        if (miliamperios < Conversiones.MA_FALLA_BAJA || miliamperios > Conversiones.MA_FALLA_ALTA) {
            throw new FallaDeLazoException(tag, miliamperios);
        }
        double valor = Conversiones.mAAValor(miliamperios, rangoMin, rangoMax);
        boolean fueraDeRango = miliamperios < Conversiones.MA_RANGO_BAJO || miliamperios > Conversiones.MA_RANGO_ALTO;
        CalidadSenal calidad = fueraDeRango ? CalidadSenal.FUERA_DE_RANGO : CalidadSenal.BUENA;
        this.ultimoValor = valor;
        this.ultimaCalidad = calidad;
        return new Lectura(tag, valor, miliamperios, calidad, momento);
    }

    public void marcarFallaDeLazo() {
        this.ultimaCalidad = CalidadSenal.FALLA;
    }

    // ---------- Calibracion ----------

    /** Error maximo entre lo que marca el instrumento y el patron, expresado en % del span. */
    @Override
    public double calcularErrorMaximoPct(double[] valoresPatron, double[] valoresLeidos) {
        if (valoresPatron == null || valoresLeidos == null
                || valoresPatron.length != valoresLeidos.length || valoresPatron.length < 2) {
            throw new IllegalArgumentException("Se necesitan al menos 2 puntos, con la misma cantidad de valores patron y leidos.");
        }
        double span = rangoMax - rangoMin;
        double errorMax = 0;
        for (int i = 0; i < valoresPatron.length; i++) {
            errorMax = Math.max(errorMax, Math.abs(valoresLeidos[i] - valoresPatron[i]) / span * 100.0);
        }
        return errorMax;
    }

    @Override
    public long diasParaVencerCalibracion(LocalDate hoy) {
        LocalDate vencimiento = ultimaCalibracion.plusDays(getIntervaloCalibracionDias());
        return ChronoUnit.DAYS.between(hoy, vencimiento);
    }

    @Override
    public boolean calibracionVencida(LocalDate hoy) {
        return diasParaVencerCalibracion(hoy) < 0;
    }

    public void registrarCalibracionAprobada(LocalDate fecha) {
        this.ultimaCalibracion = fecha;
    }

    // ---------- Presentacion ----------

    public void mostrarInfo() {
        System.out.println("[" + tag + "] " + descripcion);
        System.out.printf("   Tipo: %s (%s)%n", tipo.getNombre(), getPrincipioMedicion());
        System.out.printf("   Rango: %.2f a %.2f %s  (4-20 mA)%n", rangoMin, rangoMax, tipo.getUnidad());
        System.out.printf("   Alarmas: LL=%.2f  L=%.2f  H=%.2f  HH=%.2f  (histeresis %.1f%%)%n",
            spBajoBajo, spBajo, spAlto, spAltoAlto, histeresisPct);
        System.out.printf("   Calibracion: ultima %s | tolerancia +/-%.2f%% | cada %d dias%n",
            ultimaCalibracion, getToleranciaCalibracionPct(), getIntervaloCalibracionDias());
        System.out.println("   Servicio: " + estadoServicio);
    }

    // ---------- Getters / setters ----------

    public TipoMedicion getTipoMedicion()     { return tipo; }
    public String getTag()                    { return tag; }
    public String getDescripcion()            { return descripcion; }
    public String getUnidad()                 { return tipo.getUnidad(); }
    public double getRangoMin()               { return rangoMin; }
    public double getRangoMax()               { return rangoMax; }
    public double getSpBajoBajo()             { return spBajoBajo; }
    public double getSpBajo()                 { return spBajo; }
    public double getSpAlto()                 { return spAlto; }
    public double getSpAltoAlto()             { return spAltoAlto; }
    public double getHisteresisPct()          { return histeresisPct; }
    public LocalDate getUltimaCalibracion()   { return ultimaCalibracion; }
    public EstadoServicio getEstadoServicio() { return estadoServicio; }
    public EstadoAlarma getEstadoAlarma()     { return estadoAlarma; }
    public Double getUltimoValor()            { return ultimoValor; }
    public CalidadSenal getUltimaCalidad()    { return ultimaCalidad; }

    public void setEstadoServicio(EstadoServicio estadoServicio) { this.estadoServicio = estadoServicio; }
    public void actualizarEstadoAlarma(EstadoAlarma estado)      { this.estadoAlarma = estado; }
}
