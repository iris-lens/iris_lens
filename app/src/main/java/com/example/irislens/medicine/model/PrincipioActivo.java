package com.example.irislens.medicine.model;

public class PrincipioActivo {
    private final long id;
    private final String firestoreId; // null = creado localmente por el usuario
    private String nombre;
    private final boolean modificadoLocal;
    private final boolean eliminadoLocal;

    public PrincipioActivo(long id, String firestoreId, String nombre,
                           boolean modificadoLocal, boolean eliminadoLocal) {
        this.id = id;
        this.firestoreId = firestoreId;
        this.nombre = nombre;
        this.modificadoLocal = modificadoLocal;
        this.eliminadoLocal = eliminadoLocal;
    }

    public long getId() { return id; }
    public String getFirestoreId() { return firestoreId; }
    public String getNombre() { return nombre; }
    public boolean isModificadoLocal() { return modificadoLocal; }
    public boolean isEliminadoLocal() { return eliminadoLocal; }
    public boolean isLocalPropio() { return firestoreId == null; }
}