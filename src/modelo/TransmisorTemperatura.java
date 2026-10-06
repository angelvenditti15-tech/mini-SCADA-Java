package modelo;

import java.time.LocalDate;

public class TransmisorTemperatura extends Instrumento {

    private static final long serialVersionUID = 1L;

    private final String sensor; // RTD Pt100, termocupla K, etc.

    public TransmisorTemperatura(String tag, String descripcion, double rangoMin, double rangoMax,
                                 LocalDate ultimaCalibracion, String sensor) {
        super(TipoMedicion.TEMPERATURA, tag, descripcion, rangoMin, rangoMax, ultimaCalibracion);
        this.sensor = sensor == null || sensor.isBlank() ? "RTD Pt100" : sensor.trim();
    }

    @Override public String getPrincipioMedicion()       { return "sensor " + sensor; }
    @Override public double getToleranciaCalibracionPct(){ return 0.5; }
    @Override public int getIntervaloCalibracionDias()   { return 180; }

    public String getSensor() { return sensor; }
}
