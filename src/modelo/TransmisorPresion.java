package modelo;

import java.time.LocalDate;

public class TransmisorPresion extends Instrumento {

    private static final long serialVersionUID = 1L;

    private final String tipoPresion; // manometrica, diferencial, absoluta

    public TransmisorPresion(String tag, String descripcion, double rangoMin, double rangoMax,
                             LocalDate ultimaCalibracion, String tipoPresion) {
        super(TipoMedicion.PRESION, tag, descripcion, rangoMin, rangoMax, ultimaCalibracion);
        this.tipoPresion = tipoPresion == null || tipoPresion.isBlank() ? "manometrica" : tipoPresion.trim();
    }

    @Override public String getPrincipioMedicion()       { return "presion " + tipoPresion; }
    @Override public double getToleranciaCalibracionPct(){ return 0.25; }
    @Override public int getIntervaloCalibracionDias()   { return 365; }

    public String getTipoPresion() { return tipoPresion; }
}
