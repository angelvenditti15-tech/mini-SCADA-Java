package modelo;

import java.time.LocalDate;

public class TransmisorCaudal extends Instrumento {

    private static final long serialVersionUID = 1L;

    private final String principio; // vortex, coriolis, placa orificio

    public TransmisorCaudal(String tag, String descripcion, double rangoMin, double rangoMax,
                            LocalDate ultimaCalibracion, String principio) {
        super(TipoMedicion.CAUDAL, tag, descripcion, rangoMin, rangoMax, ultimaCalibracion);
        this.principio = principio == null || principio.isBlank() ? "vortex" : principio.trim();
    }

    @Override public String getPrincipioMedicion()       { return principio; }
    @Override public double getToleranciaCalibracionPct(){ return 1.0; }
    @Override public int getIntervaloCalibracionDias()   { return 180; }
}
