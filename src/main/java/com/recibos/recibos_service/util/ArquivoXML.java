package com.recibos.recibos_service.util;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ArquivoXML {

    // REMOVIDOS: Todos os campos estáticos (id, recibo, cpf, matricula, etc.)
    // REMOVIDOS: Todos os getters estáticos

    public InfoReciboDTO infXML(File arquivo, String tipoArquivoEve) throws ParserConfigurationException, SAXException {
        try {
            // Cria um DTO para esta execução específica
            InfoReciboDTO info = new InfoReciboDTO();
            
            String tipoEvento = "n"; // Valor padrão

            File file = arquivo;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(file);
            document.getDocumentElement().normalize();
           
            
            switch(tipoArquivoEve){
                case "2200.xml":
                    tipoEvento = "evtAdmissao";
                    break;
                case "2299.xml":
                    tipoEvento = "evtDeslig";
                    break;
                case "1200.xml":
                    tipoEvento = "evtRemun";
                    break;
                // NOVA ADIÇÃO:
                case "1202.xml":
                    tipoEvento = "evtRmnRPPS"; // Tag principal do S-1202
                    break;
                case "1210.xml":
                    tipoEvento = "evtPgtos";
                    break;
                case "3000.xml":
                    tipoEvento = "evtExclusao";
                    break;
                default:
                    tipoEvento = "n";
                    break;
               
            }
            
            if (!"n".equals(tipoEvento)) {
                NodeList nList = document.getElementsByTagName(tipoEvento);

                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Node nNode = nList.item(temp);
                    // tipoEvento = nNode.getNodeName(); // Esta linha parecia desnecessária
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        
                        // Salva no DTO da instância, não em campos estáticos
                        info.setId(eElement.getAttribute("Id")); 

                        if (!"1210.xml".equals(tipoArquivoEve) && !"3000.xml".equals(tipoArquivoEve)) {
                            // S-1200, S-1202, S-2200, S-2299
                            info.setCpf(eElement.getElementsByTagName("cpfTrab").item(0).getTextContent());
                            info.setMatricula(eElement.getElementsByTagName("matricula").item(0).getTextContent());
                        } else if ("1210.xml".equals(tipoArquivoEve)) {
                            info.setCpf(eElement.getElementsByTagName("cpfBenef").item(0).getTextContent());
                        }

                        if ("1200.xml".equals(tipoArquivoEve)
                                || "1210.xml".equals(tipoArquivoEve)
                                // NOVA ADIÇÃO:
                                || "1202.xml".equals(tipoArquivoEve)) {
                            info.setPerApur(eElement.getElementsByTagName("perApur").item(0).getTextContent());

                        } else if ("3000.xml".equals(tipoArquivoEve)) {
                            info.setNrRecEvt(eElement.getElementsByTagName("nrRecEvt").item(0).getTextContent());
                        }
                    }
                }

                nList = null;
                nList = document.getElementsByTagName("retornoEvento");
                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Node nNode = nList.item(temp);
                    
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        // Salva o recibo no DTO da instância
                        info.setRecibo(eElement.getElementsByTagName("nrRecibo").item(0).getTextContent()); 
                    }
                }
            }
            
            // Retorna o DTO preenchido
            return info;

        } catch (IOException e) {
            System.out.println(e);
            throw new RuntimeException("Erro ao ler o XML", e); // Propaga a exceção
        }
    }
}