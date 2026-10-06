import excepciones.*;
import modelo.*;
import servicio.Planta;
import servicio.SimuladorProceso;
import util.Conversiones;

import java.nio.file.*;
import java.time.*;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.stream.Stream;

/** Pruebas sin dependencias externas. Ejecutar con:  java -ea -cp bin PruebasPlanta */
public class PruebasPlanta {

    private static int ok = 0;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-05T13:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDate HOY = LocalDate.of(2026, 10, 5);

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("planta-test");
        try {
            conversiones();
            validacionDeInstrumentos();
            senalYCalidad(temp);
            alarmasEHisteresis(temp);
            fallaDeLazo(temp);
            reconocimiento(temp);
            calibracion(temp);
            persistencia(temp);
            simulador(temp);
            System.out.println("\nTodas las pruebas pasaron (" + ok + " verificaciones).");
        } finally {
            try (Stream<Path> s = Files.walk(temp)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void check(boolean condicion, String descripcion) {
        if (!condicion) throw new AssertionError("FALLO: " + descripcion);
        ok++;
        System.out.println("  OK  " + descripcion);
    }

    private static boolean cerca(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    private static Planta planta(Path temp, String carpeta) throws Exception {
        Planta p = new Planta(temp.resolve(carpeta).toString(), RELOJ);
        TransmisorPresion pt = new TransmisorPresion("PT-101", "Presion bomba", 0, 25, HOY.minusDays(100), "manometrica");
        pt.configurarAlarmas(2, 5, 20, 23, 2.0);   // histeresis = 0.5 bar
        p.registrarInstrumento(pt);
        p.registrarInstrumento(new TransmisorTemperatura("TT-201", "Horno", 0, 400, HOY.minusDays(170), "RTD Pt100"));
        return p;
    }

    private static double mA(double valor25) { return Conversiones.valorAmA(valor25, 0, 25); }

    // ------------------------------------------------------------------

    private static void conversiones() {
        System.out.println("Conversiones");
        check(Conversiones.mAAValor(4, 0, 100) == 0.0, "4 mA = 0 % del rango");
        check(Conversiones.mAAValor(20, 0, 100) == 100.0, "20 mA = 100 % del rango");
        check(Conversiones.mAAValor(12, 0, 25) == 12.5, "12 mA = punto medio");
        check(cerca(Conversiones.valorAmA(100, 0, 400), 8.0, 1e-9), "100 de 0-400 = 8 mA");
        check(cerca(Conversiones.mAAPorcentaje(8), 25.0, 1e-9), "8 mA = 25 %");
        check(cerca(Conversiones.pt100AOhm(0), 100.0, 1e-9), "Pt100 a 0 C = 100 ohm");
        check(cerca(Conversiones.pt100AOhm(100), 138.5055, 0.01), "Pt100 a 100 C = 138.51 ohm");
        check(cerca(Conversiones.pt100AOhm(-50), 80.31, 0.01), "Pt100 a -50 C = 80.31 ohm (rama negativa)");
        check(cerca(Conversiones.ohmAPt100(138.5055), 100.0, 0.01), "138.51 ohm = 100 C");
        check(cerca(Conversiones.ohmAPt100(80.31), -50.0, 0.05), "80.31 ohm = -50 C");
        try { Conversiones.ohmAPt100(5); check(false, "ohm invalido"); }
        catch (IllegalArgumentException e) { check(true, "rechaza resistencia fuera del rango Pt100"); }
        check(cerca(Conversiones.barAPsi(1), 14.5038, 0.001), "1 bar = 14.50 psi");
        check(cerca(Conversiones.celsiusAFahrenheit(100), 212.0, 1e-9), "100 C = 212 F");
        double[] p = Conversiones.puntosPatron(0, 100, 5);
        check(p.length == 5 && p[0] == 0 && p[2] == 50 && p[4] == 100, "puntos patron 0-25-50-75-100 %");
    }

    private static void validacionDeInstrumentos() {
        System.out.println("Validacion de instrumentos");
        try { new TransmisorPresion("TT-101", "x", 0, 10, HOY, "m"); check(false, "prefijo"); }
        catch (IllegalArgumentException e) { check(true, "el tag debe respetar el prefijo ISA del tipo (PT, TT, LT, FT)"); }
        try { new TransmisorPresion("PT101", "x", 0, 10, HOY, "m"); check(false, "formato"); }
        catch (IllegalArgumentException e) { check(true, "rechaza formato de tag invalido"); }
        check(new TransmisorPresion(" pt-101 ", "x", 0, 10, HOY, "m").getTag().equals("PT-101"), "normaliza el tag a mayusculas");
        try { new TransmisorNivel("LT-301", "x", 50, 50, HOY, "radar"); check(false, "rango"); }
        catch (IllegalArgumentException e) { check(true, "rechaza rango con max <= min"); }
        Instrumento i = new TransmisorCaudal("FT-401", "x", 0, 100, HOY, "vortex");
        try { i.configurarAlarmas(10, 5, 80, 90, 1); check(false, "orden"); }
        catch (IllegalArgumentException e) { check(true, "rechaza setpoints desordenados (LL < L < H < HH)"); }
        try { i.configurarAlarmas(10, 20, 80, 90, 6); check(false, "histeresis"); }
        catch (IllegalArgumentException e) { check(true, "rechaza histeresis mayor a la separacion entre setpoints"); }
        check(i.getSpBajoBajo() == 5.0 && i.getSpAltoAlto() == 95.0, "setpoints por defecto: LL 5 % y HH 95 %");
    }

    private static void senalYCalidad(Path temp) throws Exception {
        System.out.println("Senal y calidad");
        Planta p = planta(temp, "senal");
        Lectura l = p.registrarLectura("pt-101", 12.0);
        check(l.valor() == 12.5 && l.calidad() == CalidadSenal.BUENA, "12 mA en 0-25 bar = 12.5 bar, calidad BUENA");
        Lectura sub = p.registrarLectura("PT-101", 3.7);
        check(sub.calidad() == CalidadSenal.FUERA_DE_RANGO, "3.7 mA: fuera de rango pero sin falla");
        try { p.registrarLectura("XX-999", 12); check(false, "no existe"); }
        catch (EntidadNoEncontradaException e) { check(true, "tag inexistente lanza excepcion"); }
        DoubleSummaryStatistics e = p.getEstadisticas("PT-101");
        check(e.getCount() == 2, "el historial guarda las lecturas validas");
    }

    private static void alarmasEHisteresis(Path temp) throws Exception {
        System.out.println("Alarmas e histeresis (PT-101: LL 2, L 5, H 20, HH 23, hist 0.5 bar)");
        Planta p = planta(temp, "alarmas");
        Instrumento pt = p.buscarInstrumento("PT-101");

        p.registrarLectura("PT-101", mA(10));
        check(pt.getEstadoAlarma() == EstadoAlarma.NORMAL && p.getHistorialAlarmas().isEmpty(), "10 bar: normal, sin alarmas");

        p.registrarLectura("PT-101", mA(20.5));
        check(pt.getEstadoAlarma() == EstadoAlarma.ALTA && p.getAlarmasActivas().size() == 1, "20.5 bar: alarma H");

        p.registrarLectura("PT-101", mA(19.8));
        check(pt.getEstadoAlarma() == EstadoAlarma.ALTA && p.getHistorialAlarmas().size() == 1,
              "19.8 bar: sigue en H por histeresis (no genera alarma nueva)");

        p.registrarLectura("PT-101", mA(19.4));
        check(pt.getEstadoAlarma() == EstadoAlarma.NORMAL && p.getAlarmasActivas().isEmpty(), "19.4 bar: vuelve a normal");
        check(p.getHistorialAlarmas().get(0).isNormalizada(), "la alarma H queda marcada como normalizada");

        p.registrarLectura("PT-101", mA(23.2));
        check(pt.getEstadoAlarma() == EstadoAlarma.ALTA_ALTA && pt.getEstadoAlarma().esCritica(), "23.2 bar: alarma HH (critica)");

        p.registrarLectura("PT-101", mA(20.2));
        check(pt.getEstadoAlarma() == EstadoAlarma.ALTA && p.getAlarmasActivas().size() == 1, "20.2 bar: baja de HH a H");

        p.registrarLectura("PT-101", mA(10));
        p.registrarLectura("PT-101", mA(1.0));
        check(pt.getEstadoAlarma() == EstadoAlarma.BAJA_BAJA, "1 bar: alarma LL");
        p.registrarLectura("PT-101", mA(2.3));
        check(pt.getEstadoAlarma() == EstadoAlarma.BAJA_BAJA, "2.3 bar: sigue en LL por histeresis (se despeja recien sobre 2.5)");
        p.registrarLectura("PT-101", mA(4.0));
        check(pt.getEstadoAlarma() == EstadoAlarma.BAJA, "4 bar: sale de LL y queda en L");
        p.registrarLectura("PT-101", mA(5.2));
        check(pt.getEstadoAlarma() == EstadoAlarma.BAJA, "5.2 bar: sigue en L por histeresis (se despeja recien sobre 5.5)");
        p.registrarLectura("PT-101", mA(6.0));
        check(pt.getEstadoAlarma() == EstadoAlarma.NORMAL && p.getAlarmasActivas().isEmpty(), "6 bar: vuelve a normal");
    }

    private static void fallaDeLazo(Path temp) throws Exception {
        System.out.println("Falla de lazo");
        Planta p = planta(temp, "falla");
        try { p.registrarLectura("PT-101", 2.0); check(false, "lazo abierto"); }
        catch (FallaDeLazoException e) { check(e.getMiliamperios() == 2.0, "menos de 3.6 mA lanza FallaDeLazoException"); }
        check(p.buscarInstrumento("PT-101").getEstadoAlarma() == EstadoAlarma.FALLA_LAZO, "el instrumento queda en FALLA_LAZO");
        check(p.getAlarmasActivas().size() == 1 && p.getAlarmasActivas().get(0).getEstado() == EstadoAlarma.FALLA_LAZO, "se genera una alarma de falla");
        try { p.registrarLectura("PT-101", 23.0); check(false, "cortocircuito"); }
        catch (FallaDeLazoException e) { check(true, "mas de 21 mA tambien es falla"); }
        check(p.getHistorialAlarmas().size() == 1, "una falla repetida no duplica la alarma");
        p.registrarLectura("PT-101", mA(10));
        check(p.getAlarmasActivas().isEmpty() && p.buscarInstrumento("PT-101").getEstadoAlarma() == EstadoAlarma.NORMAL,
              "al volver una senal valida, la falla se normaliza");
    }

    private static void reconocimiento(Path temp) throws Exception {
        System.out.println("Reconocimiento de alarmas");
        Planta p = planta(temp, "ack");
        p.registrarLectura("PT-101", mA(21));
        check(p.getAlarmasSinReconocer().size() == 1, "alarma nueva queda sin reconocer");
        p.reconocerAlarma(1, "angel");
        check(p.getAlarmasSinReconocer().isEmpty() && p.getHistorialAlarmas().get(0).getReconocidaPor().equals("angel"),
              "queda registrado quien la reconocio");
        try { p.reconocerAlarma(1, "otro"); check(false, "doble ack"); }
        catch (OperacionInvalidaException e) { check(true, "no se puede reconocer dos veces"); }
        try { p.reconocerAlarma(99, "x"); check(false, "inexistente"); }
        catch (EntidadNoEncontradaException e) { check(true, "alarma inexistente lanza excepcion"); }
        p.registrarLectura("PT-101", mA(10));
        p.registrarLectura("PT-101", mA(22));
        check(p.reconocerTodas("angel") == 1, "reconocerTodas devuelve cuantas estaban pendientes");
    }

    private static void calibracion(Path temp) throws Exception {
        System.out.println("Calibracion");
        Planta p = planta(temp, "calib");
        Instrumento pt = p.buscarInstrumento("PT-101");
        Instrumento tt = p.buscarInstrumento("TT-201");

        check(!pt.calibracionVencida(HOY) && pt.diasParaVencerCalibracion(HOY) == 265, "PT: 100 dias de 365 -> quedan 265");
        check(p.getCalibracionesProximasAVencer(30).contains(tt) && p.getCalibracionesVencidas().isEmpty(),
              "TT: 170 de 180 dias -> vence en 10 dias (proxima a vencer)");

        double[] patron = Conversiones.puntosPatron(0, 25, 5);
        double[] bien = {0.05, 6.30, 12.45, 18.80, 24.95};   // error maximo 0.05 bar = 0.2 % del span
        RegistroCalibracion ok1 = p.registrarCalibracion("PT-101", "angel", patron, bien, "sin novedades");
        check(ok1.aprobada() && cerca(ok1.errorMaximoPct(), 0.2, 1e-9), "error 0.2 % <= tolerancia 0.25 % -> aprobada");
        check(pt.getUltimaCalibracion().equals(HOY) && pt.diasParaVencerCalibracion(HOY) == 365, "se actualiza la fecha de calibracion");

        double[] mal = {0.0, 6.25, 12.6, 18.75, 25.0};      // error 0.1 bar = 0.4 %
        RegistroCalibracion ko = p.registrarCalibracion("PT-101", "angel", patron, mal, "deriva en 50 %");
        check(!ko.aprobada() && pt.getEstadoServicio() == EstadoServicio.EN_MANTENIMIENTO, "error 0.4 % > 0.25 % -> rechazada y en mantenimiento");
        try { p.registrarLectura("PT-101", 12); check(false, "en mantenimiento"); }
        catch (OperacionInvalidaException e) { check(true, "un instrumento en mantenimiento no acepta lecturas"); }
        p.cambiarEstadoServicio("PT-101", EstadoServicio.EN_SERVICIO);
        check(p.registrarLectura("PT-101", 12) != null, "al volver a servicio acepta lecturas");
        check(p.getCalibracionesDe("PT-101").size() == 2, "historial de calibraciones por tag");

        try { pt.calcularErrorMaximoPct(new double[]{1}, new double[]{1}); check(false, "pocos puntos"); }
        catch (IllegalArgumentException e) { check(true, "exige al menos 2 puntos de calibracion"); }

        Planta vieja = new Planta(temp.resolve("calib-vencida").toString(), RELOJ);
        vieja.registrarInstrumento(new TransmisorPresion("PT-500", "vieja", 0, 10, HOY.minusDays(400), "manometrica"));
        check(vieja.getCalibracionesVencidas().size() == 1, "calibracion de 400 dias detectada como vencida");
    }

    private static void persistencia(Path temp) throws Exception {
        System.out.println("Persistencia");
        Planta a = planta(temp, "persist");
        a.registrarLectura("PT-101", mA(21));
        a.registrarCalibracion("TT-201", "angel", Conversiones.puntosPatron(0, 400, 5),
            Conversiones.puntosPatron(0, 400, 5), "ok");
        a.guardarDatos();

        Planta b = new Planta(temp.resolve("persist").toString(), RELOJ);
        check(b.getTablero().size() == 2, "se recuperan los instrumentos");
        check(b.getHistorialAlarmas().size() == 1, "se recuperan las alarmas");
        check(b.getCalibraciones().size() == 1, "se recuperan las calibraciones");
        check(b.buscarInstrumento("PT-101").getEstadoAlarma() == EstadoAlarma.ALTA, "el instrumento conserva su estado de alarma");
        b.registrarLectura("PT-101", mA(10));
        b.registrarLectura("PT-101", mA(22));
        check(b.getHistorialAlarmas().get(1).getId() == 2, "los ids de alarma continuan desde el ultimo guardado");
    }

    private static void simulador(Path temp) throws Exception {
        System.out.println("Simulador de proceso");
        Planta a = new Planta(temp.resolve("sim-a").toString(), RELOJ);
        Planta b = new Planta(temp.resolve("sim-b").toString(), RELOJ);
        a.cargarDatosDeEjemplo();
        b.cargarDatosDeEjemplo();
        SimuladorProceso.Resumen ra = new SimuladorProceso(a, 7).ejecutar(300);
        SimuladorProceso.Resumen rb = new SimuladorProceso(b, 7).ejecutar(300);
        check(ra.lecturas() > 1000, "300 ciclos x 5 instrumentos generan lecturas (" + ra.lecturas() + ")");
        check(ra.alarmasNuevas() > 0, "la simulacion dispara alarmas (" + ra.alarmasNuevas() + ")");
        check(ra.equals(rb), "misma semilla -> mismos resultados (determinista)");
        check(ra.lecturas() + ra.fallasDeLazo() == 1500, "cada intento es lectura valida o falla de lazo");
    }
}
