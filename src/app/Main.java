package app;

import excepciones.*;
import modelo.*;
import servicio.Planta;
import servicio.SimuladorProceso;
import util.Conversiones;

import java.time.format.DateTimeFormatter;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Interfaz de consola del sistema de monitoreo. Solo pide datos y muestra resultados. */
public class Main {

    private static final Planta planta = new Planta();
    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    private static String operador = "operador";
    private static long semillaSimulacion = 2026;

    public static void main(String[] args) {
        System.out.println("=== MONITOREO DE INSTRUMENTACION ===");
        try {
            operador = leerTexto("Nombre del operador: ");
            if (planta.estaVacia()) {
                System.out.println("Planta vacia. Usa la opcion 13 para cargar una planta de ejemplo.");
            }
            int opcion;
            do {
                mostrarMenu();
                opcion = leerEntero("");
                try {
                    procesarOpcion(opcion);
                } catch (FallaDeLazoException e) {
                    System.out.println("*** " + e.getMessage() + " Se genero una alarma de FALLA_LAZO.");
                } catch (EntidadNoEncontradaException | EntidadDuplicadaException | OperacionInvalidaException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println("Dato invalido: " + e.getMessage());
                }
            } while (opcion != 0);
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada.");
        }
        planta.guardarDatos();
        System.out.println("Datos guardados. Hasta luego!");
        scanner.close();
    }

    private static void mostrarMenu() {
        int pendientes = planta.getAlarmasSinReconocer().size();
        System.out.println("\n======= MENU  (alarmas sin reconocer: " + pendientes + ") =======");
        System.out.println(" 1. Tablero de instrumentos");
        System.out.println(" 2. Ingresar lectura manual (mA)");
        System.out.println(" 3. Simular ciclos de proceso");
        System.out.println(" 4. Alarmas activas");
        System.out.println(" 5. Reconocer alarma");
        System.out.println(" 6. Historial de alarmas");
        System.out.println(" 7. Registrar instrumento");
        System.out.println(" 8. Ver ficha de un instrumento");
        System.out.println(" 9. Registrar calibracion");
        System.out.println("10. Reporte de calibraciones");
        System.out.println("11. Estadisticas de un instrumento");
        System.out.println("12. Calculadoras de instrumentacion");
        System.out.println("13. Cargar planta de ejemplo");
        System.out.println("14. Cambiar estado de servicio");
        System.out.println(" 0. Salir y guardar");
        System.out.print("Opcion: ");
    }

    private static void procesarOpcion(int opcion) throws EntidadNoEncontradaException,
            EntidadDuplicadaException, OperacionInvalidaException, FallaDeLazoException {
        switch (opcion) {
            case 1 -> mostrarTablero();
            case 2 -> ingresarLectura();
            case 3 -> simular();
            case 4 -> mostrarAlarmas(planta.getAlarmasActivas(), "Alarmas activas");
            case 5 -> reconocer();
            case 6 -> mostrarHistorialAlarmas();
            case 7 -> registrarInstrumento();
            case 8 -> planta.buscarInstrumento(leerTexto("Tag: ")).mostrarInfo();
            case 9 -> registrarCalibracion();
            case 10 -> mostrarReporteCalibraciones();
            case 11 -> mostrarEstadisticas();
            case 12 -> calculadoras();
            case 13 -> { planta.cargarDatosDeEjemplo(); System.out.println("Planta de ejemplo cargada."); }
            case 14 -> cambiarServicio();
            case 0 -> { }
            default -> System.out.println("Opcion no valida.");
        }
    }

    // ---------- Pantallas ----------

    private static void mostrarTablero() {
        List<Instrumento> lista = planta.getTablero();
        if (lista.isEmpty()) {
            System.out.println("(no hay instrumentos registrados)");
            return;
        }
        System.out.println();
        System.out.printf("%-8s %-30s %-14s %-9s %-14s %s%n", "TAG", "DESCRIPCION", "VALOR", "ALARMA", "CALIBRACION", "SERVICIO");
        System.out.println("-".repeat(98));
        for (Instrumento i : lista) {
            String valor = (i.getUltimoValor() == null || i.getUltimaCalidad() == CalidadSenal.FALLA)
                ? "---" : String.format("%.2f %s", i.getUltimoValor(), i.getUnidad());
            long dias = i.diasParaVencerCalibracion(planta.getFechaActual());
            String calib = dias < 0 ? "VENCIDA (" + (-dias) + "d)" : "OK (" + dias + "d)";
            System.out.printf("%-8s %-30s %-14s %-9s %-14s %s%n", i.getTag(), recortar(i.getDescripcion(), 30),
                valor, i.getEstadoAlarma().getEtiqueta(), calib, i.getEstadoServicio());
        }
    }

    private static void mostrarAlarmas(List<Alarma> lista, String titulo) {
        System.out.println("\n--- " + titulo + " ---");
        if (lista.isEmpty()) {
            System.out.println("(sin alarmas)");
            return;
        }
        for (Alarma a : lista) {
            String valor = a.getEstado() == EstadoAlarma.FALLA_LAZO
                ? String.format("%.2f mA", a.getValor()) : String.format("%.2f", a.getValor());
            System.out.printf("#%-3d %s  %-7s %-6s valor=%-10s %s%n", a.getId(), a.getFechaActivacion().format(HORA),
                a.getTag(), a.getEstado().getEtiqueta() + (a.getEstado().esCritica() ? "!" : ""), valor, a.getSituacion());
        }
    }

    private static void mostrarHistorialAlarmas() {
        mostrarAlarmas(planta.getHistorialAlarmas(), "Historial de alarmas");
        planta.getConteoAlarmasPorEstado().forEach((e, n) -> System.out.println("  " + e + ": " + n));
    }

    private static void mostrarReporteCalibraciones() {
        System.out.println("\n--- Calibraciones vencidas ---");
        List<Instrumento> vencidas = planta.getCalibracionesVencidas();
        if (vencidas.isEmpty()) System.out.println("(ninguna)");
        for (Instrumento i : vencidas) {
            System.out.printf("%s  vencida hace %d dias (ultima: %s)%n", i.getTag(),
                -i.diasParaVencerCalibracion(planta.getFechaActual()), i.getUltimaCalibracion());
        }
        System.out.println("\n--- Vencen en los proximos 30 dias ---");
        List<Instrumento> proximas = planta.getCalibracionesProximasAVencer(30);
        if (proximas.isEmpty()) System.out.println("(ninguna)");
        for (Instrumento i : proximas) {
            System.out.printf("%s  vence en %d dias%n", i.getTag(), i.diasParaVencerCalibracion(planta.getFechaActual()));
        }
        System.out.println("\n--- Instrumentos por tipo ---");
        planta.getInstrumentosPorTipo().forEach((t, n) -> System.out.println(t.getNombre() + ": " + n));
        System.out.println("\n--- Historial de calibraciones ---");
        if (planta.getCalibraciones().isEmpty()) System.out.println("(sin registros)");
        for (RegistroCalibracion r : planta.getCalibraciones()) {
            System.out.printf("%s  %s  %s  error=%.2f%% (tol %.2f%%)  %s%n", r.fecha(), r.tag(), r.tecnico(),
                r.errorMaximoPct(), r.toleranciaPct(), r.aprobada() ? "APROBADA" : "RECHAZADA");
        }
    }

    private static void mostrarEstadisticas() throws EntidadNoEncontradaException {
        String tag = leerTexto("Tag: ");
        Instrumento inst = planta.buscarInstrumento(tag);
        DoubleSummaryStatistics e = planta.getEstadisticas(tag);
        if (e.getCount() == 0) {
            System.out.println("Todavia no hay lecturas en memoria para " + inst.getTag() + ". Usa la opcion 2 o 3.");
            return;
        }
        System.out.printf("%s (%d lecturas): min=%.2f  max=%.2f  promedio=%.2f %s%n",
            inst.getTag(), e.getCount(), e.getMin(), e.getMax(), e.getAverage(), inst.getUnidad());
    }

    // ---------- Acciones ----------

    private static void ingresarLectura() throws EntidadNoEncontradaException,
            OperacionInvalidaException, FallaDeLazoException {
        String tag = leerTexto("Tag: ");
        double mA = leerDecimal("Senal (mA): ");
        Lectura l = planta.registrarLectura(tag, mA);
        Instrumento i = planta.buscarInstrumento(tag);
        System.out.printf("%s = %.2f %s  (%.1f%% del span, calidad %s, alarma %s)%n", l.tag(), l.valor(), i.getUnidad(),
            Conversiones.mAAPorcentaje(mA), l.calidad(), i.getEstadoAlarma().getEtiqueta());
    }

    private static void simular() {
        int ciclos = leerEntero("Cantidad de ciclos: ");
        if (ciclos < 1 || ciclos > 10000) {
            System.out.println("Ingresa entre 1 y 10000 ciclos.");
            return;
        }
        SimuladorProceso.Resumen r = new SimuladorProceso(planta, semillaSimulacion++).ejecutar(ciclos);
        System.out.printf("Simulacion: %d lecturas, %d fallas de lazo, %d alarmas nuevas.%n",
            r.lecturas(), r.fallasDeLazo(), r.alarmasNuevas());
    }

    private static void reconocer() throws EntidadNoEncontradaException, OperacionInvalidaException {
        String entrada = leerTexto("Id de alarma (o 'todas'): ");
        if (entrada.equalsIgnoreCase("todas")) {
            System.out.println("Alarmas reconocidas: " + planta.reconocerTodas(operador));
        } else {
            try {
                planta.reconocerAlarma(Integer.parseInt(entrada), operador);
                System.out.println("Alarma reconocida por " + operador + ".");
            } catch (NumberFormatException e) {
                System.out.println("Ingresa un numero de alarma o la palabra 'todas'.");
            }
        }
    }

    private static void registrarInstrumento() throws EntidadDuplicadaException {
        int tipo = leerEntero("Tipo: 1) Presion  2) Temperatura  3) Nivel  4) Caudal -> ");
        if (tipo < 1 || tipo > 4) {
            System.out.println("Tipo no valido.");
            return;
        }
        String tag = leerTexto("Tag (ej. " + TipoMedicion.values()[tipo - 1].getPrefijo() + "-101): ");
        String desc = leerTexto("Descripcion: ");
        double min = leerDecimal("Rango minimo: ");
        double max = leerDecimal("Rango maximo: ");
        int dias = leerEntero("Dias desde la ultima calibracion: ");
        var fechaCalib = planta.getFechaActual().minusDays(dias);

        Instrumento inst = switch (tipo) {
            case 1 -> new TransmisorPresion(tag, desc, min, max, fechaCalib, leerTexto("Tipo de presion (manometrica/diferencial/absoluta): "));
            case 2 -> new TransmisorTemperatura(tag, desc, min, max, fechaCalib, leerTexto("Sensor (RTD Pt100/termocupla K/...): "));
            case 3 -> new TransmisorNivel(tag, desc, min, max, fechaCalib, leerTexto("Principio (radar/presion diferencial/ultrasonico): "));
            default -> new TransmisorCaudal(tag, desc, min, max, fechaCalib, leerTexto("Principio (vortex/coriolis/placa orificio): "));
        };
        if (leerTexto("Configurar setpoints de alarma ahora? (s/n, n = valores por defecto): ").equalsIgnoreCase("s")) {
            double ll = leerDecimal("LL (muy bajo): ");
            double l = leerDecimal("L (bajo): ");
            double h = leerDecimal("H (alto): ");
            double hh = leerDecimal("HH (muy alto): ");
            inst.configurarAlarmas(ll, l, h, hh, leerDecimal("Histeresis (% del span): "));
        }
        planta.registrarInstrumento(inst);
        System.out.println("Instrumento registrado:");
        inst.mostrarInfo();
    }

    private static void registrarCalibracion() throws EntidadNoEncontradaException {
        Instrumento inst = planta.buscarInstrumento(leerTexto("Tag: "));
        double[] patron = Conversiones.puntosPatron(inst.getRangoMin(), inst.getRangoMax(), 5);
        double[] leidos = new double[patron.length];
        System.out.println("Ingresa lo que marca el instrumento en cada punto patron (" + inst.getUnidad() + "):");
        for (int i = 0; i < patron.length; i++) {
            leidos[i] = leerDecimal(String.format("  Patron %.2f (%d%%) -> leido: ", patron[i], i * 25));
        }
        String obs = leerTexto("Observaciones: ");
        RegistroCalibracion r = planta.registrarCalibracion(inst.getTag(), operador, patron, leidos, obs);
        System.out.printf("Error maximo: %.2f%% (tolerancia %.2f%%) -> %s%n", r.errorMaximoPct(), r.toleranciaPct(),
            r.aprobada() ? "APROBADA. Fecha de calibracion actualizada."
                         : "RECHAZADA. El instrumento pasa a EN_MANTENIMIENTO para ajuste.");
    }

    private static void cambiarServicio() throws EntidadNoEncontradaException {
        String tag = leerTexto("Tag: ");
        int e = leerEntero("Estado: 1) EN_SERVICIO  2) EN_MANTENIMIENTO  3) FUERA_DE_SERVICIO -> ");
        if (e < 1 || e > 3) {
            System.out.println("Estado no valido.");
            return;
        }
        planta.cambiarEstadoServicio(tag, EstadoServicio.values()[e - 1]);
        System.out.println("Estado actualizado.");
    }

    private static void calculadoras() {
        System.out.println("1) mA -> valor de proceso   2) valor -> mA   3) Pt100 ohm -> C   4) C -> Pt100 ohm   5) bar <-> psi");
        switch (leerEntero("Opcion: ")) {
            case 1 -> {
                double min = leerDecimal("Rango minimo: "), max = leerDecimal("Rango maximo: ");
                double mA = leerDecimal("Senal (mA): ");
                System.out.printf("%.2f mA = %.3f (%.1f%% del span)%n", mA, Conversiones.mAAValor(mA, min, max), Conversiones.mAAPorcentaje(mA));
            }
            case 2 -> {
                double min = leerDecimal("Rango minimo: "), max = leerDecimal("Rango maximo: ");
                double v = leerDecimal("Valor de proceso: ");
                System.out.printf("%.3f = %.3f mA%n", v, Conversiones.valorAmA(v, min, max));
            }
            case 3 -> {
                double ohm = leerDecimal("Resistencia (ohm): ");
                System.out.printf("%.2f ohm = %.2f C%n", ohm, Conversiones.ohmAPt100(ohm));
            }
            case 4 -> {
                double c = leerDecimal("Temperatura (C): ");
                System.out.printf("%.2f C = %.2f ohm%n", c, Conversiones.pt100AOhm(c));
            }
            case 5 -> {
                double bar = leerDecimal("Presion en bar: ");
                System.out.printf("%.3f bar = %.2f psi%n", bar, Conversiones.barAPsi(bar));
            }
            default -> System.out.println("Opcion no valida.");
        }
    }

    // ---------- Lectura de datos con validacion ----------

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    private static String leerTexto(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String linea = scanner.nextLine().trim();
            if (!linea.isEmpty()) return linea;
            System.out.println("No puede estar vacio.");
        }
    }

    private static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Ingresa un numero entero valido.");
            }
        }
    }

    private static double leerDecimal(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Ingresa un numero valido (ej: 12.5).");
            }
        }
    }
}
