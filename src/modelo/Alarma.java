package modelo;

import excepciones.OperacionInvalidaException;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Evento de alarma. Su ciclo de vida es simplificado, inspirado en la gestion de alarmas ISA 18.2:
 * se activa, el operador la reconoce y luego vuelve a normal (en cualquier orden entre los dos ultimos).
 */
public class Alarma implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String tag;
    private final EstadoAlarma estado;
    private final double valor;               // valor de proceso; para FALLA_LAZO es la senal en mA
    private final LocalDateTime fechaActivacion;

    private boolean reconocida;
    private String reconocidaPor;
    private LocalDateTime fechaReconocimiento;

    private boolean normalizada;
    private LocalDateTime fechaNormalizacion;

    public Alarma(int id, String tag, EstadoAlarma estado, double valor, LocalDateTime fechaActivacion) {
        this.id = id;
        this.tag = tag;
        this.estado = estado;
        this.valor = valor;
        this.fechaActivacion = fechaActivacion;
    }

    public void reconocer(String operador, LocalDateTime momento) throws OperacionInvalidaException {
        if (reconocida) {
            throw new OperacionInvalidaException("La alarma #" + id + " ya fue reconocida por " + reconocidaPor + ".");
        }
        reconocida = true;
        reconocidaPor = operador;
        fechaReconocimiento = momento;
    }

    public void normalizar(LocalDateTime momento) {
        if (!normalizada) {
            normalizada = true;
            fechaNormalizacion = momento;
        }
    }

    public boolean isActiva() { return !normalizada; }

    public String getSituacion() {
        String ack = reconocida ? "reconocida" : "SIN RECONOCER";
        return normalizada ? "volvio a normal, " + ack : "ACTIVA, " + ack;
    }

    public int getId()                           { return id; }
    public String getTag()                       { return tag; }
    public EstadoAlarma getEstado()              { return estado; }
    public double getValor()                     { return valor; }
    public LocalDateTime getFechaActivacion()    { return fechaActivacion; }
    public boolean isReconocida()                { return reconocida; }
    public String getReconocidaPor()             { return reconocidaPor; }
    public LocalDateTime getFechaReconocimiento(){ return fechaReconocimiento; }
    public boolean isNormalizada()               { return normalizada; }
    public LocalDateTime getFechaNormalizacion() { return fechaNormalizacion; }
}
