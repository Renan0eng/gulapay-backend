-- =============================================================================
-- V15 — Tabela `item_comanda` (Sprint 2 — Comanda nos 3 canais).
--
-- Cada item nasce em `EM_PREPARO` (seção 5.2.1 do CLAUDE.md, decisão da
-- Sessão 6 — sem etapa manual de envio à produção). Garçom marca como
-- `ENTREGUE` ao servir.
--
-- preco_unitario é CONGELADO no momento do lançamento (RN-ITEM-04). Se o
-- preço do produto mudar depois, itens já lançados não são afetados.
--
-- item_origem_id aponta para o item de origem em uma transferência
-- (Sessão 6 — transferência cria item novo no destino, sem afetar
-- estoque; origem muda para status TRANSFERIDO).
--
-- motivo_cancelamento só faz sentido quando status='CANCELADO'. Sem
-- constraint de banco — validado no service (RN-ITEM-02).
-- =============================================================================

CREATE TABLE item_comanda (
    id                    BIGSERIAL     PRIMARY KEY,

    comanda_id            BIGINT        NOT NULL REFERENCES comanda(id),
    produto_id            BIGINT        NOT NULL REFERENCES produto(id),

    quantidade            NUMERIC(10,3) NOT NULL,

    -- Preço congelado no momento do lançamento.
    preco_unitario        NUMERIC(10,2) NOT NULL,
    valor_desconto        NUMERIC(10,2) NOT NULL DEFAULT 0,
    valor_acrescimo       NUMERIC(10,2) NOT NULL DEFAULT 0,

    -- Calculado pelo service: quantidade * preco_unitario - desconto + acrescimo.
    subtotal              NUMERIC(12,2) NOT NULL,

    -- Enum StatusItemComanda: EM_PREPARO, ENTREGUE, CANCELADO, TRANSFERIDO.
    status                VARCHAR(15)   NOT NULL DEFAULT 'EM_PREPARO',

    -- Enum MotivoCancelamentoItem (só se status='CANCELADO').
    motivo_cancelamento   VARCHAR(30),

    observacao            TEXT,

    -- FK auto-referente: aponta para o item original quando este registro
    -- foi criado por uma transferência.
    item_origem_id        BIGINT        REFERENCES item_comanda(id),

    -- Usuário que lançou o item na comanda (garçom/caixa/admin).
    lancado_por           BIGINT        NOT NULL REFERENCES usuario(id),

    data_lancamento       TIMESTAMP     NOT NULL,
    -- Última transição de status (usado por relatórios e timeline de eventos).
    data_status           TIMESTAMP     NOT NULL,

    criado_em             TIMESTAMP     NOT NULL,
    atualizado_em         TIMESTAMP     NOT NULL,
    criado_por            VARCHAR(60),
    atualizado_por        VARCHAR(60)
);

CREATE INDEX idx_item_comanda_status      ON item_comanda (comanda_id, status);
CREATE INDEX idx_item_comanda_produto     ON item_comanda (produto_id);
CREATE INDEX idx_item_comanda_origem      ON item_comanda (item_origem_id) WHERE item_origem_id IS NOT NULL;
CREATE INDEX idx_item_comanda_lancado_por ON item_comanda (lancado_por);
