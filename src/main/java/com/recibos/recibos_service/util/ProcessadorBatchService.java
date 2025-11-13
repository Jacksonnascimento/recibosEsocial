package com.recibos.recibos_service.util;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
// MUDANÇA: Não precisamos mais de MultipartFile aqui
// import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
// MUDANÇA: Não precisamos mais de Paths ou UUID aqui

@Service
public class ProcessadorBatchService {

    /**
     * MUDANÇA: O método agora recebe o Path do lote, não o array de MultipartFile.
     */
    @Async
    public void processarLote(Path loteDir, boolean modoInsert) {
        
        String loteId = loteDir.getFileName().toString();
        
        try {
            System.out.println("Processando lote " + loteId + "...");

            GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo(modoInsert);

            // 2. Itera sobre os arquivos JÁ SALVOS no diretório 'loteDir'
            // Usamos um try-with-resources para garantir que o stream de arquivos seja fechado
            try (var stream = Files.list(loteDir)) {
                
                stream.forEach(arquivoPath -> {
                    File arquivoTemp = arquivoPath.toFile();
                    String nomeOriginal = arquivoTemp.getName();

                    // 3. Processa o arquivo (lógica de antes)
                    try {
                        String scriptSQL = geradorSQL.gerarSQL(arquivoTemp, nomeOriginal);
                        salvarScript(scriptSQL, nomeOriginal);
                        
                        System.out.println("Arquivo " + nomeOriginal + " processado com sucesso.");
                        
                        // 4. Deleta o arquivo XML da pasta "pendentes" após o sucesso
                        arquivoTemp.delete();

                    } catch (Exception e) {
                        System.err.println("Erro ao processar arquivo " + nomeOriginal + ": " + e.getMessage());
                        // (Opcional: mover para uma pasta de "erros")
                    }
                });
            }
            
            // 5. Limpa o diretório do lote (que agora deve estar vazio)
            Files.delete(loteDir);
            System.out.println("Processamento do lote " + loteId + " concluído.");

        } catch (Exception e) {
            System.err.println("Erro grave no processamento do lote " + loteId + ": " + e.getMessage());
            // Tenta limpar em caso de falha
            try {
                Files.walk(loteDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            } catch (IOException ioe) {
                System.err.println("Erro ao limpar diretório de lote com falha: " + loteId);
            }
        }
    }

    // Método de ajuda (o mesmo de antes)
    private String salvarScript(String script, String nomeArquivoOriginal) throws IOException {
        String pastaScripts = "scripts_gerados";
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