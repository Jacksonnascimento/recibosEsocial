import os
import shutil
import xml.etree.ElementTree as ET
import multiprocessing
import functools
import time  # Para medir o tempo

# --- CONFIGURAÇÃO ---
PASTA_ORIGEM = r'C:\Users\jacks\Downloads\RECIBOS'
PASTA_DESTINO = r'C:\Users\jacks\Downloads\Destino'
VALOR_PROCURADO = '2023-01'
# --- FIM DA CONFIGURAÇÃO ---


def processar_arquivo(caminho_arquivo_origem, valor_procurado, pasta_destino):
    """
    Função "trabalhadora" que processa UM ÚNICO arquivo.
    Ela é otimizada para retornar status em vez de imprimir.
    (Esta função não precisa de NENHUMA alteração)
    """
    nome_arquivo = os.path.basename(caminho_arquivo_origem)
    
    try:
        if not os.path.exists(caminho_arquivo_origem):
            return (nome_arquivo, 'ignorado', 'Arquivo já processado')

        tree = ET.parse(caminho_arquivo_origem)
        root = tree.getroot()
        
        for element in root.iter():
            nome_local_da_tag = element.tag.split('}')[-1]
            
            if nome_local_da_tag == 'perApur':
                if element.text is not None:
                    valor_no_xml = element.text.strip()
                    
                    if valor_no_xml == valor_procurado:
                        # Encontramos!
                        caminho_arquivo_destino = os.path.join(pasta_destino, nome_arquivo)
                        
                        # --- VERIFICAÇÃO DE COLISÃO ---
                        # Se o arquivo já existir no destino, retorna erro
                        # para evitar que o shutil.move falhe.
                        if os.path.exists(caminho_arquivo_destino):
                            return (nome_arquivo, 'erro', f'Arquivo já existe no destino')
                        # --- FIM DA VERIFICAÇÃO ---

                        shutil.move(caminho_arquivo_origem, caminho_arquivo_destino)
                        return (nome_arquivo, 'movido')
        
        return (nome_arquivo, 'ignorado')
    
    except ET.ParseError as e:
        return (nome_arquivo, 'erro', f'XML mal formatado: {e}')
    except Exception as e:
        return (nome_arquivo, 'erro', f'Exceção: {e}')

def main():
    """
    Função principal que organiza o pool de processos.
    """
    print(f"Iniciando verificação paralela e RECURSIVA em: {PASTA_ORIGEM}")
    print(f"Procurando por <perApur> com valor: {VALOR_PROCURADO}\n")
    
    start_time = time.time()
    
    if not os.path.exists(PASTA_DESTINO):
        os.makedirs(PASTA_DESTINO)
        print(f"Pasta de destino criada: {PASTA_DESTINO}")

    # -----------------------------------------------------------------
    # --- MUDANÇA PRINCIPAL AQUI ---
    # 2. Listar todos os arquivos ANTES de começar (RECURSIVAMENTE)
    print("Listando arquivos recursivamente... (isso pode levar um tempo)")
    arquivos_para_processar = []
    try:
        # Usamos os.walk() para percorrer TODAS as subpastas
        for dirpath, dirnames, filenames in os.walk(PASTA_ORIGEM):
            for filename in filenames:
                if filename.lower().endswith('.xml'):
                    # Monta o caminho completo do arquivo
                    caminho_completo = os.path.join(dirpath, filename)
                    arquivos_para_processar.append(caminho_completo)
                    
    except Exception as e:
        print(f"Erro ao listar arquivos: {e}")
        return
    # --- FIM DA MUDANÇA ---
    # -----------------------------------------------------------------

    total_de_arquivos = len(arquivos_para_processar)
    if total_de_arquivos == 0:
        print("Nenhum arquivo .xml encontrado na pasta de origem e suas subpastas.")
        return
        
    print(f"Total de {total_de_arquivos} arquivos XML encontrados. Iniciando processamento...\n")

    worker_func = functools.partial(processar_arquivo, 
                                    valor_procurado=VALOR_PROCURADO, 
                                    pasta_destino=PASTA_DESTINO)

    num_processos = multiprocessing.cpu_count()
    print(f"Iniciando pool com {num_processos} processos.")
    
    arquivos_movidos = 0
    arquivos_ignorados = 0
    arquivos_com_erro = 0
    
    with multiprocessing.Pool(processes=num_processos) as pool:
        
        resultados = pool.imap_unordered(worker_func, arquivos_para_processar)
        
        for i, resultado in enumerate(resultados):
            nome_arquivo, status, *detalhe_erro = resultado
            
            if status == 'movido':
                arquivos_movidos += 1
            elif status == 'ignorado':
                arquivos_ignorados += 1
            elif status == 'erro':
                arquivos_com_erro += 1
                # Imprime o erro para sabermos o que aconteceu
                print(f"[ERRO] {nome_arquivo}: {detalhe_erro[0]}")
            
            # Imprime um relatório de progresso a cada 5000 arquivos
            if (i + 1) % 5000 == 0 or (i + 1) == total_de_arquivos:
                print(f"--- Progresso: {i + 1} / {total_de_arquivos} analisados ---")

    end_time = time.time()
    duracao_total = end_time - start_time
    
    print("\n--- Verificação Concluída ---")
    print(f"Tempo total: {duracao_total:.2f} segundos")
    print(f"Total de arquivos XML analisados: {total_de_arquivos}")
    print(f"Total de arquivos movidos: {arquivos_movidos}")
    print(f"Total de arquivos ignorados: {arquivos_ignorados}")
    print(f"Total de arquivos com erro: {arquivos_com_erro}")

if __name__ == '__main__':
    multiprocessing.freeze_support() 
    main()