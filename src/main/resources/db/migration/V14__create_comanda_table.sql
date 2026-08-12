-- =============================================================================
-- V14 — Tabela `comanda` (Sprint 2 — Comanda nos 3 canais).
--
-- Modelo A do CLAUDE.md (seção 5.2): Comanda é a única unidade de venda
-- nos canais MESA/BALCAO/DELIVERY. Itens e pagamentos pertencem à
-- comanda, nunca à mesa. Não existe entidade `Venda` separada — comanda
-- fechada é a venda.
--
-- Hierarquia COMPARTILHADA → INDIVIDUAIS (rateio na Sprint 3): a coluna
-- `comanda_pai_id` aponta de uma INDIVIDUAL para a COMPARTILHADA que ela
-- compõe. Forçamos `escopo='INDIVIDUAL'` em BALCAO/DELIVERY (regra do
-- service — não há constraint de banco).
--
-- Totais armazenados como NUMERIC(12,2). O service mantém eles em sincronia
-- com a soma dos itens em cada mutação.
-- =============================================================================

CREATE TABLE comanda (
    id                    BIGSERIAL    PRIMARY KEY,

    -- Código humano gerado pelo service (ex.: "M-12-001", "B-0001", "D-0001").
    codigo                VARCHAR(20)  NOT NULL UNIQUE,

    -- Enum TipoOrigemComanda: MESA, BALCAO, DELIVERY.
    tipo_origem           VARCHAR(10)  NOT NULL,

    -- Enum EscopoComanda: COMPARTILHADA, INDIVIDUAL.
    escopo                VARCHAR(15)  NOT NULL DEFAULT 'INDIVIDUAL',

    -- Enum StatusComanda: ABERTA, AGUARDANDO_PAGAMENTO, FECHADA, CANCELADA.
    status                VARCHAR(25)  NOT NULL DEFAULT 'ABERTA',

    -- Cliente é obrigatório em todos os canais (RF20 / RN-COM-01).
    cliente_id            BIGINT       NOT NULL REFERENCES cliente(id),

    -- Obrigatório se tipo_origem='MESA' (validado no service).
    mesa_id               BIGINT       REFERENCES mesa(id),

    -- Aponta para COMPARTILHADA quando esta é INDIVIDUAL participante.
    comanda_pai_id        BIGINT       REFERENCES comanda(id),

    -- Obrigatório se tipo_origem='MESA' (validado no service).
    garcom_id             BIGINT       REFERENCES usuario(id),

    -- Preenchido em DELIVERY no momento do despacho (Sprint 7).
    entregador_id         BIGINT       REFERENCES entregador(id),

    -- Obrigatório se tipo_origem='DELIVERY'. Snapshot do endereço no
    -- momento da venda — se o cliente alterar/remover, a comanda preserva.
    endereco_entrega_id   BIGINT       REFERENCES endereco_cliente(id),

    observacao            TEXT,

    -- https://wa.me/<somente_digitos(cliente.telefone)>; gerado e
    -- persistido na criação (RF22 / RN-COM-06).
    link_whatsapp         VARCHAR(200),

    total_bruto           NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_descontos       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_acrescimos      NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_liquido         NUMERIC(12,2) NOT NULL DEFAULT 0,

    data_abertura         TIMESTAMP    NOT NULL,
    data_fechamento       TIMESTAMP,

    criado_em             TIMESTAMP    NOT NULL,
    atualizado_em         TIMESTAMP    NOT NULL,
    criado_por            VARCHAR(60),
    atualizado_por        VARCHAR(60)
);

CREATE INDEX idx_comanda_status            ON comanda (status);
CREATE INDEX idx_comanda_mesa_status       ON comanda (mesa_id, status) WHERE mesa_id IS NOT NULL;
CREATE INDEX idx_comanda_cliente           ON comanda (cliente_id);
CREATE INDEX idx_comanda_origem_status     ON comanda (tipo_origem, status);
CREATE INDEX idx_comanda_pai               ON comanda (comanda_pai_id) WHERE comanda_pai_id IS NOT NULL;
CREATE INDEX idx_comanda_data_abertura     ON comanda (data_abertura DESC);
