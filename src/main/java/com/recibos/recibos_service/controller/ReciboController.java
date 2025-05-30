package com.recibos.recibos_service.controller;

import com.recibos.recibos_service.util.FonteDadoDTO;
import com.recibos.recibos_service.util.FonteDados;
import com.recibos.recibos_service.util.GeradorSQLRecibo;
import com.recibos.recibos_service.util.LimpadorDePasta;

import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
//import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        new LimpadorDePasta("scripts_gerados");
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
