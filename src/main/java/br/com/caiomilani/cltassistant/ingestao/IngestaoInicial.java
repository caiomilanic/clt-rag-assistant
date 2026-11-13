package br.com.caiomilani.cltassistant.ingestao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Indexa a CLT na primeira subida, quando a base de vetores está vazia (D23). É o que permite subir o
 * projeto inteiro com um {@code docker compose up}, sem chamada manual à API de ingestão.
 */
@Component
public class IngestaoInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestaoInicial.class);

    private final IngestaoService ingestao;
    private final JdbcTemplate jdbc;
    private final String tabela;
    private final boolean automatica;

    public IngestaoInicial(IngestaoService ingestao, JdbcTemplate jdbc,
                           @Value("${spring.ai.vectorstore.pgvector.table-name}") String tabela,
                           @Value("${app.ingestao.automatica}") boolean automatica) {
        this.ingestao = ingestao;
        this.jdbc = jdbc;
        this.tabela = tabela;
        this.automatica = automatica;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!automatica) {
            return;
        }
        Long documentos = jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
        if (documentos != null && documentos > 0) {
            log.info("Base já indexada ({} documentos): ingestão automática dispensada.", documentos);
            return;
        }
        log.info("Base vazia: indexando a CLT (na primeira subida, leva alguns minutos em CPU)...");
        IngestaoService.Resultado r = ingestao.ingerir("clt");
        log.info("CLT indexada: {} trechos, {} documentos, {} s.", r.trechos(), r.documentos(), r.duracaoMs() / 1000);
    }
}
