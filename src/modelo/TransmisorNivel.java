package modelo;

import java.time.LocalDate;

public class TransmisorNivel extends Instrumento {

    private static final long serialVersionUID = 1L;

    private final String principio; // radar, presion diferencial, ultrasonico

    public TransmisorNivel(String tag, String descripcion, double rangoMin, double rangoMax,
                           LocalDate ultimaCalibracion, String principio) {
        super(TipoMedicion.NIVEL, tag, descripcion, rangoMin, rangoMax, ultimaCalibracion);
        this.principio = principio == null || principio.isBlank() ? "radar" : principio.trim();
    }

    @Override public String getPrincipioMedicion()       { return principio; }
    @Override public double getToleranciaCalibracionPct(){ return 0.5; }
    @Override public int getIntervaloCalibracionDias()   { return 365; }
}
