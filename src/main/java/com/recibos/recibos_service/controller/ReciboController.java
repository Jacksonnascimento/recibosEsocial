package com.recibos.recibos_service.controller;

import com.recibos.recibos_service.util.GeradorSQLRecibo;

import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api")
public class ReciboController {

    @PostMapping("/upload-arquivos")
    public ResponseEntity<List<String>> uploadArquivos(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(name = "modoInsert", defaultValue = "true") boolean modoInsert) {
        List<String> respostas = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                GeradorSQLRecibo geradorSQL = new GeradorSQLRecibo(modoInsert);

                File arquivoTemp = File.createTempFile("temp-", ".xml");
                file.transferTo(arquivoTemp);

                String scriptSQL = geradorSQL.gerarSQL(arquivoTemp, file.getOriginalFilename());
                String caminhoSalvo = salvarScript(scriptSQL, file.getOriginalFilename());

                respostas.add("Arquivo " + file.getOriginalFilename() + " processado com sucesso. Script salvo em: "
                        + caminhoSalvo);
                arquivoTemp.delete();

            } catch (Exception e) {
                respostas.add("Erro ao processar arquivo " + file.getOriginalFilename() + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(respostas);
    }

    private String salvarScript(String script, String nomeArquivoOriginal) throws IOException {
        String pastaScripts = "scripts_gerados";
        File dir = new File(pastaScripts);
        if (!dir.exists())
            dir.mkdirs();

        // Trocar extensão para .sql
        String nomeScript = nomeArquivoOriginal.replaceAll("\\.xml$", ".sql");
        File arquivoScript = new File(dir, nomeScript);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(arquivoScript))) {
            bw.write(script);
        }

        return arquivoScript.getAbsolutePath();
    }

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

    // Endpoint pra baixar múltiplos arquivos em ZIP
    @GetMapping("/download-multiple")
    public ResponseEntity<InputStreamResource> downloadMultiple(@RequestParam List<String> arquivos)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (String nomeArquivo : arquivos) {
            File file = new File("scripts_gerados", nomeArquivo);
            if (file.exists()) {
                zos.putNextEntry(new ZipEntry(file.getName()));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }

        zos.close();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        InputStreamResource resource = new InputStreamResource(bais);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=arquivos.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
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

}
