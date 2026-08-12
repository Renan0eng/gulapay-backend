-- =============================================================================
-- V16 — Tabela `evento_item_comanda` (auditoria — RNF09).
--
-- Toda mutação em `item_comanda` cria um evento aqui, com:
--   - acao (enum AcaoEventoItem)
--   - usuario_id que executou
--   - motivo (se aplicável — cancelamento)
--   - snapshot JSON de `valor_antes` e `valor_depois`
--   - data_hora
--
-- O service preenche os snapshots usando o ObjectMapper do Spring. O tipo
-- JSONB é nativo do PostgreSQL; em H2 (testes), o tipo cai para CLOB e o
-- Hibernate ainda persiste a string JSON sem problemas — basta usar
-- @JdbcTypeCode(SqlTypes.JSON) na entidade ou serializar manualmente para
-- String e mapear como @Column(columnDefinition="TEXT").
--
-- Escolhemos a abordagem mais portável: serializar manualmente para String
-- e usar TEXT no banco (em PG vira TEXT também; sem JSONB). Trocamos a
-- consulta indexada do JSONB pela portabilidade e simplicidade — as
-- queries de auditoria sempre buscam por item ou usuário, não pelo
-- conteúdo do JSON.
-- =============================================================================

CREATE TABLE evento_item_comanda (
    id                BIGSERIAL    PRIMARY KEY,

    item_comanda_id   BIGINT       NOT NULL REFERENCES item_comanda(id),

    -- Enum AcaoEventoItem: CRIADO, EDITADO, TRANSFERIDO, CANCELADO, ENTREGUE.
    acao              VARCHAR(20)  NOT NULL,

    usuario_id        BIGINT       NOT NULL REFERENCES usuario(id),

    -- Enum MotivoCancelamentoItem (só preenchido em acao='CANCELADO').
    motivo            VARCHAR(30),

    -- Snapshots serializados como JSON-string. Mantemos como TEXT para
    -- compatibilidade com H2 (testes). Em produção PostgreSQL o tipo TEXT
    -- aceita strings de qualquer tamanho.
    valor_antes       TEXT,
    valor_depois      TEXT,

    data_hora         TIMESTAMP    NOT NULL,

    criado_em         TIMESTAMP    NOT NULL,
    atualizado_em     TIMESTAMP    NOT NULL,
    criado_por        VARCHAR(60),
    atualizado_por    VARCHAR(60)
);

CREATE INDEX idx_evento_item_item     ON evento_item_comanda (item_comanda_id);
CREATE INDEX idx_evento_item_usuario  ON evento_item_comanda (usuario_id);
CREATE INDEX idx_evento_item_data     ON evento_item_comanda (item_comanda_id, data_hora DESC);
