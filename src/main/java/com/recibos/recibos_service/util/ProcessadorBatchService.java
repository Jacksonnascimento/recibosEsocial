package com.recibos.recibos_service.util;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired; 

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList; 
import java.util.HashMap;   
import java.util.List;      
import java.util.Map;       

@Service
public class ProcessadorBatchService {

    @Autowired
    private StatusSessaoService statusSessaoService;

    private final Path DIRETORIO_BASE_DADOS = Paths.get("C:", "dados_app_recibos");
    private final String pastaScripts = DIRETORIO_BASE_DADOS.resolve("scripts_gerados").toString();


    /**
     * --- MUDANÇA (CORRIGE AVISO 1) ---
     * O campo 'arquivoOriginal' foi removido, pois não era lido.
     */
    private static class DtoWrapper {
        final InfoReciboDTO info;
        final String tipoEvento;
        final String nomeOriginal;
        // final File arquivoOriginal; // Removido

        DtoWrapper(InfoReciboDTO info, String tipoEvento, String nomeOriginal) { // Removido do construtor
            this.info = info;
            this.tipoEvento = tipoEvento;
            this.nomeOriginal = nomeOriginal;
            // this.arquivoOriginal = arquivoOriginal; // Removido
        }
    }

    @Async
    public void processarLote(Path sessaoDir, boolean modoInsert, String filtroPerApur) {
        
        String sessaoId = sessaoDir.getFileName().toString();
        int scriptsGerados = 0;
        
        try {
            System.out.println("Processando SESSÃO " + sessaoId + "...");
            
            // Agora esta instanciação deve funcionar, pois o GeradorSQLRecibo foi corrigido
            GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo(modoInsert); 
            
            ArquivoXML leitorXML = new ArquivoXML();
            Map<String, DtoWrapper> dtosMaisRecentes = new HashMap<>();
            List<File> arquivosParaDeletar = new ArrayList<>();

            System.out.println("Sessão " + sessaoId + ": Iniciando Etapa 1 (Parse, Filtro e Deduplicação)...");
            
            try (var stream = Files.list(sessaoDir)) {
                stream.forEach(arquivoPath -> {
                    File arquivoTemp = arquivoPath.toFile();
                    String nomeOriginal = arquivoTemp.getName();
                    arquivosParaDeletar.add(arquivoTemp); 

                    try {
                        String tipoEvento = nomeOriginal.substring(nomeOriginal.length() - 8);
                        InfoReciboDTO info = leitorXML.infXML(arquivoTemp, tipoEvento);

                        if (filtroPerApur != null && !filtroPerApur.isEmpty()) {
                            if ("1200.xml".equals(tipoEvento) || "1202.xml".equals(tipoEvento) || "1210.xml".equals(tipoEvento)) {
                                if (info.getPerApur() == null || !info.getPerApur().equals(filtroPerApur)) {
                                    return; 
                                }
                            }
                        }

                        String chave = info.getDeduplicationKey(tipoEvento);
                        
                        // --- MUDANÇA (CORRIGE AVISO 1) ---
                        // O 'arquivoTemp' foi removido da chamada do construtor.
                        DtoWrapper wrapperNovo = new DtoWrapper(info, tipoEvento, nomeOriginal);
                        DtoWrapper wrapperExistente = dtosMaisRecentes.get(chave);

                        if (wrapperExistente == null || wrapperNovo.info.isMaisRecenteQue(wrapperExistente.info)) {
                            dtosMaisRecentes.put(chave, wrapperNovo);
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao fazer parse do arquivo " + nomeOriginal + ": " + e.getMessage());
                    }
                });
            }
            
            System.out.println("Sessão " + sessaoId + ": Etapa 1 concluída. " + dtosMaisRecentes.size() + " recibos únicos selecionados.");
            
            System.out.println("Sessão " + sessaoId + ": Iniciando Etapa 2 (Geração de SQL)...");

            for (DtoWrapper wrapper : dtosMaisRecentes.values()) {
                try {
                    // --- MUDANÇA (CORRIGE ERRO 2) ---
                    // Esta chamada agora corresponde ao GeradorSQLRecibo.java corrigido.
                    String scriptSQL = geradorSQL.gerarSQL(wrapper.info, wrapper.tipoEvento);
                    
                    salvarScript(scriptSQL, wrapper.nomeOriginal);
                    scriptsGerados++; 
                    System.out.println("SQL gerado para: " + wrapper.nomeOriginal);
                } catch (Exception e) {
                    System.err.println("Erro ao gerar SQL para " + wrapper.nomeOriginal + ": " + e.getMessage());
                }
            }
            
            System.out.println("Sessão " + sessaoId + ": Etapa 2 concluída.");
            System.out.println("Sessão " + sessaoId + ": Iniciando Etapa 3 (Limpeza)...");

            for(File f : arquivosParaDeletar) {
                f.delete();
            }
            Files.delete(sessaoDir);
            
            System.out.println("Processamento da SESSÃO " + sessaoId + " concluído.");
            statusSessaoService.completarSessao(sessaoId, scriptsGerados);

        } catch (Exception e) {
            System.err.println("Erro grave no processamento da SESSÃO " + sessaoId + ": " + e.getMessage());
            statusSessaoService.falharSessao(sessaoId, e.getMessage());
            e.printStackTrace();
            
            try {
                Files.walk(sessaoDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException ioe) {
                System.err.println("Erro ao limpar diretório de sessão com falha: " + sessaoId);
            }
        }
    }

    private String salvarScript(String script, String nomeArquivoOriginal) throws IOException {
        File dir = new File(pastaScripts);
        if (!dir.exists())
            dir.mkdirs();

        String nomeScript = nomeArquivoOriginal.replaceAll("\\.xml$", ".sql");
        File arquivoScript = new File(dir, nomeScript);

        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(arquivoScript))) {
            bw.write(script);
        }
        return arquivoScript.getAbsolutePath();
    }
}