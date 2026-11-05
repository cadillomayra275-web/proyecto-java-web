package pe.edu.siscom;

public final class Fallo extends RuntimeException {
    public final int estado;
    public Fallo(int estado, String mensaje) { super(mensaje); this.estado = estado; }
}
