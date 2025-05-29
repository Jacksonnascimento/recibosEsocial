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
        this.arquivosESocial = new ArquivosESocial(modoInsert); // ou false, se quiser modo de UPDATE
    }

    public String gerarSQL(File xmlFile, String nomeArquivoOriginal)
            throws ParserConfigurationException, SAXException, IOException {
        System.out.println("Arquivo para gerar SQL: " + xmlFile.getAbsolutePath());
        System.out.println("Nome original do arquivo: " + nomeArquivoOriginal);

        // Extrair tipoEvento do nome original (últimos 8 caracteres)
        String tipoEvento = nomeArquivoOriginal.substring(nomeArquivoOriginal.length() - 8);

        // Usar tipoEvento para continuar normalmente
        leitorXML.infXML(xmlFile, tipoEvento);

        String sql = "";

        switch (tipoEvento) {
            case "2200.xml":
                sql = arquivosESocial.s2200(ArquivoXML.getMatricula(), ArquivoXML.getRecibo(), "", "", "", "");
                break;
            case "2299.xml":
                sql = arquivosESocial.s2299(ArquivoXML.getMatricula(), ArquivoXML.getRecibo(), "", "", "", "");
                break;
            case "1200.xml":
                sql = arquivosESocial.s1200(ArquivoXML.getCpf(), ArquivoXML.getRecibo(), ArquivoXML.getPerApur(), "",
                        "", "", "");
                break;
            case "1210.xml":
                sql = arquivosESocial.s1210(ArquivoXML.getCpf(), ArquivoXML.getRecibo(), ArquivoXML.getPerApur(), "",
                        "", "", "");
                break;
            case "3000.xml":
                sql = arquivosESocial.s3000(ArquivoXML.getNrRecEvt(), "", "", "", "");
                break;
            default:
                throw new IllegalArgumentException("Tipo de arquivo XML não reconhecido: " + tipoEvento);
        }

        return sql;
    }
}
