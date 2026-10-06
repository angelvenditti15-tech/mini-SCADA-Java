package servicio;

import excepciones.*;
import modelo.*;
import repositorio.RepositorioArchivo;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Logica de negocio del sistema de monitoreo. No lee ni imprime nada: lanza excepciones y la
 * interfaz (Main) decide como mostrarlas. Recibe un Clock para poder probar con fechas fijas.
 */
public class Planta {

    private static final int MAX_HISTORIAL_POR_TAG = 500;

    private final Clock reloj;
    private final Map<String, Instrumento> instrumentos = new HashMap<>();
    private final Map<String, Deque<Lectura>> historial = new HashMap<>(); // solo en memoria
    private final List<Alarma> alarmas;
    private final List<RegistroCalibracion> calibraciones;
    private int proximoIdAlarma;

    private final RepositorioArchivo<Instrumento> repoInstrumentos;
    private final RepositorioArchivo<Alarma> repoAlarmas;
    private final RepositorioArchivo<RegistroCalibracion> repoCalibraciones;

    public Planta() {
        this("datos", Clock.systemDefaultZone());
    }

    public Planta(String carpetaDatos, Clock reloj) {
        this.reloj = reloj;
        repoInstrumentos = new RepositorioArchivo<>(carpetaDatos, "instrumentos.dat");
        repoAlarmas = new RepositorioArchivo<>(carpetaDatos, "alarmas.dat");
        repoCalibraciones = new RepositorioArchivo<>(carpetaDatos, "calibraciones.dat");

        repoInstrumentos.cargar().forEach(i -> instrumentos.put(i.getTag(), i));
        alarmas = repoAlarmas.cargar();
        calibraciones = repoCalibraciones.cargar();
        proximoIdAlarma = alarmas.stream().mapToInt(Alarma::getId).max().orElse(0) + 1;
    }

    public LocalDate getFechaActual()    { return LocalDate.now(reloj); }
    public LocalDateTime getAhora()      { return LocalDateTime.now(reloj); }

    // ---------- Instrumentos ----------

    public void registrarInstrumento(Instrumento i) throws EntidadDuplicadaException {
        if (instrumentos.containsKey(i.getTag())) {
            throw new EntidadDuplicadaException("Ya existe un instrumento con tag " + i.getTag());
        }
        instrumentos.put(i.getTag(), i);
    }

    public Instrumento buscarInstrumento(String tag) throws EntidadNoEncontradaException {
        Instrumento i = instrumentos.get(tag == null ? "" : tag.trim().toUpperCase());
        if (i == null) {
            throw new EntidadNoEncontradaException("No existe un instrumento con tag " + tag);
        }
        return i;
    }

    public void cambiarEstadoServicio(String tag, EstadoServicio estado) throws EntidadNoEncontradaException {
        buscarInstrumento(tag).setEstadoServicio(estado);
    }

    // ---------- Lecturas y alarmas ----------

    /**
     * Procesa una senal 4-20 mA de un instrumento: convierte a unidades de ingenieria, guarda en el
     * historial y dispara / despeja las alarmas que correspondan.
     */
    public Lectura registrarLectura(String tag, double miliamperios)
            throws EntidadNoEncontradaException, OperacionInvalidaException, FallaDeLazoException {
        Instrumento inst = buscarInstrumento(tag);
        if (inst.getEstadoServicio() != EstadoServicio.EN_SERVICIO) {
            throw new OperacionInvalidaException(
                inst.getTag() + " esta " + inst.getEstadoServicio() + ": no se aceptan lecturas.");
        }
        LocalDateTime ahora = getAhora();

        Lectura lectura;
        try {
            lectura = inst.procesarSenal(miliamperios, ahora);
        } catch (FallaDeLazoException e) {
            inst.marcarFallaDeLazo();
            cambiarEstadoAlarma(inst, EstadoAlarma.FALLA_LAZO, miliamperios, ahora);
            throw e;
        }

        Deque<Lectura> cola = historial.computeIfAbsent(inst.getTag(), k -> new ArrayDeque<>());
        cola.addLast(lectura);
        if (cola.size() > MAX_HISTORIAL_POR_TAG) {
            cola.removeFirst();
        }

        EstadoAlarma nuevo = inst.evaluarAlarma(lectura.valor(), inst.getEstadoAlarma());
        cambiarEstadoAlarma(inst, nuevo, lectura.valor(), ahora);
        return lectura;
    }

    /** Si el estado cambia: cierra las alarmas activas del tag y, si el nuevo estado no es normal, abre una nueva. */
    private void cambiarEstadoAlarma(Instrumento inst, EstadoAlarma nuevo, double valor, LocalDateTime ahora) {
        if (nuevo == inst.getEstadoAlarma()) {
            return;
        }
        for (Alarma a : alarmas) {
            if (a.getTag().equals(inst.getTag()) && a.isActiva()) {
                a.normalizar(ahora);
            }
        }
        if (nuevo != EstadoAlarma.NORMAL) {
            alarmas.add(new Alarma(proximoIdAlarma++, inst.getTag(), nuevo, valor, ahora));
        }
        inst.actualizarEstadoAlarma(nuevo);
    }

    public void reconocerAlarma(int id, String operador)
            throws EntidadNoEncontradaException, OperacionInvalidaException {
        Alarma a = alarmas.stream().filter(x -> x.getId() == id).findFirst()
            .orElseThrow(() -> new EntidadNoEncontradaException("No existe la alarma #" + id));
        a.reconocer(operador, getAhora());
    }

    /** Reconoce todas las alarmas pendientes y devuelve cuantas fueron. */
    public int reconocerTodas(String operador) {
        int cantidad = 0;
        for (Alarma a : alarmas) {
            if (!a.isReconocida()) {
                try {
                    a.reconocer(operador, getAhora());
                    cantidad++;
                } catch (OperacionInvalidaException ignorada) {
                    // no puede ocurrir: se filtro por !isReconocida()
                }
            }
        }
        return cantidad;
    }

    // ---------- Calibracion ----------

    /**
     * Registra una verificacion de calibracion. Si el error maximo esta dentro de la tolerancia queda
     * aprobada y se actualiza la fecha; si no, el instrumento pasa a EN_MANTENIMIENTO para ajuste.
     */
    public RegistroCalibracion registrarCalibracion(String tag, String tecnico,
            double[] valoresPatron, double[] valoresLeidos, String observaciones)
            throws EntidadNoEncontradaException {
        Instrumento inst = buscarInstrumento(tag);
        double error = inst.calcularErrorMaximoPct(valoresPatron, valoresLeidos);
        double tolerancia = inst.getToleranciaCalibracionPct();
        boolean aprobada = error <= tolerancia;

        LocalDate hoy = getFechaActual();
        RegistroCalibracion registro = new RegistroCalibracion(
            inst.getTag(), hoy, tecnico, error, tolerancia, aprobada, observaciones);
        calibraciones.add(registro);

        if (aprobada) {
            inst.registrarCalibracionAprobada(hoy);
        } else {
            inst.setEstadoServicio(EstadoServicio.EN_MANTENIMIENTO);
        }
        return registro;
    }

    // ---------- Consultas y reportes (Streams) ----------

    public List<Instrumento> getTablero() {
        return instrumentos.values().stream()
            .sorted(Comparator.comparing(Instrumento::getTag))
            .collect(Collectors.toList());
    }

    public List<Alarma> getAlarmasActivas() {
        return alarmas.stream()
            .filter(Alarma::isActiva)
            .sorted(Comparator.comparingInt((Alarma a) -> a.getEstado().getPrioridad()).reversed()
                .thenComparing(Alarma::getFechaActivacion))
            .collect(Collectors.toList());
    }

    public List<Alarma> getAlarmasSinReconocer() {
        return alarmas.stream().filter(a -> !a.isReconocida()).collect(Collectors.toList());
    }

    public List<Alarma> getHistorialAlarmas() {
        return Collections.unmodifiableList(alarmas);
    }

    public Map<EstadoAlarma, Long> getConteoAlarmasPorEstado() {
        return alarmas.stream().collect(Collectors.groupingBy(
            Alarma::getEstado, () -> new EnumMap<>(EstadoAlarma.class), Collectors.counting()));
    }

    public Map<TipoMedicion, Long> getInstrumentosPorTipo() {
        return instrumentos.values().stream().collect(Collectors.groupingBy(
            Instrumento::getTipoMedicion, () -> new EnumMap<>(TipoMedicion.class), Collectors.counting()));
    }

    public List<Instrumento> getCalibracionesVencidas() {
        LocalDate hoy = getFechaActual();
        return getTablero().stream().filter(i -> i.calibracionVencida(hoy)).collect(Collectors.toList());
    }

    /** Instrumentos cuya calibracion todavia es valida pero vence dentro de los proximos 'dias' dias. */
    public List<Instrumento> getCalibracionesProximasAVencer(int dias) {
        LocalDate hoy = getFechaActual();
        return getTablero().stream()
            .filter(i -> !i.calibracionVencida(hoy) && i.diasParaVencerCalibracion(hoy) <= dias)
            .collect(Collectors.toList());
    }

    public List<RegistroCalibracion> getCalibracionesDe(String tag) {
        String t = tag == null ? "" : tag.trim().toUpperCase();
        return calibraciones.stream().filter(c -> c.tag().equals(t)).collect(Collectors.toList());
    }

    public List<RegistroCalibracion> getCalibraciones() {
        return Collections.unmodifiableList(calibraciones);
    }

    /** Estadisticas (min, max, promedio, cantidad) de las lecturas validas en memoria de un instrumento. */
    public DoubleSummaryStatistics getEstadisticas(String tag) throws EntidadNoEncontradaException {
        Instrumento inst = buscarInstrumento(tag);
        return historial.getOrDefault(inst.getTag(), new ArrayDeque<>()).stream()
            .mapToDouble(Lectura::valor)
            .summaryStatistics();
    }

    // ---------- Persistencia y datos de ejemplo ----------

    public void guardarDatos() {
        repoInstrumentos.guardar(new ArrayList<>(instrumentos.values()));
        repoAlarmas.guardar(alarmas);
        repoCalibraciones.guardar(calibraciones);
    }

    public boolean estaVacia() {
        return instrumentos.isEmpty();
    }

    /** Carga una planta de ejemplo: una bomba, un horno, un tanque y una linea de gas. */
    public void cargarDatosDeEjemplo() {
        LocalDate hoy = getFechaActual();
        try {
            Instrumento pt101 = new TransmisorPresion("PT-101", "Presion descarga bomba P-101",
                0, 25, hoy.minusDays(100), "manometrica");
            pt101.configurarAlarmas(2, 5, 20, 23, 2.0);
            registrarInstrumento(pt101);

            Instrumento pt102 = new TransmisorPresion("PT-102", "Presion separador V-100",
                0, 40, hoy.minusDays(30), "manometrica");
            registrarInstrumento(pt102);

            Instrumento tt201 = new TransmisorTemperatura("TT-201", "Temperatura salida horno H-200",
                0, 400, hoy.minusDays(200), "termocupla K");
            tt201.configurarAlarmas(50, 100, 320, 370, 2.0);
            registrarInstrumento(tt201);

            Instrumento lt301 = new TransmisorNivel("LT-301", "Nivel tanque TK-301",
                0, 100, hoy.minusDays(340), "radar");
            lt301.configurarAlarmas(10, 20, 85, 95, 2.0);
            registrarInstrumento(lt301);

            Instrumento ft401 = new TransmisorCaudal("FT-401", "Caudal de gas a quemador",
                0, 500, hoy.minusDays(60), "vortex");
            registrarInstrumento(ft401);
        } catch (EntidadDuplicadaException e) {
            System.out.println("Los datos de ejemplo ya estaban cargados: " + e.getMessage());
        }
    }
}
