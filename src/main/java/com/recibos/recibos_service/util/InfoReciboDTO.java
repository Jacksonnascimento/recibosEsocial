package com.recibos.recibos_service.util;

// Usando Lombok, que já está no seu pom.xml
import lombok.Data;

@Data
public class InfoReciboDTO {
    private String id;
    private String recibo;
    private String cpf;
    private String matricula;
    private String perApur;
    private String nrRecEvt;
}