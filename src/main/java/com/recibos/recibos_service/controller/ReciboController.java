package com.recibos.recibos_service.controller;

import com.recibos.recibos_service.util.FonteDadoDTO;
import com.recibos.recibos_service.util.FonteDados;
import com.recibos.recibos_service.util.ProcessadorBatchService;
import com.recibos.recibos_service.util.LimpadorDePasta;

import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api")
public class ReciboController {

    @Autowired
    private ProcessadorBatchService processadorBatchService;

    @PostMapping("/upload-arquivos")
    public ResponseEntity<String> uploadArquivos(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(name = "modoInsert", defaultValue = "true") boolean modoInsert) {

        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("Nenhum arquivo enviado.");
        }

        // 1. Definir um diretório base estável no home do usuário
        String userHome = System.getProperty("user.home");
        String loteId = UUID.randomUUID().toString();
        Path loteDir = Paths.get(userHome, "recibos_uploads_pendentes", loteId);
        
        try {
            Files.createDirectories(loteDir);
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório de lote: " + loteDir.toAbsolutePath() + " | Erro: " + e.getMessage());
            return ResponseEntity.status(500).body("Erro interno ao criar diretório de lote.");
        }

        // 2. Salvar todos os arquivos da requisição neste diretório
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getOriginalFilename() == null) continue;
            
            try {
                String nomeSeguro = Paths.get(file.getOriginalFilename()).getFileName().toString();
                File arquivoDestino = loteDir.resolve(nomeSeguro).toFile();
                file.transferTo(arquivoDestino);
            } catch (IOException e) {
                System.err.println("Erro ao salvar arquivo " + file.getOriginalFilename() + ": " + e.getMessage());
                e.printStackTrace(); 
                return ResponseEntity.status(500).body("Erro ao salvar arquivos. Verifique os logs.");
            }
        }

        // 3. Limpar a pasta de scripts gerados antes de iniciar
        new LimpadorDePasta("scripts_gerados");

        // 4. Chamar o serviço ASSÍNCRONO
        processadorBatchService.processarLote(loteDir, modoInsert);

        // 5. Retornar resposta imediata em TEXTO
        String resposta = files.length + " arquivos recebidos. O processamento foi iniciado em segundo plano (Lote: " + loteId + ").";
        return ResponseEntity.accepted().body(resposta);
    }

    // --- NOVO MÉTODO PARA BAIXAR TUDO EM UM ÚNICO ARQUIVO ---
    @GetMapping("/download/gerados/completo")
    public ResponseEntity<Resource> downloadArquivosGeradosCompleto() throws IOException {
        File pastaScripts = new File("scripts_gerados");
        if (!pastaScripts.exists() || !pastaScripts.isDirectory()) {
            return ResponseEntity.status(404).body(null);
        }

        // Listar arquivos .sql
        File[] arquivosSql = pastaScripts.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));

        if (arquivosSql == null || arquivosSql.length == 0) {
            String aviso = "-- Nenhum script .sql encontrado na pasta scripts_gerados.\n";
            byte[] bytes = aviso.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scripts_vazio.sql\"")
                    .contentLength(bytes.length)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }
        
        // Ordenar para manter a ordem consistente
        Arrays.sort(arquivosSql); 

        StringBuilder conteudoCompleto = new StringBuilder();
        conteudoCompleto.append("-- SCRIPT CONSOLIDADO - SISTEMA DE IMPORTAÇÃO\n");
        conteudoCompleto.append("-- Total de arquivos processados: ").append(arquivosSql.length).append("\n\n");

        for (File file : arquivosSql) {
            try {
                String conteudo = Files.readString(file.toPath());
                conteudoCompleto.append("-- Arquivo: ").append(file.getName()).append("\n");
                conteudoCompleto.append(conteudo).append("\n\n");
            } catch (IOException e) {
                System.err.println("Erro ao ler arquivo " + file.getName() + ": " + e.getMessage());
            }
        }

        byte[] bytes = conteudoCompleto.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scripts_consolidado.sql\"")
                .contentLength(bytes.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // --- Endpoints Legados / Auxiliares ---

    @GetMapping("/download/{nomeArquivo}")
    public ResponseEntity<Resource> downloadArquivo(@PathVariable String nomeArquivo) throws IOException {
        File file = new File("scripts_gerados", nomeArquivo);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toURI());
        String contentDisposition = "attachment; filename=\"" + file.getName() + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    @GetMapping("/download-multiple")
    public ResponseEntity<StreamingResponseBody> downloadMultiple(@RequestParam List<String> arquivos) {
        StreamingResponseBody stream = outputStream -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                for (String nomeArquivo : arquivos) {
                    File file = new File("scripts_gerados", nomeArquivo);
                    if (file.exists()) {
                        zos.putNextEntry(new ZipEntry(file.getName()));
                        try (InputStream fis = new FileInputStream(file)) {
                            fis.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                }
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=arquivos.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }

    @GetMapping("/download/completo")
    public ResponseEntity<Resource> downloadArquivoCompleto(@RequestParam List<String> arquivos) throws IOException {
        if (arquivos == null || arquivos.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        StringBuilder conteudoCompleto = new StringBuilder();
        for (String nomeArquivo : arquivos) {
            File file = new File("scripts_gerados", nomeArquivo);
            if (file.exists()) {
                String conteudo = Files.readString(file.toPath());
                conteudoCompleto.append("-- Início do script: ").append(nomeArquivo).append("\n");
                conteudoCompleto.append(conteudo).append("\n\n");
            }
        }
        byte[] bytes = conteudoCompleto.toString().getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scripts_completos.sql\"")
                .contentLength(bytes.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/fontes-dados")
    public ResponseEntity<List<FonteDadoDTO>> listarFontesDados() {
        try {
            FonteDados fonteDados = new FonteDados();
            List<FonteDadoDTO> lista = fonteDados.listarFontesDados();
            return ResponseEntity.ok(lista);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/fontes-dados/editar")
    public ResponseEntity<Void> editarFonteDado(@RequestBody Map<String, String> payload) {
        String nomeArquivo = payload.get("nomeArquivo");
        String conteudo = payload.get("conteudo");
        try {
            FonteDados fonteDados = new FonteDados();
            if (fonteDados.getArquivoPorNome(nomeArquivo) == null) {
                return ResponseEntity.notFound().build();
            }
            fonteDados.escreverArquivoPorNome(nomeArquivo, conteudo);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}