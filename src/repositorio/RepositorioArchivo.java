package repositorio;

import interfaces.IRepositorio;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Repositorio generico: persiste una lista de objetos Serializable en un archivo .dat. */
public class RepositorioArchivo<T extends Serializable> implements IRepositorio<T> {

    private final Path archivo;

    public RepositorioArchivo(String carpeta, String nombreArchivo) {
        this.archivo = Path.of(carpeta, nombreArchivo);
    }

    @Override
    public void guardar(List<T> elementos) {
        try {
            Files.createDirectories(archivo.getParent());
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(archivo))) {
                oos.writeObject(new ArrayList<>(elementos));
            }
        } catch (IOException e) {
            System.out.println("Error al guardar " + archivo.getFileName() + ": " + e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> cargar() {
        if (!Files.exists(archivo)) {
            return new ArrayList<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(archivo))) {
            return (List<T>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error al cargar " + archivo.getFileName() + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
