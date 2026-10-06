package servicio;

import excepciones.*;
import modelo.Instrumento;
import util.Conversiones;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Genera senales 4-20 mA verosimiles para todos los instrumentos en servicio:
 * una variable que fluctua alrededor del 50 % del rango, con perturbaciones ocasionales
 * (que disparan alarmas) y fallas de lazo muy poco frecuentes. Con la misma semilla produce
 * siempre la misma secuencia, lo que permite probarlo.
 */
public class SimuladorProceso {

    public record Resumen(int lecturas, int fallasDeLazo, int alarmasNuevas) { }

    private final Planta planta;
    private final Random azar;
    private final Map<String, Double> valorActual = new HashMap<>();

    public SimuladorProceso(Planta planta, long semilla) {
        this.planta = planta;
        this.azar = new Random(semilla);
    }

    public Resumen ejecutar(int ciclos) {
        int alarmasAntes = planta.getHistorialAlarmas().size();
        int lecturas = 0;
        int fallas = 0;

        for (int c = 0; c < ciclos; c++) {
            for (Instrumento inst : planta.getTablero()) {
                double mA = siguienteSenal(inst);
                try {
                    planta.registrarLectura(inst.getTag(), mA);
                    lecturas++;
                } catch (FallaDeLazoException e) {
                    fallas++;
                } catch (OperacionInvalidaException e) {
                    // instrumento fuera de servicio o en mantenimiento: no entrega lectura
                } catch (EntidadNoEncontradaException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return new Resumen(lecturas, fallas, planta.getHistorialAlarmas().size() - alarmasAntes);
    }

    private double siguienteSenal(Instrumento inst) {
        double min = inst.getRangoMin();
        double max = inst.getRangoMax();
        double span = max - min;
        double nominal = min + 0.5 * span;

        double v = valorActual.getOrDefault(inst.getTag(), nominal);
        v += (nominal - v) * 0.25 + azar.nextGaussian() * 0.015 * span;      // regresa al nominal + ruido
        if (azar.nextDouble() < 0.04) {                                       // perturbacion de proceso
            v += (azar.nextBoolean() ? 1 : -1) * (0.30 + azar.nextDouble() * 0.15) * span;
        }
        valorActual.put(inst.getTag(), v);

        if (azar.nextDouble() < 0.01) {                                       // lazo abierto o cortocircuito
            return azar.nextBoolean() ? 2.0 : 22.5;
        }
        double mA = Conversiones.valorAmA(v, min, max);
        // un transmisor real satura en 3.8 / 20.5 mA
        return Math.max(Conversiones.MA_RANGO_BAJO, Math.min(Conversiones.MA_RANGO_ALTO, mA));
    }
}
