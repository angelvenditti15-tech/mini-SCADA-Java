package interfaces;

import java.util.List;

/** Contrato generico de persistencia. */
public interface IRepositorio<T> {
    void guardar(List<T> elementos);
    List<T> cargar();
}
