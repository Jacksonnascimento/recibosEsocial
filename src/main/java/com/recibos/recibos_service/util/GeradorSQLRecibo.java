package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class GeradorSQLRecibo {

    private final ArquivoXML leitorXML;
    private final ArquivosESocial arquivosESocial;

    public GeradorSQLRecibo(boolean modoInsert) throws Exception {
        this.leitorXML = new ArquivoXML();
        this.arquivosESocial = new ArquivosESocial(modoInsert); 
    }

    public String gerarSQL(File xmlFile, String nomeArquivoOriginal)
            throws ParserConfigurationException, SAXException, IOException {

        String tipoEvento = nomeArquivoOriginal.substring(nomeArquivoOriginal.length() - 8);

        // 1. Chama o método refatorado que RETORNA o DTO
        InfoReciboDTO info = leitorXML.infXML(xmlFile, tipoEvento);

        String sql = "";

        // 2. Usa o DTO 'info' ao invés de getters estáticos
        switch (tipoEvento) {
            case "2200.xml":
                sql = arquivosESocial.s2200(info.getMatricula(), info.getRecibo());
                break;
            case "2299.xml":
                sql = arquivosESocial.s2299(info.getMatricula(), info.getRecibo());
                break;
            case "1200.xml":
                sql = arquivosESocial.s1200(info.getCpf(), info.getRecibo(), info.getPerApur());
                break;
            // NOVA ADIÇÃO:
            case "1202.xml":
                sql = arquivosESocial.s1202(info.getCpf(), info.getRecibo(), info.getPerApur());
                break;
            case "1210.xml":
                sql = arquivosESocial.s1210(info.getCpf(), info.getRecibo(), info.getPerApur());
                break;
            case "3000.xml":
                // O s3000 usa o nrRecEvt, não o nrRecibo
                sql = arquivosESocial.s3000(info.getNrRecEvt()); 
                break;
            default:
                throw new IllegalArgumentException("Tipo de arquivo XML não reconhecido: " + tipoEvento);
        }

        return sql;
    }
}